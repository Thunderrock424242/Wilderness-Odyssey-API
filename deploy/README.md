# Deploying Aether

For the self-contained JAR that includes Ollama and the approved model, start with [the bundled server guide](../docs/ai/aether-bundled-server.md). The instructions below cover the smaller gateway-only distribution with externally managed Ollama.

The Minecraft server calls Aether on port 8085; Aether calls privately hosted Ollama, normally on port 11434. On multiplayer servers, joining clients receive replies through Minecraft networking. A single-player installation sends requests from its integrated server to the gateway; it never needs direct access to Ollama.

## Build and configure

Use JDK 21. From the repository root:
```powershell
.\gradlew.bat -p aether-server build '-PcodexBuildDir=.codex-build' --no-parallel
```

The tested executable is `aether-server/.codex-build/libs/Aether-Gateway.jar`. Without isolation, it is `aether-server/build/libs/Aether-Gateway.jar`. This build runs tests and checks that the JAR contains no Minecraft or NeoForge classes/dependencies.

Copy `aether-server.example.yml` to an operator-controlled `aether-server.yml`. Configure its listener and Ollama endpoint. The gateway is public: readiness and generation require no access key. Old `security.api_keys` and `AETHER_API_KEYS` values are ignored. Minecraft likewise ignores its former key settings. Update both applications when upgrading from a keyed release; existing configuration files are preserved.

Start the service:
```sh
java -jar Aether-Gateway.jar --external --config aether-server.yml
```

In `--external` mode without `--config`, bundled gateway defaults are used. Health, readiness and generation are available without credentials. Stop with Ctrl+C or the service manager; accepted HTTP exchanges and pending jobs close. Ollama is managed independently.

Install or create the configured Ollama model yourself. `aether-custom:8b` is the default model identifier, not a bundled/downloaded model. It must exist in your Ollama installation. The gateway never installs models or launches Ollama.

The adapter uses [Ollama chat](https://docs.ollama.com/api/chat) for non-streaming structured replies and a second verification pass. [Ollama model listing](https://docs.ollama.com/api/tags) identifies whether the configured model is installed. Readiness means that Ollama is reachable and lists that model; it is not proof of GPU capacity, a warm model, or response quality.

## Local development

1. Start Ollama manually.
2. Start the Aether JAR with its model configured.
3. In Minecraft's `config/ai_config.yaml`, set `ai_backend.remote.base_url: "http://127.0.0.1:8085"`, `enabled: true`, and `mode: remote`.
4. Address Aether in chat. Stop the gateway and confirm recovered-intent fallback.

See [the complete nested Minecraft configuration](../docs/ai/aether-backend.md). A server restart applies configuration changes.

## Linux and HTTPS

The default bind address is 127.0.0.1. Put a reverse proxy in front of port 8085 and terminate HTTPS there. Expose only the HTTPS gateway to the Minecraft host. Keep Ollama on loopback or an isolated private network; do not publish port 11434.

The gateway does not use an Authorization header. Disable request-body logging in the proxy. Set a 64 KiB body limit and bounded header/body/connection timeouts. Set the proxy response timeout to cover the gateway deadline. Minecraft's own deadline may be shorter than the gateway's maximum; increase it only after measuring actual inference latency.

The example [systemd unit](systemd/aether-ai.service) expects:
- JAR: /opt/aether/Aether-Gateway.jar
- Config: /etc/aether/aether-server.yml
- A dedicated existing service account named aether

Review paths and access before installing. Use the host's usual service manager controls to start, stop and restart the unit. This repository does not install or modify host services automatically.

## Health, load and privacy

`GET /health` is public liveness, with cached Ollama/model status and active/queued generation counts. It stays HTTP 200 when the model is unavailable. `GET /ready` is public and returns 200 only when the configured model is listed; otherwise 503. Dependency observations refresh every ten seconds.

`POST /v1/aether/generate` accepts validated JSON without credentials. Body size, nesting, context/history limits, deadlines, rates and queue capacity are bounded. Generation queue exhaustion returns 503 OVERLOADED; the shared service rate limit returns 429 RATE_LIMITED. All callers use one `limits.requests_per_minute` budget, so changing player IDs, server IDs, or headers cannot create another allowance. The old `requests_per_minute_per_server` setting is accepted only as a fallback for this shared limit.

The executable also caps open HTTP connections at 128, idle connections at 32, request headers at 32 fields and 16 KiB, and incomplete requests at `server.request_timeout_seconds`. Response connections are bounded by the inference timeout plus two seconds. These [OpenJDK 21 listener settings](https://github.com/openjdk/jdk21u/blob/master/src/jdk.httpserver/share/classes/sun/net/httpserver/ServerConfig.java) are installed before startup in the standalone process; embedding the service in tests does not change the host JVM's HTTP settings.
The default is two concurrent generations, twenty queued jobs, sixty requests across the service per minute. Queue time, draft generation and factual verification share one 30-second deadline. Raising concurrency can worsen latency and GPU memory use; tune using measured model behavior.

Logging flags default false. Metadata can be enabled separately from player messages and responses. Opt-in content logs are length-bounded and remove control characters. The service does not persist conversations or maintain shared cross-server chat history. Secure logs and configure retention externally.

## Docker example

Build the JAR first. From the repository root:
```sh
docker build -f deploy/docker/Dockerfile --build-arg AETHER_JAR=.codex-build/libs/Aether-Gateway.jar aether-server
```

The Dockerfile defaults to `build/libs/Aether-Gateway.jar` for a normal Gradle build. The service runs as a non-root user.

The [Compose example](docker/docker-compose.yml) runs separate gateway and Ollama containers. No credential environment variables are required. Its configuration binds the gateway inside its container and connects to Ollama by its private service name. The gateway's host port is bound to loopback for a local HTTPS proxy; Ollama has no published host port.

Create/install the model explicitly in the Ollama container. GPU passthrough, model files, production image pinning and reverse-proxy configuration remain deployment-specific. Docker/systemd examples have not been exercised by automated Java tests.

No new monitoring, account registration, or access-code system is included.

## Validation

Mocked tests cover the executable JAR, health/readiness, generation and verification, model outage, key-free requests, ignored legacy key settings, malformed/oversized input, timeouts, queue overload, multiple players and shared request limits. Live model quality, Minecraft gameplay/multiplayer tick responsiveness and remote production hosting are separate manual checks.
