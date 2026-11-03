# Aether server playground implementation plan

> Execute inline using executing-plans and test-driven-development. The user approved
> the pinned handoff and requested implementation on in-dev; preserve existing dirty work.

**Goal:** Implement the server peer and main-service contracts at bot revision
`60a962005e1684610da1b6588e63b6fc195f0fd5`.

**Architecture:** Extend the standalone JDK 21 gateway, reuse its scoped Bearer and
Access JWT checks and native admission journal. Isolated Ollama sandbox calls never
enter the production prompt/memory path. SQLite transactions own jobs, cancellation
tombstones and service-operation identities. A single bounded worker runs the bundled
offline Python LoRA trainer against approved local safetensors models, with explicit
resource policy. Candidates remain private and inactive.

**Spec:** Frozen handoff/contracts under docs/ai/contracts/60a9620.

## Global constraints

- Native health/readiness/admission remain compatible; no fake successes.
- Monitoring, administration, main-read, main-write and inference secrets are distinct.
- Exact intended identity, strict bounded JSON, UTF-8 hash before dataset parsing.
- One Codex-controlled Gradle process at a time, isolated .codex-build output.
- No automatic production ingestion, model downloads or activation.
- Preserve unrelated uncommitted edits; work in the requested in-dev checkout.

## Tasks

1. Freeze contracts and fixtures; add strict validation tests for identity, limits,
   duplicate/extra fields, literal hash, ordered dataset and expiry. Implement
   PlaygroundContract and PlaygroundConfig.
2. Add restart/replay/conflict/cancellation-race tests. Implement transactional
   PlaygroundStore and bounded TrainingWorker, fail interrupted jobs without reruns;
   retain fingerprints while erasing private snapshots/artifacts on finite deadlines.
3. Test isolated inference and real artifact validation. Add Ollama sandbox method and
   fixed offline LoRA trainer with hardware preflight, deadlines, resource bounds and
   candidate hashes; run a genuine small training smoke when runtime is available.
4. Test HTTP scopes and pinned response fixtures. Route playground and main-service
   endpoints through existing gateway, separate main service credentials, durable
   ai.pause operations using native admission revision/idempotency. Unknown Minecraft
   metrics until measured; omit unsupported operations.
5. Run standalone compile/tests/check, contract round trips with pinned Zod schemas,
   review diff, document exact host/Kinetic config and runtime prerequisites, commit
   only this change. Report live verification separately from tests.

## Review focus

- Lost acknowledgements and restart must never rerun training.
- Cancellation winning before acceptance must survive restart and late submission.
- Candidate success requires validated persisted weight bytes, not an exit code.
- Retention cannot erase durable request identities or permit old jobs to relaunch.
- Read credentials must never authorize mutations; Access identity must be verified.

## Execution ledger

- Baseline: in-dev at 7b800ed6, existing dirty client/water work preserved.
- Ruling: operate in the requested checkout; isolated source additions and explicit
  staging protect existing work. No branch/worktree switch over dirty user changes.
- Tasks 1–5 implemented inline. Final standalone check/assemble passed, 77 JUnit
  tests including the real optimizer candidate validator, no skipped tests.
- Pinned Zod schemas round-trip actual Java responses. The frozen AetherGatewayClient
  accepts the durable cancellation receipt and reconciles the original request ID.
- Real CPU training produced/reloaded a 9176-byte LoRA adapter after 8 optimizer
  steps; evidence is in docs/ai/aether-playground-training-evidence-2026-11-03.json.
- Independent read-only review completed: strict dataset wrapper, approved base
  binding, truthful main settings and definitive admission-capacity findings fixed.
- Ruling: default model approvals remain empty. Operators must review a local
  safetensors base and pin its directory digest; this session cannot approve or
  install production weights by inference from an Ollama model name.
- Ruling: retain compact protocol receipts/fingerprints for the bounded server-identity
  lifetime; erase snapshots/actors/candidates on finite retention. Discarding request
  identities would undermine durable idempotency.
- Ruling: Linux cgroup enforcement is required for production training. CPU optimizer
  verification on Windows exercises the trainer directly and does not claim host
  admission, CUDA, Kinetic deployment or production model verification.
