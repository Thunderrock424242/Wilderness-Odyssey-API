# Ollama auto-start migration

Minecraft no longer starts Ollama or any local model executable.

The old `settings.provider: ollama`, `ollama_autostart`, `ollama_executable` and `local_model` settings are recognized as legacy configuration. They cannot reactivate runtime management. Existing files are preserved; without the new `ai_backend` section Aether uses deterministic fallback.

The [self-contained server JAR](aether-bundled-server.md) can instead manage its own bundled runtime and model outside Minecraft. For local development with externally managed Ollama:

1. Start your installed Ollama service manually and install/configure the desired model yourself.
2. Start the separate Java 21 service: `java -jar Aether-Gateway.jar --config aether-server.yml`.
3. Configure Minecraft to use `http://127.0.0.1:8085` without an access key.

For remote hosting, configure an HTTPS gateway URL instead. Keep Ollama private to the AI host.

See [the configuration and migration guide](aether-backend.md) and [deployment resources](../../deploy/README.md). Optional Python voice remains manually started and opt-in.
