"""Offline Llama GGUF -> sharded FP16 safetensors, one tensor at a time.

Uses the exact GGUF mapping/permutation implementation in Transformers 5.16.1.
Unlike its full loader, this does not retain every dequantized tensor in RAM.
Unknown tensor layouts or positional-frequency scaling fail closed.
"""
import json
from pathlib import Path
import shutil


def require(valid, category):
    if not valid: raise ValueError(category)


def restore_rope(config, factors):
    import torch
    from transformers.modeling_rope_utils import ROPE_INIT_FUNCTIONS
    # Older Ollama Llama 3.1 GGUFs store the actual factors as a tensor rather
    # than metadata. The ordinary HF GGUF loader drops that tensor silently.
    require(config.hidden_size == 4096 and config.num_attention_heads == 32
            and config.max_position_embeddings == 131072, "UNSUPPORTED_GGUF_ROPE")
    config.rope_parameters = {"rope_type":"llama3", "rope_theta":500000.0,
        "factor":8.0,"low_freq_factor":1.0,"high_freq_factor":4.0,"original_max_position_embeddings":8192}
    inverse, _ = ROPE_INIT_FUNCTIONS["llama3"](config, "cpu")
    default = 1 / (500000.0 ** (torch.arange(0,128,2).float()/128))
    require(factors.shape == inverse.shape and torch.allclose(default / inverse, factors, rtol=1e-5, atol=1e-5),
            "UNSUPPORTED_GGUF_ROPE")


def convert(source, output):
    import torch
    import transformers
    from gguf import GGUFReader, dequantize
    from safetensors.torch import save_file, load_file
    from transformers import AutoConfig, AutoModelForCausalLM, AutoTokenizer
    from transformers.modeling_gguf_pytorch_utils import LlamaTensorProcessor, get_gguf_hf_weights_map
    require(transformers.__version__ == "5.16.1", "PINNED_GGUF_CONVERTER_REQUIRED")
    source = Path(source); output = Path(output)
    torch.set_num_threads(2)
    config = AutoConfig.from_pretrained(source.parent, gguf_file=str(source), local_files_only=True, trust_remote_code=False)
    require(config.model_type == "llama", "ONLY_LLAMA_GGUF_SUPPORTED")
    reader = GGUFReader(str(source))
    frequency_tensors = [t for t in reader.tensors if t.name == "rope_freqs.weight"]
    require(len(frequency_tensors) <= 1, "DUPLICATE_GGUF_TENSOR")
    if frequency_tensors:
        tensor = frequency_tensors[0]
        restore_rope(config, torch.from_numpy(dequantize(tensor.data, tensor.tensor_type).copy()))
    with torch.device("meta"):
        model = AutoModelForCausalLM.from_config(config, trust_remote_code=False)
    processor = LlamaTensorProcessor(config=config.to_dict())
    mapping = get_gguf_hf_weights_map(model, processor)
    expected = {name:tuple(t.shape) for name,t in model.state_dict().items()}
    if config.tie_word_embeddings:
        expected.pop("lm_head.weight", None)
    required_disk = sum(t.n_elements * 2 for t in reader.tensors) + 256 * 1048576
    output.parent.mkdir(parents=True, exist_ok=True)
    require(shutil.disk_usage(output.parent).free >= required_disk * 1.1, "IMPORT_DISK_HEADROOM_REQUIRED")
    output.mkdir(exist_ok=False)
    config._name_or_path = ""; config.architectures = ["LlamaForCausalLM"]
    config.save_pretrained(output)
    tokenizer = AutoTokenizer.from_pretrained(source.parent, gguf_file=str(source), local_files_only=True, trust_remote_code=False)
    tokenizer.save_pretrained(output)
    inventory = {}; total = 0
    for index, tensor in enumerate(reader.tensors):
        if tensor.name == "rope_freqs.weight": continue  # Verified against HF's RoPE above.
        name = mapping.get(tensor.name)
        require(name is not None and name not in inventory, "UNKNOWN_OR_DUPLICATE_GGUF_TENSOR")
        weights = dequantize(tensor.data, tensor.tensor_type)
        result = processor.process(weights, tensor.name)
        converted = torch.from_numpy(result.weights.copy()).to(torch.float16).contiguous()
        del result, weights
        require(name in expected and tuple(converted.shape) == expected[name], "GGUF_TENSOR_SHAPE_MISMATCH")
        require(bool(torch.isfinite(converted).all()), "NONFINITE_GGUF_WEIGHTS")
        filename = f"model-{index + 1:05d}.safetensors"
        save_file({name:converted}, output / filename, metadata={"format":"pt"})
        # Check the actual serialized tensor before releasing this shard.
        saved = load_file(output / filename)
        require(torch.equal(converted, saved[name]), "GGUF_SHARD_VERIFICATION_FAILED")
        inventory[name] = filename; total += converted.numel() * converted.element_size()
        del saved, converted
    require(set(inventory) == set(expected), "INCOMPLETE_GGUF_MODEL")
    (output / "model.safetensors.index.json").write_text(json.dumps(
        {"metadata":{"total_size":total},"weight_map":inventory}, indent=2), encoding="utf-8")
