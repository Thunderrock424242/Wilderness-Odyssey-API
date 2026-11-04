"""Fixed offline LoRA worker. Only the Java service constructs its private config.

No downloads, remote model code, shell commands, production memory or activation.
CLI training fails closed without Linux cgroup memory/CPU/PID limits.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import sys

PRESETS = {"lora-smoke": (8, 256, 8), "lora-small": (100, 1024, 100)}


def model_digest(base):
    files = sorted(p for p in base.rglob("*") if p.is_file())
    if not files or len(files) > 4096:
        raise RuntimeError("INVALID_MODEL_INVENTORY")
    digest = hashlib.sha256()
    for path in files:
        if path.is_symlink():
            raise RuntimeError("LINKED_MODEL_UNSUPPORTED")
        content = hashlib.sha256()
        with path.open("rb") as stream:
            while chunk := stream.read(1048576):
                content.update(chunk)
        digest.update(path.relative_to(base).as_posix().encode("utf-8") + b"\x00" + content.hexdigest().encode("ascii") + b"\n")
    return digest.hexdigest()


def host_limits(c):
    if sys.platform != "linux":
        raise RuntimeError("LINUX_RESOURCE_ISOLATION_REQUIRED")
    import ctypes
    import resource
    import signal
    parent = os.getppid()
    if ctypes.CDLL(None).prctl(1, signal.SIGKILL) != 0 or os.getppid() != parent or parent == 1:
        raise RuntimeError("PARENT_LIFECYCLE_UNAVAILABLE")
    # cgroup v2 limits are inherited by this child, including on parent crashes.
    group = next(x.split(":", 2)[2] for x in Path("/proc/self/cgroup").read_text().splitlines() if x.startswith("0::"))
    root = Path("/sys/fs/cgroup") / group.lstrip("/")
    memory = (root / "memory.max").read_text().strip()
    quota, period = (root / "cpu.max").read_text().split()
    pids = (root / "pids.max").read_text().strip()
    if memory == "max" or int(memory) > c["memoryMiB"] * 1048576:
        raise RuntimeError("MEMORY_LIMIT_REQUIRED")
    if quota == "max" or int(quota) / int(period) > c["cpuThreads"]:
        raise RuntimeError("CPU_LIMIT_REQUIRED")
    if pids == "max" or int(pids) > 256:
        raise RuntimeError("PID_LIMIT_REQUIRED")
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    resource.setrlimit(resource.RLIMIT_FSIZE, (c["artifactMiB"] * 1048576,) * 2)
    resource.setrlimit(resource.RLIMIT_CPU, (c["timeoutSeconds"] * c["cpuThreads"],) * 2)


def runtime(c, laptop=False):
    os.environ.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1",
                      TOKENIZERS_PARALLELISM="false", OMP_NUM_THREADS=str(c["cpuThreads"]),
                      MKL_NUM_THREADS=str(c["cpuThreads"]))
    import torch
    import peft
    import transformers
    torch.set_num_threads(c["cpuThreads"])
    torch.set_num_interop_threads(1)
    if c["device"] == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("GPU_UNAVAILABLE")
        free, total = torch.cuda.mem_get_info()
        budget = c["minFreeGpuMiB"] * 1048576
        if free < budget:
            raise RuntimeError("GPU_CAPACITY_UNAVAILABLE")
        torch.cuda.set_per_process_memory_fraction(min(0.95, budget / total))
    base = Path(c["modelDirectory"]).resolve(strict=True)
    if not base.is_dir() or not (base / "config.json").is_file() or not list(base.glob("*.safetensors")):
        raise RuntimeError("LOCAL_SAFETENSORS_MODEL_REQUIRED")
    if any(p.is_symlink() for p in base.rglob("*")):
        raise RuntimeError("LINKED_MODEL_UNSUPPORTED")
    if model_digest(base) != c["approvedBaseSha256"]:
        raise RuntimeError("APPROVED_BASE_HASH_MISMATCH")
    # This worker uses unquantized frozen base weights. Reserve room for activations,
    # adapter gradients and optimizer state before advertising a model as trainable.
    weights_bytes = sum(p.stat().st_size for p in base.glob("*.safetensors"))
    quantized = c.get("quantization") == "nf4"
    if quantized and not laptop:
        raise RuntimeError("QUANTIZATION_REQUIRES_LOCAL_PROFILE")
    required_bytes = int(weights_bytes * 0.5) + 2048 * 1048576 if quantized else weights_bytes * 2 + 512 * 1048576
    budget_bytes = c["minFreeGpuMiB"] * 1048576 if c["device"] == "cuda" else c["memoryMiB"] * 1048576
    if required_bytes > budget_bytes:
        raise RuntimeError("MODEL_EXCEEDS_TRAINING_BUDGET")
    # Local tokenizer/config must be loadable without fetching code or files.
    transformers.AutoConfig.from_pretrained(base, local_files_only=True, trust_remote_code=False)
    transformers.AutoTokenizer.from_pretrained(base, local_files_only=True, trust_remote_code=False)
    return torch, peft, transformers, base


def chat_sample(tokenizer, prompt, target, system_prompt=None):
    user = ([{"role": "system", "content": system_prompt}] if system_prompt is not None else [])
    user += [{"role": "user", "content": prompt}]
    prefix = tokenizer.apply_chat_template(user, tokenize=True, add_generation_prompt=True, return_dict=False)
    full = tokenizer.apply_chat_template(user + [{"role": "assistant", "content": target}],
                                         tokenize=True, add_generation_prompt=False, return_dict=False)
    if full[:len(prefix)] != prefix:
        # BPE can encode the same trailing header whitespace differently when
        # completion text follows it. Preserve the exact inference prefix and
        # encode only the rendered completion suffix, including its end marker.
        prefix_text = tokenizer.apply_chat_template(user, tokenize=False, add_generation_prompt=True)
        full_text = tokenizer.apply_chat_template(user + [{"role":"assistant", "content":target}],
                                                   tokenize=False, add_generation_prompt=False)
        if not full_text.startswith(prefix_text):
            raise RuntimeError("CHAT_TEMPLATE_PREFIX_MISMATCH")
        return prefix, tokenizer.encode(full_text[len(prefix_text):], add_special_tokens=False)
    return prefix, full[len(prefix):]


def train(c, checked_runtime=None, profile=None):
    torch, peft, transformers, base = checked_runtime or runtime(c)
    torch.manual_seed(424242)
    steps, sequence, maximum = PRESETS[c["presetId"]] if profile is None else profile["bounds"]
    dataset = Path(c["datasetPath"]).read_bytes()
    if len(dataset) > 262144 or hashlib.sha256(dataset).hexdigest() != c["datasetHash"]:
        raise RuntimeError("SNAPSHOT_HASH_MISMATCH")
    examples = json.loads(dataset.decode("utf-8", errors="strict"))
    if not 1 <= len(examples) <= maximum:
        raise RuntimeError("PRESET_DATASET_LIMIT")
    tokenizer = transformers.AutoTokenizer.from_pretrained(base, local_files_only=True, trust_remote_code=False)
    # Supervised completion only: prompt tokens never contribute to the target loss.
    samples = []
    for example in examples:
        if profile is not None and profile.get("chatTemplate"):
            prompt, target = chat_sample(tokenizer, example["prompt"], example["target"], profile.get("systemPrompt"))
        else:
            prompt = tokenizer.encode(example["prompt"], add_special_tokens=False)
            target = tokenizer.encode(example["target"], add_special_tokens=False)
            if tokenizer.eos_token_id is not None:
                target.append(tokenizer.eos_token_id)
        if not prompt or not target or len(prompt) + len(target) > sequence:
            raise RuntimeError("SAMPLE_EXCEEDS_PRESET_CONTEXT")
        ids = torch.tensor([prompt + target], dtype=torch.long, device=c["device"])
        labels = ids.clone()
        labels[:, :len(prompt)] = -100
        samples.append((ids, labels))
    quantized = c.get("quantization") == "nf4" and profile is not None
    loading = {}
    if quantized:
        loading = {"quantization_config": transformers.BitsAndBytesConfig(
            load_in_4bit=True, bnb_4bit_quant_type="nf4", bnb_4bit_use_double_quant=True,
            bnb_4bit_compute_dtype=torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16),
            "device_map": {"": 0}}
    model = transformers.AutoModelForCausalLM.from_pretrained(
        base, local_files_only=True, trust_remote_code=False, use_safetensors=True,
        torch_dtype=torch.float32 if c["device"] == "cpu" else torch.float16, **loading)
    model.config.use_cache = False
    if quantized:
        model = peft.prepare_model_for_kbit_training(model, use_gradient_checkpointing=True)
    model = peft.get_peft_model(model, peft.LoraConfig(
        task_type="CAUSAL_LM", r=4, lora_alpha=8, lora_dropout=0.0,
        target_modules="all-linear", bias="none"))
    if not quantized:
        model.to(c["device"])
    model.train()
    params = [p for p in model.parameters() if p.requires_grad]
    before = [p.detach().float().cpu().clone() for p in params]
    optimizer = torch.optim.AdamW(params, lr=0.0002)
    loss_value = None
    for step in range(steps):
        ids, labels = samples[step % len(samples)]
        optimizer.zero_grad(set_to_none=True)
        loss = model(input_ids=ids, attention_mask=torch.ones_like(ids), labels=labels).loss
        if not torch.isfinite(loss):
            raise RuntimeError("NONFINITE_LOSS")
        loss.backward()
        torch.nn.utils.clip_grad_norm_(params, 1.0, error_if_nonfinite=True)
        optimizer.step()
        loss_value = float(loss.detach().cpu())
    changed = any(not torch.equal(a, p.detach().float().cpu()) for a, p in zip(before, params))
    if not changed or any(not torch.isfinite(p).all() for p in params):
        raise RuntimeError("INVALID_TRAINED_WEIGHTS")
    output = Path(c["outputDirectory"])
    output.mkdir(exist_ok=False)
    model.save_pretrained(output, safe_serialization=True)
    weights = output / "adapter_model.safetensors"
    # Reload the actual candidate tensors before asserting that it exists and is usable.
    from safetensors.torch import load_file
    saved = load_file(weights)
    if not saved or any(not torch.isfinite(p).all() for p in saved.values()):
        raise RuntimeError("INVALID_CANDIDATE")
    metadata = {"schemaVersion": "1.0", "baseModelId": c["baseModelId"], "presetId": c["presetId"],
                "datasetHash": c["datasetHash"], "steps": steps, "loss": loss_value,
                "weightsChanged": changed, "weightsSha256": hashlib.sha256(weights.read_bytes()).hexdigest(),
                "approvedBaseSha256": c["approvedBaseSha256"],
                "device": c["device"], "torchVersion": torch.__version__,
                "transformersVersion": transformers.__version__, "peftVersion": peft.__version__}
    if profile is not None:
        if "profileSha256" in c:
            metadata["profileSha256"] = c["profileSha256"]
        metadata["quantization"] = c.get("quantization", "none")
        metadata["chatTemplate"] = bool(profile.get("chatTemplate"))
        metadata["peakGpuAllocatedMiB"] = torch.cuda.max_memory_allocated() / 1048576 if c["device"] == "cuda" else None
    (output / "metadata.json").write_text(json.dumps(metadata), encoding="utf-8")
    for path in output.iterdir():
        if path.is_file():
            with path.open("rb+") as stream:
                os.fsync(stream.fileno())
    return metadata


if __name__ == "__main__":
    try:
        if len(sys.argv) == 3 and sys.argv[1] == "--fingerprint":
            print(model_digest(Path(sys.argv[2]).resolve(strict=True)))
            sys.exit(0)
        if len(sys.argv) != 3 or sys.argv[1] not in ("--check", "--train"):
            raise RuntimeError("INVALID_WORKER_ARGUMENTS")
        config = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
        host_limits(config)
        checked = runtime(config)
        if sys.argv[1] == "--train":
            train(config, checked)
    except Exception:
        # Private datasets, library diagnostics and host paths must not enter logs.
        sys.exit(1)
