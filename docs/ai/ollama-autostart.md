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
- `ollama_startup_timeout_seconds` is bounded to 5 through 60 seconds and applies to the complete background readiness wait, including an automatic fallback attempt.
- `ollama_executable` may be left empty for trusted Windows discovery. An override must be an absolute, existing file named `ollama app.exe` or `ollama.exe`; it is never parsed as a shell command. A valid override is an explicit choice and does not fall back to the other executable.

## Startup sequence

1. The private-single-player access policy must approve the integrated server. Dedicated servers and LAN-published worlds never request startup.
2. A background I/O worker sends a bounded `GET /api/tags` request to the validated loopback endpoint.
3. If Ollama is already available, no process is started.
4. Otherwise, Windows discovery checks the configured override and the standard `%LOCALAPPDATA%/Programs/Ollama` installation, followed by bounded `PATH` entries. Automatically discovered `ollama.exe` candidates are ordered before `ollama app.exe` candidates.
5. Automatic discovery launches the background CLI as `ollama.exe serve` first. If it cannot be launched or does not make the endpoint ready within its portion of the bounded wait, `ollama app.exe` is launched as the fallback.
6. The same worker shares the configured polling budget across both automatic attempts, so falling back does not double the maximum readiness wait.
7. The existing A.E.T.H.E.R warm-up requests the exact configured model and keeps it loaded for the established keep-alive period.

Only one startup attempt can run at a time. Minecraft threads never wait for the executable, endpoint, or model.

## Installation and model ownership

The mod does not install Ollama and does not run `ollama pull`. Each player must install Ollama and download the configured model once. With the default configuration, the one-time command on Windows is:

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" pull llama3.2:latest
```

Automatic startup does not require Ollama to be on `PATH` when the standard Windows installation exists.

## Failure behavior

- If both automatically discovered executables are missing, cannot be launched, or exhaust the shared readiness wait, the scripted A.E.T.H.E.R fallback remains available.
- A missing model is reported by the existing warm-up request; it is not silently downloaded.
- Non-loopback and HTTPS endpoints are rejected before process startup.
- Minecraft does not stop the installed Ollama tray application when the world or client closes.
- Disable automatic startup with `ollama_autostart: false`; an Ollama instance started by the player is still detected and used.
