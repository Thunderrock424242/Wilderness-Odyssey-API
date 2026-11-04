# Aether playground server deployment and verification

The wire contract is frozen at bot commit
`60a962005e1684610da1b6588e63b6fc195f0fd5` under `contracts/60a9620`.
This implementation belongs to the standalone `aether-server` module. It never
uses Minecraft player memory or tools and never activates a training candidate.
Native `/health`, `/ready`, generation and admission keep their existing contracts.

## Implemented endpoints

| Endpoint | Credential |
| --- | --- |
| GET `/v1/admin/playground/capabilities` | Monitoring plus configured Access identity |
| POST `/v1/admin/playground/sandbox` | Administration plus Access identity |
| POST `/v1/admin/playground/training/jobs` | Administration plus Access identity |
| GET `/v1/admin/playground/training/jobs/by-request/:requestId` | Administration plus Access identity |
| POST the preceding path plus `/cancel` | Administration plus Access identity |
| GET `/v1/service/health`, `/capabilities`, `/operations/:id` | Separate main-service read token plus Access when configured |
| POST `/v1/service/operations` | Separate main-service write token plus Access identity |

Main-service operations support **only `ai.pause`**. It uses the real native
admission journal, revision check and deterministic native command ID. Model
activation, unloading, probing and inference-setting writes are absent from
capabilities. The older admin model schema uses opaque `model-<24 hex>` aliases
because its IDs exclude Ollama punctuation. Playground IDs remain exact runtime
IDs. Main capabilities requires an explicit `ollama.context_tokens` value so it
can report established native settings; otherwise it returns 503. Native default
behavior is preserved when this optional setting is absent/zero.

Health has a per-boot UUID, increasing sequence and measured Ollama-check timestamps
and latency. Observations older than 30 seconds become unknown. The standalone
service has no authoritative Minecraft metrics feed: all Minecraft metrics and
its status are explicitly null/unknown. Connecting a trusted, timestamped Minecraft
metrics producer remains deployment/integration work; this service never invents TPS,
MSPT, player counts or game/modpack versions.

## Private configuration

Merge `deploy/aether-playground.example.yml` into the existing private configuration;
it is a section example, not a replacement for hosting, model or activation settings.
The gateway remains loopback-only behind the existing trusted HTTPS connector.
Keep TLS/Access deployment and production inference commissioning intact.

Store these five independent Bearer secrets in the API host secret manager/environment:

| API host environment | Used by |
| --- | --- |
| `AETHER_INFERENCE_TOKEN` | Existing Minecraft inference bridge, unchanged |
| `AETHER_MONITORING_TOKEN` | Kinetic `AETHER_GATEWAY_MONITORING_TOKEN` |
| `AETHER_ADMINISTRATION_TOKEN` | Kinetic `AETHER_GATEWAY_ADMINISTRATION_TOKEN` |
| `MAIN_SERVER_READ_TOKEN` | Kinetic `MAIN_SERVER_READ_TOKEN` |
| `MAIN_SERVER_WRITE_TOKEN` | Kinetic `MAIN_SERVER_WRITE_TOKEN` |

Each must be a different 32–256 character token using `[A-Za-z0-9._~-]`.
Missing/expired credentials reject requests. No tokens belong in YAML values,
source control, browser assets, request URLs, candidate metadata or logs. Existing
`*_token_env`/`*_expires_at` security settings can name secret references/expiry.
Administration and main-service writes require their respective enable flags
and signed Access assertions. Disabled write authority does not inherit read scope.

Set these values **privately on Kinetic**, then restart the bot:

