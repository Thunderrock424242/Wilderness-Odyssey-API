# Deploying the protected Aether gateway

Phase 1 provides a protected origin and Minecraft transport. Production deployment, live restarts, active-model changes and enabling remote administration require separate owner approval. The confirmed Kinetic allocation is a 500% CPU limit and 8 GB RAM, with GPU availability unknown. This does not establish that the existing 8B model fits alongside Minecraft.

## Trust boundaries

- Minecraft -> authenticated Aether Gateway -> private Ollama.
- Cloudflare Pages browser -> Kinetic Discord/admin backend -> protected Aether Gateway.
- The browser never receives gateway, Minecraft or Ollama administration credentials.
- The gateway binds only to loopback. A trusted local HTTPS connector must reach that listener; a public origin port is unsupported.
- The Kinetic backend remains the administration intermediary. The gateway independently checks its bearer scope, Cloudflare Access JWT signature, issuer, application audience, service identity, timestamps and each operation's schema.
- No arbitrary Ollama proxy, shell, model pull, model deletion or model activation route exists.

## Build and configuration

Use JDK 21 and the repository wrapper, with one Gradle process at a time:

```powershell
.\gradlew.bat :aether-server:build '-PcodexBuildDir=.codex-build' --no-parallel
```

The gateway-only output is `aether-server/.codex-build/libs/Aether-Gateway.jar`. It does not contain model weights or Minecraft dependencies. See the [bundle guide](../docs/ai/aether-bundled-server.md) for optional native distributions; they were not rebuilt for Phase 1.

Copy [the example](aether-server.example.yml) into operator-owned configuration. Provision three different, cryptographically random secrets of at least 32 characters through the named environment variables. The allowed token alphabet is letters, digits, period, underscore, tilde and hyphen. Never commit their values or include them in a public client pack. Optional `inference_expires_at`, `monitoring_expires_at` and `administration_expires_at` settings accept ISO-8601 UTC timestamps; expired credentials fail closed. Rotation is an operator-controlled configuration/restart action requiring the production approval gate.

Set `security.minecraft_server_id` to the same ID as Minecraft's `ai_backend.server_id`. Minecraft receives only `AETHER_INFERENCE_TOKEN` (or its configured environment reference). Missing credentials cannot fall back to anonymous HTTP. Remote Minecraft endpoints require HTTPS; HTTP is permitted only for loopback.

Keep all activation flags false until hosting and measured capacity are approved. `activation.verified_model` must match `ollama.model` exactly; changing the model invalidates the gate. The administrative API cannot change these fields. Keep Ollama private and independently managed for the gateway-only distribution.

Ollama endpoints accept localhost, loopback IPs, private IPv4 addresses and private IPv6 addresses; public addresses and other DNS names are rejected. Use a private literal address or a local tunnel. This also avoids allowing an approved hostname to resolve later to a public model endpoint.

After the separate deployment approval, the gateway-only entry point is:

```sh
java -jar Aether-Gateway.jar --external --config aether-server.yml
```

Legacy anonymous configurations remain locked. Old `0.0.0.0` listener configurations are rejected. Old API-key settings are not migrated into the new scopes. Existing files are preserved, so migration is explicit.

## HTTP contract

| Operation | Required authority | Result |
| --- | --- | --- |
| GET /health | Monitoring credential | Liveness, cached model availability and bounded queue counts |
| GET /ready | Monitoring credential | 200 only if commissioned, admitted and cached model check is ready |
| GET /v1/aether/status | Inference credential | Sanitized availability and policy code; no model inventory |
| POST /v1/aether/generate | Inference credential and matching server ID | Existing bounded generation/verification protocol |
| GET /v1/admin/admission | Monitoring credential | Pause state, local revision and journal health |
| POST /v1/admin/admission | Administration credential, verified Access assertion and enabled administration | Durable pause/resume only |

Credentials do not inherit other scopes. Browser Origin or Sec-Fetch-Site headers are rejected; no CORS permission is supplied. This supplements secret separation and origin isolation, and is not a substitute for them.

Apply the Cloudflare Access service policy to administration routes (and optionally monitoring routes) separately from Minecraft's inference route. Minecraft authenticates with its own bearer credential and does not hold Kinetic's Cloudflare service token. The connector must preserve that bearer while the origin remains unreachable directly from the public network.

To configure administration later, pin `access_issuer` to the team's HTTPS `*.cloudflareaccess.com` issuer, set `access_audience` to the application's audience and `administration_client_id` to the Kinetic service token's `common_name` identity. The local connector must pass the signed `Cf-Access-Jwt-Assertion`; an arbitrary identity header is insufficient. Kinetic sends its origin administration bearer separately from its Cloudflare service credentials. Optionally pin a separate `monitoring_client_id` as well. Keep `administration_enabled: false` until the owner approves the production route.

The [Phase 1 record](../docs/ai/protected-gateway-phase-1.md) describes mutation validation, durability, replay handling and failure behavior.

## Host resources, state and privacy

The [systemd example](systemd/aether-ai.service) expects a dedicated aether account, the JAR under /opt/aether, configuration and an operator-protected credentials.env under /etc/aether, and writable state under /var/lib/aether. StateDirectory creates service-owned state; this repository does not install the unit or change host permissions.

The [Docker example](docker/docker-compose.yml) is a Linux host-network template with all services behind an explicit commissioning profile. Both listeners stay on host loopback; a connector on that same host is required. It is not a Windows/Docker Desktop networking prescription and has not been runtime tested. Native GPU access, fixed production image versions, model provisioning and secret delivery require host-specific review. There is no public port mapping.

Preserve the state directory across restarts. Its admission journal is the authoritative local pause state and mutation audit. Do not delete it to recover from an error. Corrupt/torn journals prevent startup; bounded journal exhaustion rejects new mutations. Routine request audits retain only timestamps, operation, scope and outcome in two bounded files. No prompts, conversations, bearer tokens or JWTs are written. Legacy message/response logging settings no longer enable conversation logging. Disable request-body and authorization-header logging in the connector too.

Health is an observation of an installed model, not a GPU, latency or answer-quality guarantee. The existing request, body, connection, generation-queue and total-deadline bounds remain. The default concurrency of two is a configuration default, not a recommendation for the unverified 8 GB host.

## Evidence and remaining acceptance

Java tests use mock Ollama and locally signed Access fixtures, including executable gateway startup. They do not contact Kinetic, Cloudflare or a real model. Production HTTPS/Access configuration, persistent-volume behavior, real capacity and live Minecraft gameplay/multiplayer responsiveness require separate acceptance after approval. No production service has been deployed or enabled by this phase.
