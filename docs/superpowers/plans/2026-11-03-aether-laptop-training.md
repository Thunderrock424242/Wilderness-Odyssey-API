# Laptop Aether training and deployment export

> Execute inline with executing-plans and test-driven-development. The user approved
> laptop training, local evaluation/export and deployment to the Minecraft host.

**Goal:** Produce and verify a real GPU-trained candidate on this laptop, then prepare
an immutable Ollama import package without activating a production model.

**Architecture:** Reuse the fixed offline trainer and reviewed snapshot/base hashes.
Keep the hosted worker's Linux resource admission unchanged. Add a local operator CLI
with explicit approvals, finite GPU/time/example/context budgets and separate output.
Support LoRA and a laptop-specific 4-bit QLoRA profile. Export against the exact base,
merge adapters to safetensors and produce a versioned Modelfile/import manifest.

**Environment:** Windows laptop, RTX 4090 Laptop GPU / 16 GiB VRAM. WSL is absent;
the install command returned an environment failure. Official native Windows CUDA
PyTorch/bitsandbytes support can provide immediate local verification. A separate
ignored virtualenv must not modify the existing voice environment.

**Global constraints:** Preserve native server trust/health/admission and pinned peer
contracts. Never weaken the hosted Linux gate to enable laptop training. Never ingest
production conversations or activate a candidate automatically. Existing unrelated
client/water edits remain outside this work. Production model choice is still reviewed;
small synthetic/pretrained verification models do not approve production weights.

## Tasks

- [x] Add local operator validation tests: exact dataset/base/candidate hashes, bounded
  presets, path containment, no overwrites, no remote code/downloads during training.
- [x] Implement the local runner, GPU admission and QLoRA profile with deadlines,
  isolated child processes and finite resource configuration; reuse the training core.
- [x] Add export tests: exact base association, adapter integrity, merged weights,
  tokenizer/template preservation, package manifest and explicit Ollama import command.
- [x] Implement safe local export; verify a genuine CUDA optimizer run and a merged
  candidate. Try a small approved-for-verification pretrained model and local Ollama
  inference without modifying the active production model.
- [x] Run Python and affected standalone Java checks, independent review, document
  exact laptop commands and host deployment steps; commit only this focused change.

## Review focus

- Laptop mode must not become an HTTP or arbitrary-command bypass for hosted training.
- Candidate and export hashes must pin the exact base and immutable reviewed snapshot.
- Quantized training must reload an unquantized exact base for portable merging.
- Export and local import must preserve the chat template and remain separate from
  production activation.
- Tests and evidence must distinguish real CUDA training from CPU/synthetic checks.

## Ledger

- Baseline in-dev at 480127c46771fd490ae564f73a0d4c274a0013f3; dirty user work preserved.
- WSL install attempt did not install WSL. Continue supported native Windows setup;
  report the Linux prerequisite separately instead of modifying Windows security settings.
- User retained Llama 3.1 8B and requested using the installed Ollama model rather
  than configuring Hugging Face access. Imported the exact local Q4_K_M blob using
  streamed official tensor mapping, preserving its Llama 3.1 positional factors.
  Provenance explicitly records that original full precision is not recovered.
- Actual 8B CUDA NF4 run completed eight real optimizer steps in 44.469 seconds;
  saved/reloaded changed adapter SHA-256
  `17dea6e45ab02759f359650fc3403732949bc27d3745ad4a8a34f5e1969a7b9c`.
  Peak GPU 8449.58 MiB, observed process-tree RSS 16016.97 MiB. Two synthetic
  examples establish pipeline mechanics, not production quality.
- Real small-model tests establish exact tensor/logit equivalence for streaming
  GGUF import and streaming PEFT safe merge. Added modern/legacy RoPE config
  compatibility for Ollama's importer.
- Independent review findings addressed: OS-owned Windows child lifetime (including
  forced parent-death test), staging/cleanup before publication, streamed export
  memory usage, and durable binding of a successful Ollama import's GGUF digest to
  the exact merged manifest before bundling. Hosted admission remains unchanged.
- Actual Ollama Q4_K_M import produced a 4921247072-byte GGUF, SHA-256
  `6235be5c4631e8961e13e27bcbdd203e3a6272bad6815836a36c6eb41ae3d26b`.
  Runtime layer count and positional vector match the merged configuration.
- Real inference caught Ollama's raw-template fallback when importing newer HF
  exports. Runtime import now explicitly restores the original source Go template,
  stop tokens and license, preserves LF bytes on Windows, and binds the displayed
  Modelfile in a v1.1 receipt. Receipt-backed template repair preserved GGUF bytes.
  The final real 8B response was exactly `READY`, stopped normally and unloaded.
- Final standalone JUnit: 77 passed, no failures/errors/skips. Laptop policy and
  lifecycle tests: 21 passed. Real small-model loader/merge comparisons: four
  passed. Hosted policy: four passed. Fresh hosted CPU optimizer smoke reproduced
  the expected adapter hash and unchanged base. Isolated pip check passed.
- Final bounded cold import passed under a 24 GiB Windows job ceiling, publishing
  the exact trained base fingerprint after verification. Observed RSS 10750.30 MiB,
  duration 159.219 seconds. Temporary duplicate weights were removed after comparison.
  Final portable bundle hashes verified; no production activation or host deployment.
