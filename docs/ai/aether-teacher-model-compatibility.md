# Aether Teacher model compatibility

The native Teacher now admits **Qwen 3.5 4B** as its only base model, plus its verified candidate versions. This replaces the earlier conditional Llama fallback. Llama remains supported by legacy operator CLI regression paths; it is no longer a selectable Teacher base.

| Model | Local inference availability | Current Teacher import/train/export acceptance |
| --- | --- | --- |
| Qwen 3.5 4B | Actual prepared GGUF inference verified with Ollama 0.17.7 | Actual profile-aware 8-step CUDA training, standalone merge, pinned GGUF conversion, runtime import/probe, comparison, rollback and portable handoff passed on this laptop |
| Llama 3.1 8B | Original user Ollama installation/store preserved | Removed from Teacher model policy; historical acceptance remains documented separately |

The [official Qwen model](https://huggingface.co/Qwen/Qwen3.5-4B/tree/851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a) is pinned at `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a`. Training uses a losslessly selected text-only base from those official safetensors, rather than dequantizing an Ollama download. All 426 text tensors were checked for exact equality after serialization; 297 vision and 15 MTP tensors were explicitly excluded. The app remains a text teaching tool.

`tools/aether/qwen35-source.json` pins every permitted official file and the upstream llama.cpp converter source/Windows CPU quantizer. Explicit acquisition is separate from offline preparation and conversion. Runtime jobs download nothing. Qwen uses thinking-disabled text prefixes and requests. The existing inverse GGUF importer remains Llama-only; Qwen has its own official-weight preparation path. The merge adapter admits only the reviewed causal-LM architectures.

See [Qwen acceptance and migration evidence](aether-teacher-qwen-acceptance-2026-11-04.md) for artifact identities, real checks, resource observations, delivery and cleanup boundaries. These synthetic pipeline tests do not establish improved gameplay knowledge, lore correctness or production hosting capacity.
