# Deploying the Aether gateway

The Minecraft server calls Aether on port 8085; Aether calls privately hosted Ollama, normally on port 11434. Minecraft clients do not need access to either port.

## Build and configure

Use JDK 21. From the repository root:
```powershell
.\gradlew.bat -p aether-server build '-PcodexBuildDir=.codex-build' --no-parallel
```

The tested executable is `aether-server/.codex-build/libs/Aether-AI-Server.jar`. Without isolation, it is `aether-server/build/libs/Aether-AI-Server.jar`. This build runs tests and checks that the JAR contains no Minecraft or NeoForge classes/dependencies.

Copy `aether-server.example.yml` to an operator-controlled `aether-server.yml`. Set `AETHER_API_KEYS` in the service environment to one or more comma-separated credentials. Set `AETHER_BACKEND_API_KEY` in the Minecraft server environment to one accepted credential. Prefer a separate cryptographically random secret of at least 32 characters for each Minecraft server. Keys in YAML are also supported; keep active files out of Git.

Start the service:
```sh
java -jar Aether-AI-Server.jar --config aether-server.yml
```

Without `--config`, bundled defaults are used. With no configured keys, generation and readiness fail closed while public health remains available. Stop with Ctrl+C or the service manager; accepted HTTP exchanges and pending jobs close. Ollama is managed independently.

Install or create the configured Ollama model yourself. `aether-custom:8b` is the default model identifier, not a bundled/downloaded model. It must exist in your Ollama installation. The gateway never installs models or launches Ollama.

The adapter uses [Ollama chat](https://docs.ollama.com/api/chat) for non-streaming structured replies and a second verification pass. [Ollama model listing](https://docs.ollama.com/api/tags) identifies whether the configured model is installed. Readiness means that Ollama is reachable and lists that model; it is not proof of GPU capacity, a warm model, or response quality.

## Local development

1. Start Ollama manually.
2. Start the Aether JAR with its key and model configured.
3. In Minecraft's `config/ai_config.yaml`, set `ai_backend.remote.base_url: "http://127.0.0.1:8085"`, `enabled: true`, and `mode: remote`.
4. Supply the matching key through `AETHER_BACKEND_API_KEY`.
5. Address Aether in chat. Stop the gateway and confirm recovered-intent fallback.

See [the complete nested Minecraft configuration](../docs/ai/aether-backend.md). A server restart applies configuration/environment changes.

## Linux and HTTPS

The default bind address is 127.0.0.1. Put a reverse proxy in front of port 8085 and terminate HTTPS there. Expose only the HTTPS gateway to the Minecraft host. Keep Ollama on loopback or an isolated private network; do not publish port 11434.

Preserve the Authorization header and disable logging of it and request bodies in the proxy. Set a 64 KiB body limit and bounded header/body/connection timeouts. Set the proxy response timeout to cover the gateway deadline. Minecraft's own deadline may be shorter than the gateway's maximum; increase it only after measuring actual inference latency.

The example [systemd unit](systemd/aether-ai.service) expects:
- JAR: /opt/aether/Aether-AI-Server.jar
- Config: /etc/aether/aether-server.yml
- Environment: /etc/aether/aether.env, containing AETHER_API_KEYS
- A dedicated existing service account named aether

Review paths and access before installing. Use the host's usual service manager controls to start, stop and restart the unit. This repository does not install or modify host services automatically.

## Health, load and privacy

`GET /health` is public liveness, with cached Ollama/model status and active/queued generation counts. It stays HTTP 200 when the model is unavailable. `GET /ready` requires bearer authentication and returns 200 only when the configured model is listed; otherwise 503. Dependency observations refresh every ten seconds.

`POST /v1/aether/generate` requires bearer authentication and validated JSON. Body size, nesting, credentials, context/history limits, deadlines, rates and queue capacity are bounded. Generation queue exhaustion returns 503 OVERLOADED; credential rate exhaustion returns 429 RATE_LIMITED. Rate budgets are keyed to the authenticated credential, so changing a request's serverId cannot bypass them.

The default is two concurrent generations, twenty queued jobs, sixty requests per credential per minute. Queue time, draft generation and factual verification share one 30-second deadline. Raising concurrency can worsen latency and GPU memory use; tune using measured model behavior.

Logging flags default false. Metadata can be enabled separately from player messages and responses. Opt-in content logs redact configured credentials and control characters. The service does not persist conversations or maintain shared cross-server chat history. Secure logs and configure retention externally.

## Docker example

Build the JAR first. From the repository root:
```sh
docker build -f deploy/docker/Dockerfile --build-arg AETHER_JAR=.codex-build/libs/Aether-AI-Server.jar aether-server
```

The Dockerfile defaults to `build/libs/Aether-AI-Server.jar` for a normal Gradle build. The service runs as a non-root user.

The [Compose example](docker/docker-compose.yml) runs separate gateway and Ollama containers. Set AETHER_API_KEYS before starting it. Its configuration binds the gateway inside its container and connects to Ollama by its private service name. The gateway's host port is bound to loopback for a local HTTPS proxy; Ollama has no published host port.

Create/install the model explicitly in the Ollama container. GPU passthrough, model files, production image pinning and reverse-proxy configuration remain deployment-specific. Docker/systemd examples have not been exercised by automated Java tests.

## Validation

Mocked tests cover the executable JAR, health/readiness, generation and verification, model outage, authentication, malformed/oversized input, timeouts, queue overload, multiple players and multiple credential identities. Live model quality, Minecraft gameplay/multiplayer tick responsiveness and remote production hosting are separate manual checks.
