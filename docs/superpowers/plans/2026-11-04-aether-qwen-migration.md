# Aether Teacher Qwen Migration Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` inline, with meaningful REDâ†’GREEN behavior tests and one final independent review. No parallel Gradle/model operations.

**Goal:** Make verified Qwen 3.5 4B the native app's only base model, then remove the owner-authorized app-owned Llama weights/test artifacts after successful replacement.

**Architecture:** Keep the existing native UI/controllers/store/owned runtime. Add a bounded explicit official-weight preparation/conversion adapter; extend local training/streaming merge for the Qwen text model; replace app model policy/source settings and stage a new private model store. Preserve the original user Ollama installation/store.

**Tech stack:** Existing CPython 3.12.10 GUI/training environments, Transformers 5.16.1, PEFT 0.18.1, CUDA Torch 2.11.0+cu128, bitsandbytes 0.50.2; pinned upstream GGUF conversion/quantization tools. Native source launcher remains the delivery.

**Spec:** [Migration design](../specs/2026-11-04-aether-qwen-migration-design.md).

## Global constraints

- Official HF revision `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a`; verify LFS SHA256 or Git blob identity for every permitted file. No remote code or authentication required; no tokens in logs.
- Text-only scope; explicit no-thinking instruction prefix must match actual inference. No vision/MTP adapter or silent tensor drops.
- Preserve finite presets, reviewed immutable inputs, durable idempotency/cancellation, resource bounds, private state, artifact receipts and explicit model selection. Keep all hosted/native health/contract behavior unchanged.
- No app-owned Llama deletion until both verification and daily Qwen acceptance pass. Never touch original Ollama store, shared training environments or unrelated services. No system installer/security changes.
- Pin converter source/binary release by commit and SHA; canonical engine manifest changes require reviewed revalidation. No automatic download from runtime jobs.

## Review focus

1. Source download substitution, path escape or unexpected tensor inventory must fail closed before model preparation.
2. Qwen hybrid attention, tied embeddings, tensor naming, norm/value-head conversion, no-thinking prefix and context accounting must remain numerically/semantically correct.
3. Existing daily profiles/lessons/chats and interrupted jobs must not become a new unreviewed dataset or resume under a new base/engine identity.
4. Runtime model/template/receipt bindings must survive import, comparison, selection, export and restart without Llama fallback.
5. Cleanup must target only inventoried app-owned Llama data, preserve active Qwen artifacts/shared environments/original user store, and run only after real acceptance.

## Task 1: Pin and prepare official Qwen text weights and conversion tools

**Files:** `tools/aether/qwen35.py`, `tools/aether/test_qwen35.py`; ignored acquisition/preparation outputs under `.codex-build/aether-qwen/`.

**Interface:** Fixed source revision/inventory; explicit operator `acquire` and offline `prepare` phases. Streaming selected/renamed safetensors preserve exact values, shape, tie policy and source provenance. A converter manifest admits only the fixed upstream conversion command and CPU quantizer, with bounded paths/processes.

- [x] Write download/inventory/path/hash and text-tensor mapping tests; observe expected missing behavior.
- [x] Implement finite, explicit acquisition and offline preparation; run regressions and verify source/model metadata before executing tools.
- [x] Acquire only permitted official files and pinned tools, prepare the actual text base, verify all tensors/license/tokenizer. Record actual disk usage/hashes.
- [x] Include focused preparation support and provenance docs in the final migration commit.

## Task 2: Admit Qwen local training and numerically verified streaming export

**Files:** `tools/aether/laptop.py`, `merge_export.py`, trainer local chat/model handling and corresponding tests.

**Interface:** Existing local presets and dataset/profile CLI contracts remain; add only explicit Qwen architecture/converter provenance. Qwen no-thinking template arguments apply locally, leaving hosted/default behavior unchanged. Streaming merge retains one-tensor memory bounds and validates all adapter keys/tied outputs.

- [x] Write real tokenizer-prefix/context/masking and tiny Qwen merge numerical regressions; observe failures.
- [x] Implement narrow Qwen text admission, no-thinking samples and architecture-correct streaming merge/conversion. Keep Llama CLI regressions intact.
- [x] Run targeted Python/host policy checks; verify a real Qwen small CUDA artifact and sequential FP16+adapter versus merged numerical output before reporting training support.
- [x] Convert/quantize the base and candidate with pinned upstream tools; record actual tensor/artifact/runtime compatibility.

## Task 3: Switch app policy and private runtime setup

**Files:** `tools/aether_teacher` setup/runtime/chat/domain/UI/controller/workflow/engine manifest and tests.

**Interface:** Base identity `qwen3.5:4b`, validated Qwen source origin, runtime store `<workspace>/runtime/qwen35-models`, verified source store path. Original app data is preserved; accepted old engine/base identities cannot resume. Comparison original means approved Qwen base.

- [x] Write regressions rejecting Llama/unsupported model origins and preserving profiles/reviews/sessions/old-job safety.
- [x] Update source guard, template/thinking payload, labels/comparison, private store and exact engine manifest. Keep the same native launcher and bounded operations.
- [x] Verify real private Qwen baseline/candidate inference, Save profile, complete durable training workflow, selection/rollback, export and fresh-process restoration. No success stubs or model auto-activation.

## Task 4: Deliver and remove authorized app-owned Llama assets

**Files:** Installed owned source/settings/shortcut; docs/ai migration/acceptance records; ignored cleanup inventory.

- [x] Request one read-only final independent review; reproduce/fix Important findings with regressions and a green relevant suite.
- [x] Reconcile/close only the known app instance safely, preserve user DB content, update installation/settings, and verify actual daily-workspace Qwen chat before cleanup.
- [x] Inventory exact app-owned Llama targets and active references; resolve/reject links/out-of-root targets; delete only the authorized model/test data after all Qwen gates pass. Preserve original Ollama and shared Python libraries.
- [x] Run final Python/desktop/server checks appropriate to changed code, review diff/secrets, commit on in-dev. Report actual model revision/artifacts/test evidence, deleted paths/space, deployment boundaries and anything unfinished; no push/deploy.
