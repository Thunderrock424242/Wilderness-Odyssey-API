# Aether self-contained server

The platform-specific JAR contains the Java gateway, native Ollama 0.17.7, bundled `llama3.1:8b` weights, and Aether's personality, lore, and specialist prompts. Model startup is now gated by explicit hosting and capacity verification. The existing 8B model is not approved for the shared Kinetic server with a 500% CPU limit, 8 GB RAM and unknown GPU. Built with Llama; original model and runtime licenses are included.

This is a standalone Java 21 server application. Start it as the server application's JAR in a compatible hosting panel, or alongside Minecraft as a separate service. It is not a Bukkit plugin or NeoForge mod to place in `plugins/` or `mods/`. If Minecraft itself also runs on that machine, the gateway needs its own available port.

## Start the complete bundle

Choose the bundle matching the host, not the computer uploading it:

- Linux x86-64: `Aether-AI-Server-linux-amd64.jar`
- Windows x86-64: `Aether-AI-Server-windows-amd64.jar`

```sh
java -jar Aether-AI-Server-linux-amd64.jar
```

For Windows, substitute the Windows filename. A panel that requires `server.jar` can use a renamed copy of the matching bundle. Use Java 21 or newer, and a startup command that actually runs this application. Java heap settings do not allocate Ollama's native/GPU memory.

On first launch the application:

1. Checks the operating system and locks its data directory against a second launcher.
2. Verifies and extracts the native runtime and model, streaming large files without loading them into Java's heap.
3. Creates locked, loopback-only `aether/aether-server.yml` with separate credential references and disabled activation. It uses `SERVER_PORT`, then `PORT`, then port 8085 for the gateway. Later launches preserve the configuration.
4. Stops before native startup unless the configured model has explicit hosting/capacity approval and inference enablement. Once separately approved, starts its own Ollama process on loopback port 11435 with its own model directory and user-home directory.
5. Creates `aether-custom:8b` from the included base model, preloads it with a bounded three-minute allowance, and then starts the gateway. Aether's complete personality and lore are applied by the gateway for each request. Display and voice share one verified answer; copied format placeholders fail validation.

No model download or system-wide Ollama installation is required at startup. First extraction and later integrity checks can take time. Keep the JAR: the launcher uses it to repair a damaged bundled file on a later start. Normal shutdown stops its own Ollama process and descendants. Type `stop` in the application console or use a service manager's graceful stop. A forced process kill or machine power loss cannot run Java cleanup; check for a leftover owned Ollama process if its private port remains occupied.

Optional commands:

```sh
java -jar Aether-AI-Server-linux-amd64.jar --data-dir /srv/aether-data
java -jar Aether-AI-Server-linux-amd64.jar --setup-only
java -jar Aether-AI-Server-linux-amd64.jar --help
```

`--setup-only` extracts files and creates settings without starting services. Keep the data directory on a writable local filesystem. The launcher rejects symbolic links/reparse points in managed paths, preserves existing settings, repairs damaged bundled assets, and removes only its own abandoned extraction files on retry.

## Hosting requirements

The provider must permit custom Java applications to launch native child processes. A provider restricted to Minecraft plugins or approved server types may not permit this. The Linux runtime also needs compatible system libraries; GPU acceleration needs drivers supplied by the host. The JAR does not install drivers, change firewalls, configure DNS, or create HTTPS certificates.

Allow space for both the large JAR and its extracted contents, plus logs and working room. The Windows payload alone is about 10.4 GB; reserve at least 30 GB for that distribution. Linux library aliases can increase its size; check the delivered artifact's size and allow at least twice that amount plus working room. The 8B model also needs substantial native RAM and/or GPU memory beyond Minecraft and the Java process. Measure response time on the actual hosting plan before admitting players.

The gateway binds only to loopback. Old `0.0.0.0` settings are rejected and require an explicit configuration migration. Keep Ollama private. A trusted local HTTPS connector must reach the gateway without exposing its origin port. The Kinetic backend supplies administration credentials and a Cloudflare Access assertion; the origin independently verifies both. A Minecraft game port does not itself provide HTTPS. Hosting support for this arrangement must be verified before activation.

