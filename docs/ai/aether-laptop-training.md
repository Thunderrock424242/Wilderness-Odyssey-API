# Train Aether locally and deploy an Ollama candidate

The laptop can train a candidate using the existing `llama3.1:8b` Ollama model.
Ollama serves the result; PyTorch/PEFT performs the actual QLoRA optimization.
No Hugging Face login or model download is needed for this local import path.
The Llama license still applies and is copied into the export.

The initial **eight-step, two-example pipeline test** used the laptop's RTX 4090
Laptop GPU. It is verification of training mechanics, not an Aether content model
or evidence of general quality. See the accompanying dated evidence JSON.
The verified training run peaked at 8449.58 MiB GPU allocation and 16016.97 MiB
observed process-tree RSS. Standalone export used about 4096 MiB observed RSS.
Ollama's later safetensors conversion/quantization reached about 20 GiB server RSS;
keep other model operations sequential and allow conversion memory headroom.

## Ownership and safety

- `tools/aether/laptop.py` is a Windows operator CLI, separate from HTTP routes.
  Hosted jobs retain their Linux/cgroup admission and durable SQLite lifecycle.
  This CLI does not make a sleeping laptop a remote training worker.
- Import verifies the installed Ollama model/license blob digests. The Llama-only
  converter uses pinned Transformers mapping/permutation code, writes one FP16
  tensor at a time, verifies every serialized tensor and checks completeness.
  Old Llama 3.1 GGUF positional factors are verified against the matching Llama 3
  RoPE implementation. Unknown layouts/scaling are rejected.
- Q4 -> FP16 conversion **does not restore original full-precision weights**.
  Source GGUF, converted base, reviewed dataset, trainer and adapter are separately
  fingerprinted. Do not describe the converted base as the original Meta checkpoint.
- Training loads local safetensors only, disables hub access/remote model code,
  snapshots literal reviewed UTF-8 bytes and checks SHA-256 again in the child.
  Production conversations and Minecraft player memory are never imported.
- A project lock allows one local import/training/export at a time. Windows Job
  Objects bound aggregate committed memory and eight owned processes, and kill
  descendants when the supervisor dies. The parent also monitors RSS, output size
  and wall time. GPU allocation uses the explicit PyTorch budget. Windows committed
  memory includes mapped-weight reservations: the real 8B run needed the 24 GiB
  limit; a 16 GiB limit rejected mapping the base before optimization.
- Work, snapshots and partial artifacts stage under ignored `.codex-build` and
  are erased after failure. Final output appears only after successful validation.
  Output must share the state volume, be new, and not overlap any input.
  Keep these directories on private local storage; this tool does not modify ACLs.
- Exports use PEFT's actual safe merge one tensor at a time, preserve tokenizer and
  chat template, and contain standalone FP16 weights. Each output tensor is reread
  and verified. Small-model regression tests compare every tensor and inference
  logits against a full PEFT merge/reload. This avoids requiring several full 8B
  model copies in RAM.
- Runtime import creates only a **new** `aether-candidate:...` tag. It records the
  successful Ollama creation's exact GGUF digest against the merged-package manifest.
  It also restores the source Ollama Go chat template, stop tokens and license;
  the original source model must remain installed and match the approved base's
  recorded GGUF/license hashes. Template/parameter blobs are verified against the
  installed source manifest and pinned in the new runtime receipt. The receipt
  binds the actual runtime Modelfile, including formatting and parameters.
  Bundling rejects a replaced blob or runtime template under the same tag.
  Importing a tag does not change the Minecraft/gateway active model.
- The Ollama daemon does its own quantization during runtime import. Its existing
  service resource configuration governs that work; the CLI's Job Object bounds
  the client process. The CLI deadline closes/kills its owned client on failure;
  inspect Ollama before retrying an interrupted import. Existing tags are never
  overwritten automatically.

## Laptop prerequisites

Verified environment: Windows, Python 3.12.14, NVIDIA driver 621.01, RTX 4090 Laptop
GPU with 16376 MiB VRAM, 31.7 GiB physical RAM, Ollama 0.17.7. WSL is unnecessary
for this local path. CUDA PyTorch and bitsandbytes must support the installed GPU.
Allow at least **60 GiB free disk** for an 8B base, merged export, temporary files
and quantized bundle; Python dependencies and retained previous versions need more.
Close unnecessary applications if RAM/VRAM admission fails. Do not disable host
security or change permissions to work around a failure.

Use a separate ignored virtualenv; preserve the existing voice-service environment:

```powershell
py -3.12 -m venv .codex-build/aether-laptop/.venv
$python = '.codex-build/aether-laptop/.venv/Scripts/python.exe'
& $python -m pip install torch==2.11.0 --index-url https://download.pytorch.org/whl/cu128
& $python -m pip install -r tools/aether/laptop-requirements.txt
& $python -m pip check
& $python -B tools/aether/laptop.py doctor
```

