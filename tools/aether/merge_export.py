"""Merge fixed rank-4 LoRA adapters with PEFT, one base tensor at a time.

This uses PEFT's actual Linear.safe_merge implementation, rather than retaining a
whole 8B model and its merge copies in RAM. Only the local trainer's vanilla LoRA
layout is supported. The standalone output contains every expected model tensor.
"""
import json
from pathlib import Path


def require(valid, category):
    if not valid: raise ValueError(category)


def ollama_compatible_config(config):
    result = dict(config); rope = result.get("rope_parameters")
    require(isinstance(rope,dict) and "rope_theta" in rope,"ROPE_PARAMETERS_REQUIRED")
    # Ollama 0.17's safetensors converter reads the legacy HF field names. Keep
    # modern Transformers fields too, with exactly matching positional settings.
    result["rope_theta"] = rope["rope_theta"]
    result["rope_scaling"] = {k:v for k,v in rope.items() if k != "rope_theta"}
    return result


def merge(base, candidate, output):
    import torch
    import peft
    from peft.tuners.lora.layer import Linear
    from safetensors import safe_open
    from safetensors.torch import load_file, save_file
    from transformers import AutoConfig, AutoModelForCausalLM, AutoTokenizer
    require(peft.__version__ == "0.18.1", "PINNED_PEFT_MERGE_REQUIRED")
    base = Path(base); candidate = Path(candidate); output = Path(output)
    torch.set_num_threads(2)
    adapter_config = json.loads((candidate / "adapter_config.json").read_text())
    require(adapter_config["peft_type"] == "LORA" and adapter_config["r"] == 4
            and adapter_config["lora_alpha"] == 8 and adapter_config["bias"] == "none", "FIXED_LORA_EXPORT_REQUIRED")
    for key in ("fan_in_fan_out","use_rslora","use_dora","use_qalora","lora_bias","rank_pattern","alpha_pattern",
                "modules_to_save","layer_replication","target_parameters","trainable_token_indices","alora_invocation_tokens","arrow_config"):
        require(not adapter_config.get(key), "UNSUPPORTED_LORA_EXPORT")
    adapter = load_file(candidate / "adapter_model.safetensors")
    config = AutoConfig.from_pretrained(base, local_files_only=True, trust_remote_code=False)
    require(config.model_type == "llama", "ONLY_LLAMA_EXPORT_SUPPORTED")
    with torch.device("meta"):
        model = AutoModelForCausalLM.from_config(config, trust_remote_code=False)
    expected = {name:tuple(t.shape) for name,t in model.state_dict().items()}
    if config.tie_word_embeddings: expected.pop("lm_head.weight", None)
    output.mkdir(parents=True, exist_ok=False)
    config._name_or_path = ""; config.dtype = torch.float16; config.save_pretrained(output)
    config_path = output / "config.json"
    config_path.write_text(json.dumps(ollama_compatible_config(json.loads(config_path.read_text())),indent=2),encoding="utf-8")
    tokenizer = AutoTokenizer.from_pretrained(base, local_files_only=True, trust_remote_code=False)
    require(bool(tokenizer.chat_template), "CHAT_TEMPLATE_REQUIRED"); tokenizer.save_pretrained(output)
    inventory = {}; used = set(); total = 0
    for shard in sorted(base.glob("*.safetensors")):
        with safe_open(shard, framework="pt", device="cpu") as opened:
            for name in opened.keys():
                require(name in expected and name not in inventory, "UNEXPECTED_BASE_TENSOR")
                value = opened.get_tensor(name).to(torch.float16).contiguous()
                require(tuple(value.shape) == expected[name], "BASE_TENSOR_SHAPE_MISMATCH")
                prefix = "base_model.model." + name.removesuffix(".weight")
                a, b = prefix + ".lora_A.weight", prefix + ".lora_B.weight"
                layer = None
                if a in adapter or b in adapter:
                    require(a in adapter and b in adapter and value.ndim == 2, "INCOMPLETE_ADAPTER_PAIR")
                    require(tuple(adapter[a].shape) == (4,value.shape[1]) and tuple(adapter[b].shape) == (value.shape[0],4), "ADAPTER_TENSOR_SHAPE_MISMATCH")
                    original = torch.nn.Linear(value.shape[1],value.shape[0],bias=False,device="meta",dtype=torch.float16)
                    original.weight = torch.nn.Parameter(value,requires_grad=False)
                    layer = Linear(original,"default",r=4,lora_alpha=8,lora_dropout=0.0,init_lora_weights=False)
                    layer.lora_A["default"].weight = torch.nn.Parameter(adapter[a].float(),requires_grad=False)
                    layer.lora_B["default"].weight = torch.nn.Parameter(adapter[b].float(),requires_grad=False)
                    layer.merge(safe_merge=True)
                    value = layer.get_base_layer().weight.detach(); used.update((a,b))
                require(bool(torch.isfinite(value).all()), "NONFINITE_MERGED_WEIGHTS")
                filename = f"model-{len(inventory) + 1:05d}.safetensors"
                save_file({name:value}, output / filename, metadata={"format":"pt"})
                saved = load_file(output / filename)
                require(torch.equal(value,saved[name]), "MERGED_SHARD_VERIFICATION_FAILED")
                inventory[name] = filename; total += value.numel() * value.element_size()
                del value, saved, layer
    require(set(expected) == set(inventory) and used == set(adapter), "INCOMPLETE_MERGED_MODEL")
    (output / "model.safetensors.index.json").write_text(json.dumps(
        {"metadata":{"total_size":total},"weight_map":inventory},indent=2),encoding="utf-8")
    return {"tensorCount":len(inventory),"adapterTensorCount":len(used),"standaloneWeightsVerified":True}
