# Aether Teacher Qwen 3.5 4B migration

The owner explicitly requested Qwen as the main model, removal of Llama from the app, and deletion of **app-owned Llama weights and test artifacts after Qwen passes**. The original user Ollama store and unrelated services remain outside that deletion authorization. This changes the model implementation inside the already approved native teaching app; the teaching UI and its safety/durability contracts remain in scope unchanged.

## Approach

Use the official ungated `Qwen/Qwen3.5-4B` safetensors at commit `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a`. Prepare a losslessly selected/renamed **text-only** training base with the pinned Transformers Qwen3.5 causal-LM class. Exclude vision/projector and MTP tensors explicitly, preserve tokenization/license and record every source hash and excluded tensor. Train job/personality/lesson text as before. Image/video teaching is not added.

This is the implementer's selected route. Reconstructing Qwen training weights from a quantized Ollama artifact would require a new inverse tensor converter and lose precision; the existing Llama converter must not simply have its guard removed. A chat-only Qwen switch would leave teaching/training unsupported and does not meet the request.

Reuse the reviewed bounded trainer and streaming PEFT merge with a narrowly admitted Qwen text architecture. Use a pinned upstream llama.cpp converter and Windows CPU quantizer to produce a standalone Q4_K_M GGUF; do not write a speculative Qwen tensor converter. Pin source/binary inventories and run conversion offline under finite owned-process budgets. Stage network acquisition only as an explicit operator preparation action, never as an HTTP/job side effect.

The official source is multimodal (`qwen3_5`) while the derived text base is `qwen3_5_text`. Preserve Qwen's ChatML instruction/stop behavior and explicitly disable thinking for the short teaching responses/training prefix. Verify rendered system/user/assistant text against the HF template; no automatic profile save or lesson approval is added.

## Runtime and model policy

The app admits `qwen3.5:4b` plus verified app candidate identities; Llama is removed from app setup/chat/comparison. Legacy CLI support can remain for regression/backward compatibility, but it is not a selectable app model. Preserve profiles, lessons, sessions, database caps, durable acceptance/cancellation, engine/base/profile bindings, explicit candidate selection and no automatic production activation.

Prepare the Qwen baseline in an app-owned source Ollama store and verify its actual model/license/template/parameter layers. Continue using the existing store-copy/private-runtime lifecycle. Use a separate private `runtime/qwen35-models` directory so the old daily model store is not deleted before the replacement is verified. If the installed Ollama is incompatible with the converter artifact, use a pinned **app-owned** runtime download rather than changing the user's original Ollama installation or service.

Retain loopback port 11435, separate private home/store, owned Windows process trees, bounded I/O and finite budgets. Serialize GPU/model operations. Do not stop an unrelated runtime, weaken native gateway health/admission, change peer contracts, ingest conversations automatically, alter system permissions/security, or deploy remotely.

## Acceptance and cleanup

Before switching the installed app or deleting old assets: verify original-source integrity, deterministic text-base inventory, correct training prefix/masking, a real small CUDA optimization with changed/reloaded finite weights, numerically checked streaming merge, actual GGUF conversion/quantization/import, coherent bounded Qwen chat using saved profiles, comparison/selection/rollback, export hashes and fresh-process restoration. Unit success alone is insufficient.

Then update the owned installation and daily settings, preserve its user-authored DB content, and verify real daily-workspace Qwen chat. Only after those checks may cleanup remove inventoried app-owned Llama model stores, dequantized weights and synthetic acceptance/candidate/export artifacts. Resolve every absolute target; require an allowlisted owned root, reject links/overlap, check active references and show the exact cleanup inventory in the record. Never delete the entire `aether-laptop` environment: its pinned libraries are reused by the working training interpreter. Preserve the original `~/.ollama/models` and non-Llama/hosted CPU test assets.

Portable frozen `.exe` packaging remains a separate known limitation; this model migration continues the working native Python-GUI launcher and desktop shortcut.