`doctor` executes a real CUDA kernel. Detecting a model name or a driver alone does
not establish a working CUDA runtime. No service tokens are needed on the laptop.

## Import and approve the local base

Run these from the repository root. Use new versioned output paths on later runs.
The import requires exactly one installed model blob and one license blob. The
default store is the current user's `.ollama/models`; `--store` supports an explicit
private local store. It does not pull a model or contact an external registry.

```powershell
& $python -B tools/aether/laptop.py import-ollama --model llama3.1:8b `
  --output .codex-build/aether-laptop/models/llama31-local-v1 `
  --memory-mib 24576 --timeout-seconds 1800
& $python -B tools/aether/laptop.py approve-base `
  --directory .codex-build/aether-laptop/models/llama31-local-v1 `
  --model-id llama3.1:8b-ollama-q4 `
  --output .codex-build/aether-laptop/llama31-base-v1.json
```

Approval fingerprints all model-directory files. Changes invalidate it. This is a
local input approval, not approval for production model activation.

## Prepare and train reviewed examples

Manually prepare a private UTF-8 JSON array with only these members per example:

```json
[{"id":"reviewed-1","revision":"r1","prompt":"Hello!","target":"Hello! How can I help?"}]
```

Review every prompt and target before acknowledging the dataset. Story-facing
datasets must follow the repository's Established lore and core-lore precedence.
No example collection runs automatically. The file is at most 262144 UTF-8 bytes;
IDs are unique; unknown/duplicate fields and duplicate JSON members are rejected.
The smoke profile allows 1–8 examples, 256 rendered tokens, 8 optimizer steps. The
small profile allows 1–100 examples, 1024 tokens, 100 steps. Token overflow fails
without truncation. Both use rank 4, alpha 8, batch 1, LR 0.0002, completion-only
loss, fixed seed and NF4/double quantization. `laptop-lora-smoke` is unquantized
and is intended for smaller bases that fit the configured GPU budget.

```powershell
$dataset = '.codex-build/aether-laptop/reviewed-examples-v1.json'
$snapshotHash = (Get-FileHash $dataset -Algorithm SHA256).Hash.ToLowerInvariant()
& $python -B tools/aether/laptop.py train `
  --base-approval .codex-build/aether-laptop/llama31-base-v1.json `
  --dataset $dataset --dataset-sha256 $snapshotHash `
  --preset laptop-qlora-smoke `
  --output .codex-build/aether-laptop/candidates/aether-v1 `
  --gpu-memory-mib 11000 --memory-mib 24576 --cpu-threads 2 `
  --timeout-seconds 600 --acknowledge-reviewed-data
```

A successful result includes changed, finite, reloaded adapter weights and measured
resource usage. Evaluate against separate reviewed examples; training loss on the
same examples is not a quality score. Keep the small preset for an explicit later
training decision after the smoke and dataset review.

## Export, quantize, test and package

```powershell
& $python -B tools/aether/laptop.py export `
  --base-approval .codex-build/aether-laptop/llama31-base-v1.json `
  --candidate .codex-build/aether-laptop/candidates/aether-v1 `
  --output .codex-build/aether-laptop/exports/aether-v1 `
  --name aether-candidate:aether-v1 --memory-mib 8192 --timeout-seconds 1800
& $python -B tools/aether/laptop.py import-runtime `
  --base-approval .codex-build/aether-laptop/llama31-base-v1.json `
  --merged-package .codex-build/aether-laptop/exports/aether-v1 `
  --ollama-executable "$env:LOCALAPPDATA/Programs/Ollama/ollama.exe" `
  --receipt .codex-build/aether-laptop/aether-v1-import.json --timeout-seconds 900
ollama run aether-candidate:aether-v1
& $python -B tools/aether/laptop.py bundle `
  --merged-package .codex-build/aether-laptop/exports/aether-v1 `
  --model aether-candidate:aether-v1 `
  --import-receipt .codex-build/aether-laptop/aether-v1-import.json `
  --output .codex-build/aether-laptop/deployment/aether-v1
& $python -B tools/aether/laptop.py verify-package `
  --directory .codex-build/aether-laptop/deployment/aether-v1
```

