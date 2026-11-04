# Aether Teacher model compatibility

The owner's current preference is Qwen 3.5 4B **if the complete workflow works**; otherwise retain the existing verified model. For this delivery the fallback is Llama 3.1 8B, because Qwen is not supported by the current reviewed training/export adapters.

| Model | Local inference availability | Current Teacher import/train/export acceptance |
| --- | --- | --- |
| Installed Llama 3.1 8B | Real app inference verified | Actual profile-aware 8-step CUDA adapter, standalone merge, Ollama conversion, inference, comparison, rollback and handoff verified on this laptop |
| Qwen 3.5 4B | Published as `qwen3.5:4b` in Ollama | Not verified or approved in this app; existing GGUF import and standalone export explicitly reject non-Llama architectures |

The [official Qwen model card](https://huggingface.co/Qwen/Qwen3.5-4B) identifies a `qwen3_5` multimodal model and the [Ollama model inventory](https://ollama.com/library/qwen3.5:4b) publishes its inference artifact. Availability in Ollama establishes an inference route, not compatibility with this repository's weight conversion and fine-tuning workflow. The current repository guards are `ONLY_LLAMA_GGUF_SUPPORTED` in `tools/aether/gguf_import.py` and `ONLY_LLAMA_EXPORT_SUPPORTED` in `tools/aether/merge_export.py`; setup/chat also admit the reviewed Llama identity only.

A future Qwen migration needs its own reviewed source/license/template/tokenizer identity, compatible HF/PEFT loading and completion masking, tensor mapping or a verified native source-weight path, numerical conversion/merge checks, a real small optimizer artifact, actual Ollama import/inference, resource admission and portable bundle verification. Do not rename Llama artifacts, weaken architecture guards, download weights automatically, or advertise Qwen training without those checks. This assessment does not claim Qwen can never be supported.