```dotenv
AETHER_GATEWAY_ORIGIN=https://YOUR-APPROVED-AETHER-HOST
AETHER_GATEWAY_SERVER_ID=EXACT-security.minecraft_server_id
AETHER_GATEWAY_MONITORING_TOKEN=SECRET_MATCHING_API_MONITORING
AETHER_GATEWAY_ADMINISTRATION_TOKEN=SECRET_MATCHING_API_ADMINISTRATION
AETHER_GATEWAY_ACCESS_CLIENT_ID=PLAYGROUND_ACCESS_SERVICE_CLIENT_ID
AETHER_GATEWAY_ACCESS_CLIENT_SECRET=PLAYGROUND_ACCESS_SERVICE_CLIENT_SECRET
MAIN_SERVER_ORIGIN=https://YOUR-APPROVED-AETHER-HOST
MAIN_SERVER_ID=EXACT-security.minecraft_server_id
MAIN_SERVER_READ_TOKEN=SECRET_MATCHING_API_MAIN_READ
MAIN_SERVER_WRITE_TOKEN=SECRET_MATCHING_API_MAIN_WRITE
MAIN_SERVER_ACCESS_CLIENT_ID=MAIN_ACCESS_SERVICE_CLIENT_ID
MAIN_SERVER_ACCESS_CLIENT_SECRET=MAIN_ACCESS_SERVICE_CLIENT_SECRET
```

Origins must be fixed HTTPS roots without embedded credentials, paths, query or
fragment. Access ID/secret pairs are paired settings. This API's write security
requires Access: configure the corresponding pairs, not just Bearer secrets.
The existing `AETHER_ENABLED` Discord setting does not install a remote trainer.

On the API, configure `security.access_issuer`, production application
`access_audience`, and the expected assertion `common_name` in
`monitoring_client_id`/`administration_client_id` and
`service_read_client_id`/`service_write_client_id`. The bot uses one service identity
for each read/write pair, while Bearer scopes remain distinct. Use separate Access
applications/audiences/credentials for other environments. The origin verifies
RS256 signature, issuer, audience, service identity and timestamps; forwarded
service-auth headers alone grant no authority. Permit only the required paths in
Access. Do not expose the loopback listener or Ollama publicly.

## Approved models, presets and runtime prerequisites

The default approved-model list is **empty**. No production model has been approved
or installed by this change. For each reviewed base, export an immutable local
Hugging Face model directory containing safetensors weights, config and tokenizer;
an Ollama GGUF model alone is insufficient for training. No remote Python model
code, pickle weights, symlinks, model URLs or downloads are accepted. Export files
as read-only to the service account and mount approved base directories read-only.

Compute the reviewed directory's fingerprint with the shipped trainer:

```sh
/opt/aether/trainer-venv/bin/python trainer.py --fingerprint /srv/aether-models/APPROVED-BASE
```

Then configure an entry (these values are placeholders, not implicit approvals):

```yaml
models:
  - id: exact-approved-ollama-id:tag
    label: Reviewed base display name
    training_directory: /srv/aether-models/APPROVED-BASE
    training_sha256: REVIEWED_64_CHARACTER_LOWERCASE_SHA256
```

The same logical ID must name the matching installed Ollama model for sandbox
inference. A sandbox-only model may omit the training path/digest. Training checks
all local files against the approved digest before loading; accepted jobs also
pin their trainer/config binding across restart. Changed bindings fail existing
queued jobs instead of silently retargeting them.

Supported presets are fixed, not request-supplied code:

| Preset | Examples | Optimizer steps | Max prompt+target tokens |
| --- | --- | --- | --- |
| `lora-smoke` | 1–8 | 8 | 256 |
| `lora-small` | 1–100 | 100 | 1024 |

Both use rank-4 LoRA, alpha 8, batch size 1, learning rate 0.0002, deterministic
seed, completion-only loss and all-linear targets. Samples exceeding the tokenizer
context fail the job; they are never silently truncated. Presets are additionally
allowlisted in private YAML. Requests cannot supply scripts, paths or parameters.

Production training prerequisites:

- JDK 21; checked-in Gradle wrapper for packaging. Ollama reachable at the existing
  fixed private URL with the approved sandbox models installed.
- Linux with cgroup v2. The trainer verifies inherited finite `memory.max`,
  `cpu.max` and `pids.max` rather than trusting a readiness flag. A reviewed
  example systemd drop-in is `deploy/systemd/aether-playground-limits.conf.example`.
  Apply limits to the actual service/container; other hosting panels need equivalent
  cgroup limits. Windows training admission intentionally remains unavailable.
