# A.E.T.H.E.R. remote backend

For the optional JAR with native Ollama and bundled model weights, see [the bundled server guide](aether-bundled-server.md). Bundled weights are not a capacity approval for Kinetic. See [Phase 1 commissioning and administration](protected-gateway-phase-1.md).

## Architecture and audit

Development: Minecraft server -> localhost Aether gateway (8085) -> localhost Ollama (11434).
Production: Minecraft server -> HTTPS reverse proxy -> Aether gateway -> private Ollama.

Only the logical Minecraft server sends AI requests. Ordinary Minecraft clients receive dialogue and existing voice metadata. The protected gateway requires an inference-only bearer credential and binds that credential to the configured Minecraft server ID. Website administration travels through the Kinetic backend; the browser receives no gateway credentials.

The audited checkout used `OllamaChatClient` and `OllamaLocalRuntime`, rather than the older `LocalModelClient` named in the request. It did not contain an active GGUF downloader, bundled model extractor, or AI process-based `BackendStatus`.

| Responsibility | Existing owner | New owner |
| --- | --- | --- |
| Chat invocation, lore inventory scan, biome/dimension and meteor proximity | AIChatListener | AIChatListener, captured before background work |
| HTTP model transport | OllamaChatClient | AetherBackendClient calls the gateway |
| Ollama discovery, autostart and readiness | OllamaLocalRuntime | Standalone bundled launcher or operator-managed external Ollama; gateway observes readiness |
| Canon, system prompt, specialist routing and factual review | AetherSystemPrompt plus ai_config.yaml | Standalone service and aether-prompts.yml |
| Recent conversation history | MemoryStore | Bounded, transient, save/player-isolated MemoryStore |
| Persistent personal notes | AIPlayerProfileStore | Same local store; sending notes to backend defaults off |
| Legacy global notes | Deprecated AIKnowledgeStore | Preserved unchanged; not used by active chat |
| Deterministic fallback | AIFallbackResponder and ai_fallback resources | Preserved in Minecraft |
| Scheduling | AsyncTaskManager | Same bounded I/O pool and server executor |
| Speech | VoiceIntegration and optional Python voice service | Preserved; Python remains manual and opt-in |

The original six specialists remain Aegis, Eclipse, Terra, Helios, Enforcer and Requiem, with Aether as the central personality. Requests can select a canonical speaker. The gateway owns model generation parameters and performs a second factual-verification pass before returning usable dialogue. Display and voice use the same canonical verified answer; a separately generated speech field cannot add claims.

The deprecated game-side `AetherSystemPrompt` builder remains as a compatibility/regression reference. It is not used to construct network requests. Custom permanent prompts must now be installed on the gateway. Existing configuration and lore/profile files are preserved on disk.

## Minecraft configuration

Edit the server's `config/ai_config.yaml`; restart the logical server to apply changes.

```yaml
ai_backend:
  enabled: true
  mode: "remote"
  server_id: "wilderness-production-01"
  inference_token_env: "AETHER_INFERENCE_TOKEN"
  circuit_cooldown_seconds: 30
  max_concurrent_requests: 2
  send_player_memory: false
  remote:
    base_url: "https://ai.example.com"
    timeout_seconds: 30
    retry_attempts: 3
    retry_backoff_millis: 250
  local_dev:
    enabled: false
    base_url: "http://127.0.0.1:8085"
```

Set the named environment variable in the Minecraft server process to the gateway's inference credential. Never distribute it in a public modpack, website, client packet, or YAML file. Monitoring and administration credentials must be different and must never be supplied to Minecraft. Update both applications when migrating from the former anonymous protocol. Old `api_key` values and `AETHER_BACKEND_API_KEY` are ignored; they do not satisfy the new authentication contract. Only trusted, explicitly provisioned integrated servers may use this service; public single-player enrollment is not implemented.

For development, either set `remote.base_url` to `http://127.0.0.1:8085`, or select `mode: local_dev` and set `local_dev.enabled: true`. Local development requires an independently started gateway. The local mode rejects non-loopback endpoints.

Old `local_model` or `settings.provider: ollama` configurations produce a deprecation warning. Their model URL and autostart settings do not activate networking or processes. Add the explicit new section to enable the gateway; the loader does not silently redirect Ollama's port to the new service.

The source configuration is not overwritten. Malformed YAML and unsupported modes fail closed for backend access, while local fallback remains available. Safe YAML construction rejects arbitrary object tags and duplicate mapping keys without logging the offending source text.

## Request data

`POST /v1/aether/generate` receives a request ID, operator-selected server ID, dimension, player UUID/name, selected speaker, message, structured context, and bounded history. Context includes dimension, biome, surface tag, activation interface, collected lore IDs, and recognized meteor/discovery tags. A lore ID is evidence of collection, not permission to invent that document's contents.

Save paths and internal save/profile keys are not transmitted. At most 128 context tags are sent; location, interface and meteor observations are added before lore IDs. Current player chat appears once, separately from previous turns.