Ollama must already be running locally on `127.0.0.1:11434`. Runtime import performs
local safetensors -> Q4_K_M conversion. The final bundle contains `model.gguf`, the
runtime's actual template/parameters in a portable Modelfile, license, deployment
provenance and a complete SHA-256 inventory. It excludes the training dataset and
adapter's machine-specific input paths. Preserve the import receipt privately.
No automatic upload, public model push or production activation occurs.
Use `import-runtime` for intermediate safetensors exports: older Ollama importers
can ignore a separate Hugging Face `chat_template.jinja` and silently select raw
prompt continuation. The importer explicitly verifies the source template after
creation. For an interrupted import with a valid prior receipt, the optional
`--resume-import-receipt PRIOR.json` can repair only that receipt's candidate
formatting without changing its GGUF digest. It writes a new receipt; ordinary
imports continue to reject existing tags. Bundling requires a version 1.1 receipt
that binds both model and actual runtime Modelfile.

## Deploy inference on the Minecraft host

Training on the laptop removes the host's training requirement. It does **not**
remove the host's inference RAM/CPU requirement. The checked Kinetic allocation
has 7 GiB for Minecraft and 1 GiB for the bot, with all 8 GiB assigned. No usable GPU
or mixed Java/Ollama runtime has been verified there. Adding the 8B runtime beside
the modpack within that allocation is not a verified deployment. Budget a separate
inference allocation or increase total memory; 16 GiB total is a planning starting
point, subject to actual modpack/JVM load, context, concurrency and runtime overhead.
An 8+ GiB free-VRAM GPU can accelerate inference; CPU inference needs a host trial
and will have different latency from the laptop.

1. Verify the host can run JDK 21 plus Ollama for its actual OS/architecture and
   has persistent private disk, sufficient RAM/CPU, and an allowed private port.
   Keep Ollama loopback-only. GPU serving requires compatible NVIDIA drivers/runtime.
2. Upload only the verified final bundle into private storage. Recompute and check
   every manifest SHA-256 after transfer. Do not upload the dataset or training base.
3. From the uploaded bundle directory run
   `ollama create aether-candidate:aether-v1 -f Modelfile`. This imports the already
   quantized GGUF; host training packages and HF credentials are unnecessary.
4. Test the candidate with bounded context/output, separate reviewed evaluation
   prompts, and actual Minecraft load. Keep the previously working active model.
5. Use the standalone gateway JAR and exact private credential/Access settings in
   [the server deployment guide](aether-playground-server.md#private-configuration).
   Preserve the existing inference token and use distinct monitoring, admin,
   main-read and main-write tokens. Leave `playground.training_enabled: false` on
   an inference-only host. Review any sandbox model allowlist entry separately.
6. Production activation is a separate reviewed operation under the existing
   native commissioning flow. The main service adapter still exposes only `ai.pause`.

For a new constrained inference host, start with these private Ollama service
settings, then validate under actual load. They are service environment settings,
not browser or bot secrets. Existing services need an explicit configuration review
before changing their concurrency/context behavior:

```dotenv
OLLAMA_HOST=127.0.0.1:11434
OLLAMA_MODELS=/srv/aether/ollama-models
OLLAMA_CONTEXT_LENGTH=2048
OLLAMA_NUM_PARALLEL=1
OLLAMA_MAX_LOADED_MODELS=1
OLLAMA_MAX_QUEUE=8
```

Provision the model directory privately with persistent storage. Set the gateway's
explicit `ollama.context_tokens` consistently with the reviewed native inference
context. Larger contexts/concurrent requests increase runtime memory requirements;
see [Ollama's concurrency guidance](https://docs.ollama.com/faq#how-does-ollama-handle-concurrent-requests).

The actual private host origin, server ID, Access application/service identity,
secret values and deployment access must come from the operator's existing setup;
there are no safe universal values to substitute. The earlier pinned-contract
implementation and deployment guide remain authoritative for gateway/bot settings.

## Validation commands

```powershell
& $python -B -m unittest tools/aether/test_laptop.py tools/aether/test_local_process.py
# Explicit local small weights enable the real loader/merge comparisons:
$env:AETHER_VERIFICATION_GGUF = 'ABSOLUTE-LOCAL-SMALL-GGUF'
$env:AETHER_VERIFICATION_BASE = 'ABSOLUTE-LOCAL-SMALL-SAFETENSORS-DIRECTORY'
$env:AETHER_VERIFICATION_ADAPTER = 'ABSOLUTE-LOCAL-MATCHING-ADAPTER-DIRECTORY'
& $python -B -m unittest tools/aether/test_gguf_import.py tools/aether/test_merge_export.py
& $python -B aether-server/src/test/python/test_training_policy.py
.\gradlew.bat :aether-server:test :aether-server:verifyStandalone '-PcodexBuildDir=.codex-build' --no-parallel
```

Absent explicit weights, model-dependent comparisons skip honestly. The fast tests
cover exact snapshot hashes/fields, changed/extra package files, isolated environment,
Windows forced-parent-death cleanup, tokenizer boundaries, temporary-file races,
model/path validation and replacing an Ollama tag's blob after a recorded import.