The gateway defaults to a 30-second generation deadline, covering both drafting and factual verification. A cold model on a busy or small machine can exceed it. `ollama.timeout_seconds` supports up to 120 seconds for diagnosis, while Minecraft's transport supports at most 60 seconds. Increasing only the gateway timeout does not make Minecraft wait longer. Managed startup preloads the model before opening the gateway. The existing one-hour retention also applies to normal model calls; after a longer idle period, a request may load it again. Readiness then remains an availability check, not a latency guarantee. Preloading follows the [official Ollama API guidance](https://docs.ollama.com/faq#how-can-i-preload-a-model-into-ollama-to-get-faster-response-times).

## Connect a distant single-player world

The player's integrated Minecraft server uses the same remote gateway path as a multiplayer server. The player does not need Ollama locally and does not have to join the host's Minecraft world.

1. Obtain separate host/capacity and production activation approval, then provision a trusted integrated server with its own gateway instance/server identity.
2. Configure `ai_backend`, `mode: remote`, and the HTTPS `remote.base_url`. Supply its inference-only credential through the integrated server process environment. Never put the official dedicated server's credential in a public modpack or distribute it to players. Public enrollment is outside Phase 1.
3. Restart Minecraft/the world server after changing settings, then address Aether in chat.

Each single-player installation is an API client with its own settings. There is no automatic address discovery. For multiplayer, only the Minecraft server operator supplies these settings; joining players receive replies through the existing mod networking. Conversation histories remain separated by game session and player.

Old `security.api_keys`, `remote.api_key`, `AETHER_API_KEYS`, and `AETHER_BACKEND_API_KEY` values are ignored; existing configuration files are preserved. Migrate both applications to the protected protocol and provision separate inference, monitoring and administration secrets. The shared inference rate budget, bounded queues, deadlines and request sizes remain. Read-only monitoring and durable pause/resume are documented in [Phase 1](protected-gateway-phase-1.md).

See [the complete Minecraft configuration](aether-backend.md) and [gateway administration](../../deploy/README.md).

## Regenerate the distribution

The regular standalone build produces the smaller `Aether-Gateway.jar` for an externally managed Ollama service. Its explicit command is `java -jar Aether-Gateway.jar --external --config aether-server.yml`. It does not contain model weights. The old `--config` invocation remains compatible.

To build a complete Windows bundle from an installed Ollama runtime and the approved locally installed base model, use the repository wrapper and JDK 21:

```powershell
.\gradlew.bat -p aether-server build '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat -p aether-server bundleJar '-PcodexBuildDir=.codex-build' '-PaetherBundlePlatform=windows-amd64' "-PaetherRuntimeDirectory=$env:LOCALAPPDATA\Programs\Ollama" "-PaetherModelDirectory=$env:USERPROFILE\.ollama\models" --no-parallel
```

Outputs are under `aether-server/.codex-build/libs/`. A normal non-isolated build uses `aether-server/build/libs/`.

For Linux, supply the extracted official Ollama 0.17.7 Linux archive directory, containing `bin/ollama` and `lib/ollama`, with `-PaetherBundlePlatform=linux-amd64`. Run the same Gradle task with the matching runtime and model paths. Packaging streams and hashes the inputs, checks the model's original blob digests, and includes only the selected model's manifest and referenced blobs. It does not download payloads during an ordinary build.

The verified Linux amd64 archive used for this delivery is [Ollama 0.17.7](https://github.com/ollama/ollama/releases/tag/v0.17.7), SHA-256 `51ca1e0e9f66fa2493ced77debe8d9b9336ffc06271e6dcdde8b9f42b6c80a1d`. Windows packaging uses the installed 0.17.7 standalone runtime. When staging Linux on Windows, internal library symlinks are represented by identical regular files; preserve all aliases. Changing the runtime version requires updating the pinned manifest version and validating the new runtime.

Java tests use tiny fixtures and mock HTTP/process boundaries. Actual model inference, platform-specific native execution, remote HTTPS, Minecraft chat, multiplayer isolation, and gameplay performance are separate checks. Consult the [recorded delivery evidence](aether-bundle-verification.md) for checksums and what has actually run.