- A dedicated operator-installed Python virtual environment with the packages in
  `aether-server/training-requirements.txt`. The CPU smoke used Python 3.12 from
  the existing voice-tool environment. Choose a PyTorch CUDA wheel compatible with
  the actual NVIDIA driver when using `device: cuda`; merely detecting a GPU is
  insufficient. PEFT/Transformers must support the reviewed model architecture.
- RAM headroom, configured free VRAM and disk headroom. Training is unquantized:
  conservative admission requires at least twice the local weight bytes plus
  512 MiB within the selected CPU/GPU budget. This is a preflight floor, not a
  promise that every architecture fits. OOM, unsupported architectures and token
  overflow fail honestly. An 8B base is not promised to fit a 16 GiB laptop GPU.
- One training child at a time; default 8 active/queued jobs, 100 new jobs/day
  globally and 10/day per audited actor. The accepted snapshot is at most 262144
  UTF-8 bytes. Default child deadline 600 seconds, 2 CPU threads, 16 GiB memory
  ceiling, 8 GiB GPU allocation budget, 256 MiB artifact bound and 2 GiB storage
  high-water mark. Host filesystem quota is required to make total disk bounds
  hard between periodic checks; Python also imposes a per-file size bound.
- Reserve resources for the production runtime. Child CPU/memory limits inherit
  from the gateway unit; size the unit for JVM plus trainer. The trainer receives
  a minimal environment without service credentials, hub tokens or production data.

Feature flags reflect successful runtime/hardware probes. Training starts disabled;
after installing the above, configure the approved model/digest and presets, enable
training, restart, and wait for a successful model probe. A configured flag alone
cannot make training available. Full model hashing may take time on large bases.

## Persistence, cancellation and retention

Private paths under `playground.state_directory`:

- `playground.sqlite`: SQLite FULL synchronous transactions, unique request IDs,
  exact replay fingerprints, immutable literal snapshots, actor quota digests,
  worker claims, cancellation tombstones, runtime bindings and main-service operations.
- `trainer.py`: the bundled fixed trainer, installed by the gateway.
- `work-<requestId>/`: private snapshot consumed by a claimed trainer; erased after
  terminal processing and recovered after crashes.
- `candidate-<requestId>/`: LoRA adapter safetensors, adapter config and provenance
  metadata. Candidates are not registered with Ollama or production Minecraft.

Snapshots are never queried from production conversation storage or changed by later
browser edits. Acceptance commits before replying; replay returns the original job,
conflicting immutable payloads return 409. An expired new request cannot be accepted.
A durable worker claim is never launched again after restart: interrupted claimed
jobs become failed/cancelled. Queued unclaimed jobs retain their original binding.
Linux parent-death handling and service cgroup shutdown terminate owned children.

Cancel-before-acceptance commits a tombstone and returns the contract's explicit
receipt. A late authorized valid submission under that ID becomes cancelled without
training. Running cancellation races atomically with completion; completed wins only
after real validated, fsynced candidate files exist. Reconcile by original request ID
after ambiguous responses. Never invent a replacement request to retry a lost ack.

Default private snapshot/actor and candidate retention is 7 days (configurable
1–30 days); terminal work directories are erased promptly. Maintenance runs in the
worker at startup and about once/minute while idle; a running bounded job may delay
maintenance by its deadline. Secure-delete SQLite erases cleared private values.
Minimal protocol receipts, hashes, cancellation identities and fingerprints remain
for the lifetime of this server identity, bounded at 50000 training identities and
50000 main operations (database page cap 256 MiB). These compact durable receipts are
required to prevent old requests from launching again; they contain no prompt/target,
reason, plaintext actor or credentials after retention. At capacity writes refuse
instead of discarding idempotency. Do not delete the database to regain capacity;
review archival/new-identity migration explicitly. Native admission retains its
existing separate finite journal limits and native audit rotation.

The POSIX state directory must be owner-only; new directories are created with 0700
and an existing group/world-readable directory is rejected. Provision Windows
development directories privately through the operator; this code does not modify
NTFS permissions. Apply finite host journal/log retention (for example 7 days) and
encrypted backup expiry no longer than the private-data policy. Existing metadata
audit files rotate at their established bounded size; they never store prompt bodies.

