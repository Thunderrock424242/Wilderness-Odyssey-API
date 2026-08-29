# A.E.T.H.E.R Ollama auto-start

A.E.T.H.E.R can start an already-installed Windows Ollama application when a private single-player world opens. This affects the local text intelligence only; the optional faster-whisper/Kokoro voice service remains a separately started, opt-in process.

## Configuration

The `settings` section of `config/ai_config.yaml` accepts:

```yaml
settings:
  provider: "ollama"
  endpoint: "http://127.0.0.1:11434"
  model: "llama3.2:latest"
  ollama_autostart: true
  ollama_startup_timeout_seconds: 20
  ollama_executable: ""
```

- `ollama_autostart` controls whether Minecraft may start an installed Ollama application after the loopback health check fails. Existing configs without this setting use the code default of `true`.
- `ollama_startup_timeout_seconds` is bounded to 5 through 60 seconds and applies only to the background readiness wait.
- `ollama_executable` may be left empty for trusted Windows discovery. An override must be an absolute, existing file named `ollama app.exe` or `ollama.exe`; it is never parsed as a shell command.

## Startup sequence

1. The private-single-player access policy must approve the integrated server. Dedicated servers and LAN-published worlds never request startup.
2. A background I/O worker sends a bounded `GET /api/tags` request to the validated loopback endpoint.
3. If Ollama is already available, no process is started.
4. Otherwise, Windows discovery checks the configured override, `%LOCALAPPDATA%/Programs/Ollama/ollama app.exe`, `%LOCALAPPDATA%/Programs/Ollama/ollama.exe`, and then bounded `PATH` entries.
5. `ollama app.exe` is launched directly when available. The CLI fallback is launched directly as `ollama.exe serve`.
6. The same worker polls the endpoint every 250 milliseconds until it becomes ready or reaches the configured timeout.
7. The existing A.E.T.H.E.R warm-up requests the exact configured model and keeps it loaded for the established keep-alive period.

Only one startup attempt can run at a time. Minecraft threads never wait for the executable, endpoint, or model.

## Installation and model ownership

The mod does not install Ollama and does not run `ollama pull`. Each player must install Ollama and download the configured model once. With the default configuration, the one-time command on Windows is:

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" pull llama3.2:latest
```

Automatic startup does not require Ollama to be on `PATH` when the standard Windows installation exists.

## Failure behavior

- A missing executable, rejected path, startup exception, interruption, or readiness timeout leaves the scripted A.E.T.H.E.R fallback available.
- A missing model is reported by the existing warm-up request; it is not silently downloaded.
- Non-loopback and HTTPS endpoints are rejected before process startup.
- Minecraft does not stop the installed Ollama tray application when the world or client closes.
- Disable automatic startup with `ollama_autostart: false`; an Ollama instance started by the player is still detected and used.