Only addressed chat or an active onboarding exchange enters the AI path. One pending turn per player prevents that player's requests racing their own history. A session accepts at most 32 pending chats; the transport defaults to two concurrent generation calls. Overflow of the shared worker queue uses deterministic fallback.

## Deadlines and fallback

The total transport deadline includes retries and complete response-body reading. Defaults are 30 seconds, three total attempts, 250 ms initial backoff and a 30-second circuit cooldown. Timeout values are bounded to 1-60 seconds; attempts to 1-3; backoff to 0-2000 ms; cooldown to 1-300 seconds; concurrent HTTP requests to 1-8.

Access-denied responses from an external proxy, overload and timeouts are not retried. Explicit 502/504 gateway failures and eligible transport failures can retry within the same total deadline. Requests retain the same ID across retries. The next request after cooldown probes the backend again.

Offline service, missing model, rejected verification, full gateway queue and unusable responses leave deterministic recovered-intent replies available. Authentication, activation, pause and access-restriction denials instead return an explicit service notice, including during circuit cooldown. Missing factual answers remain archive gaps. Network failures do not crash Minecraft.

`BackendStatus` is a cached observation: enabled mode, sanitized endpoint, reachability, model readiness/name, latency and a safe failure code. Reading status never makes a network request.

## Threading and lifecycle

AIChatListener gathers Minecraft data on the logical server thread. Background work receives immutable text, IDs and context snapshots, never live entities, worlds, registries or inventories.

The listener uses the existing `AsyncTaskManager.trySubmitIoWork` entry point. The generic `submitIoTask` deadline defaults to 20 seconds and would discard a valid 30-second backend reply. The transport owns this request's deadline; no separate AI thread pool is introduced in Minecraft.

Results return through `server.execute` and re-resolve the player by UUID. The callback checks the active server session and original dimension before delivery. Server shutdown invalidates callbacks, cancels outstanding HTTP exchanges and clears transient history. No Minecraft backend operation launches or manages Ollama. The separate bundled launcher owns its native runtime.

## Privacy and voice

Recent dialogue remains in memory and is bounded. It is not written as a raw conversation log. Explicit remember/recall/forget exchanges are local. Stored profile notes are included in requests only when `send_player_memory` is enabled. Addressed chat itself is still transmitted when the backend is enabled.

New configurations disable natural profile learning; existing explicit settings are preserved. Forget clears local stored profile data and recent conversation history. This does not delete records an independently administered backend or proxy may have kept.

The gateway records bounded request metadata and durable administration mutations, never conversation content. Legacy gateway message/response logging switches no longer write content. Existing bounded Minecraft conversation memory and explicit personal-note ownership remain unchanged. Reports will be explicit player submissions in a later phase. Keep request bodies and authorization headers out of proxy logs.

Optional Python speech, microphone capture, subtitles and cinematic voice retain their existing configuration and private-world restrictions. Neither Minecraft nor the gateway starts Python or downloads speech models. See [local voice](local-voice.md).

## Build and test

Use Java 21 and the checked-in Gradle wrapper. Only one Gradle, NeoForm, Minecraft development or data-generation process may run at a time.

From the repository root on PowerShell:

```powershell
.\gradlew.bat :compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :aiTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :build '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat -p aether-server build '-PcodexBuildDir=.codex-build' --no-parallel
```

Standalone server tests use mock Ollama endpoints. The service project has its own settings/build files so it can be moved into another repository and built with Gradle without Minecraft dependencies.

Normal outputs are `build/libs/wildernessodysseyapi-<version>.jar` and `aether-server/build/libs/Aether-Gateway.jar`. Isolated builds replace each project's `build` directory with `.codex-build`. Install the regular mod JAR, not the sources JAR.

For service installation, ports, systemd and Docker examples, see [deployment](../../deploy/README.md).

## Manual acceptance

1. Load a legacy config and confirm one migration warning, usable fallback, preserved data and no automatic Ollama startup.
2. Start Ollama and the separate gateway manually. Configure matching keys and obtain a model reply through Minecraft.
3. Confirm ordinary chat is not sent, named specialists route correctly, and lore/biome/meteor context remains accurate.
4. Stop the gateway, remove the selected model, saturate its queue and simulate a slow reply. Each case must preserve fallback and server responsiveness.
5. Send simultaneous addressed messages from different players on the provisioned Minecraft server. Confirm private replies and separate histories; spoofed server IDs must be rejected.
6. Stop or replace a Minecraft session during inference; confirm the response does not appear in the next session.
7. Verify profile sharing defaults off and remember/recall/forget controls still work.
8. Check real voice playback separately in a supported private world.
9. After separate production approval, repeat authenticated connection, wrong-scope, pause and failure checks through the HTTPS proxy.

Compilation, mocked protocol tests, real model behavior and live multiplayer/tick responsiveness are separate evidence. Record unrun checks as unverified.

For the recorded test results, package paths, complete file manifest and remaining checks, see [delivery and verification](aether-backend-verification.md).