For backup: stop the service/owned trainer, copy the entire private state volume and
its reviewed configuration using the approved encrypted backup mechanism, and resume.
For restore: keep the exact server ID, model bindings and paths; restore the whole
volume before starting one gateway instance. Do not copy a live SQLite database or
restore an old backup over a running writer. Candidate erasure includes expired
backup copies; secure deletion is not a guarantee against retained host snapshots.

## Build and deployment sequence

1. Build `:aether-server:check :aether-server:assemble` with JDK 21 and the checked-in
   wrapper, using `-PcodexBuildDir=.codex-build --no-parallel`. The gateway artifact is
   `aether-server/.codex-build/libs/Aether-Gateway.jar`.
2. Install that artifact into the existing API host deployment; install the trainer
   virtualenv and reviewed local base separately. Keep the existing launcher mode.
   For external Ollama: `java -jar Aether-Gateway.jar --external --config PRIVATE.yml`.
   Existing `--config PRIVATE.yml` remains supported. A bundled native distribution
   still requires its normal explicit bundle inputs; this change does not rebuild
   or deploy Ollama/model blobs.
3. Configure host secrets/YAML, private durable volume, read-only model mount, cgroup
   limits and filesystem quota. Validate YAML using the existing launcher
   `--validate-config`. Restart the existing service, then test health and signed
   capabilities. Native inference activation remains under existing commissioning.
4. Configure Kinetic privately as above, ensure its bot is at the pinned contract
   revision or a reviewed compatible descendant, and restart it. Verify server ID,
   native health/readiness, administration scopes and capabilities through the real
   Access/gateway chain.
5. From the website: run sandbox inference, explicitly save/review synthetic examples,
   submit `lora-smoke`, reconcile by request ID, test cancel-before-acceptance and
   running cancellation, then inspect the private candidate metadata/weights. Do not
   activate it. Record deployed API/bot/gateway/website revisions separately.

No host deployment, Kinetic secret change, production model activation or complete
website→gateway→bot→API verification was performed. Subsequent local GPU training
and the laptop-to-Ollama export workflow are documented in
[the laptop training guide](aether-laptop-training.md); they do not weaken hosted
Linux/cgroup admission or establish that Kinetic has sufficient inference capacity.

## Reproducible verification

Standalone JUnit tests cover strict schema/hash/byte validation, replay/restart,
cancellation races, quotas, binding persistence, backup restore, native trust and
compatibility, isolated Ollama transport, main operations and candidate validation.
`tools/aether/verify-playground-contracts.cjs` checks captured Java HTTP responses
against the verbatim pinned Zod schemas using an existing bot `node_modules` directory:

```powershell
.\gradlew.bat :aether-server:test :aether-server:verifyStandalone '-PcodexBuildDir=.codex-build' --no-parallel
node tools/aether/verify-playground-contracts.cjs aether-server/.codex-build/peer-roundtrip.json PATH-TO-BOT/node_modules
```

Run the real offline optimizer smoke using the installed trainer environment:

```text
python -B aether-server/src/test/python/test_training_policy.py
python -B aether-server/src/test/python/training_smoke.py NEW-PRIVATE-OUTPUT-DIRECTORY
```

The test creates a 45952-parameter random GPT-2 baseline locally, trains two explicit
synthetic reviewed examples for eight real LoRA steps, saves/reloads the adapter and
checks that base weights are unchanged. This direct CPU test intentionally does not
bypass production HTTP resource admission. Set `AETHER_TRAINING_SMOKE_PATH` to that
output and rerun JUnit to include Java validation/corruption checks of the real weights.

Observed local result: CPU PyTorch 2.13.0, Transformers 5.16.1, PEFT 0.18.1,
8 steps, loss 2.621537446975708, 9176-byte adapter,
SHA-256 `ad22d5e8f47ff50cb0835794d66460f7c7099228022cd1d22e682f25d1e59975`.
The test used two CPU threads; no GPU memory was allocated. It proves training
mechanics and persisted candidate validation, not production Aether model quality,
CUDA readiness, Linux cgroup behavior or live deployment success.
