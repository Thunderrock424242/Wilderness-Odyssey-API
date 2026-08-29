# A.E.T.H.E.R local voice

A.E.T.H.E.R voice is an optional, client-local input and presentation layer for the existing private single-player Ollama companion. It does not replace Ollama, create a second chatbot, or send microphone audio to the server or internet.

## Implemented flow

```text
Hold V in a private single-player world
  -> JavaSound records a bounded 16 kHz mono clip in memory
  -> POST /v1/transcribe to 127.0.0.1
  -> faster-whisper returns text
  -> Minecraft sends that text as ordinary player chat
  -> the existing Aether Ollama history, player profile, specialist router, lore context,
     factual verifier, and provider-outage fallback handle it
  -> the verified display response appears once in chat
  -> a client payload carries the matching spoken text and metadata
  -> POST /v1/speak to 127.0.0.1
  -> Kokoro returns an in-memory WAV
  -> JavaSound plays it and the client shows one temporary subtitle
```

The local voice service intentionally owns only heavy speech work. The established Java-to-Ollama chat path remains the conversation authority, so typed and spoken requests cannot develop separate memories, profiles, or personalities. Stable details shared through either input method are stored by the same bounded per-save, per-player profile owner.

## Uses beyond conversation

- Registered cryo cinematic cues play fixed bundled Kokoro WAVs through the same bounded JavaSound playback owner. Their server-approved cue IDs, localized text, measured subtitle duration, and cinematic timing remain authoritative. They do not need the service at runtime, and the LLM does not rewrite a cinematic line.
- The recovered-lore Codex exposes **Read Aloud** while voice and lore reading are enabled. Requiem reads the exact visible page spread; the LLM does not invent missing pages.
- A new player question, a new non-queued authored request, leaving the world, or disabling voice invalidates stale generated audio before it can begin playing.

These authored paths deliberately keep their text/subtitle behavior when speech generation fails. Voice failure never blocks a cinematic, hides lore, duplicates chat, or disables typed Aether conversation.

## Client settings

The settings live in the existing unified client config at:

```text
config/wildernessodysseyapi/wildernessodysseyapi-client.toml
```

The `[aether_voice]` category contains:

- `enabled`: master opt-in; defaults to `false`.
- `inputMode`: `TEXT` or `PUSH_TO_TALK`. `ALWAYS_LISTENING` is reserved but intentionally inactive.
- `serviceEndpoint`: defaults to `http://127.0.0.1:8765` and is rejected unless it is plain HTTP on a loopback host.
- `serviceToken`: optional bearer token shared with the service process.
- `voiceName`: Kokoro voice ID, default `af_bella`, selected for A.E.T.H.E.R's grounded, conversational caretaker delivery.
- `volume`: generated voice volume before Minecraft's Master and Voice sliders.
- `speechSpeed`: base speech speed before bounded emotion adjustment; fresh configs default to normal `1.0` delivery.
- `subtitles`, `radioProcessing`, `cinematicNarration`, and `loreReadAloud`. Radio processing defaults off so the natural voice is preserved.
- `requestTimeoutSeconds`: client request timeout; no Minecraft thread waits for it.

The push-to-talk key defaults to **V** and can be changed in Minecraft's normal Controls screen. An unbound **Aether Voice Status** key is also available there; bind it to request local readiness and latency diagnostics even before enabling speech.

Voice is hard-disabled on dedicated servers and as soon as an integrated world is opened to LAN. The same private-single-player authority used by Aether chat owns this boundary.

## Install and start the speech service on Windows

Use Python 3.10, 3.11, or 3.12; Python 3.11 is the recommended baseline.

```powershell
cd tools\aether_voice_service
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -e .
```

Install eSpeak NG from its official Windows release before loading Kokoro, then open a new terminal so the native library can be found.

The service does not silently download models. For the first deliberate download and model load:

```powershell
$env:AETHER_VOICE_ALLOW_MODEL_DOWNLOADS='true'
.\start.ps1
```

In a second terminal:

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:8765/v1/models/load
```

The defaults are faster-whisper `small.en` and Kokoro `af_bella`. The model-load request also preloads that voice. Both Hugging Face/Kokoro files and the separate Whisper cache are directed outside the repository under `%USERPROFILE%\.cache\aether-voice` unless `AETHER_VOICE_MODEL_DIR` or an existing `HF_HOME` overrides the relevant cache location. For another voice, set `AETHER_KOKORO_VOICE` during this deliberate download step and use the same client `voiceName`. After the initial download, stop the service, remove `AETHER_VOICE_ALLOW_MODEL_DOWNLOADS`, and restart in the default cache-only mode.

Normal startup is:

```powershell
cd tools\aether_voice_service
.\start.ps1
```

Load cached models explicitly after startup:

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:8765/v1/models/load
Invoke-RestMethod http://127.0.0.1:8765/v1/status
```

The service selects CUDA/float16 for faster-whisper when CTranslate2 reports a usable NVIDIA device; otherwise it selects CPU/int8. A failed CUDA model initialization is retried on CPU/int8. Kokoro's upstream runtime owns automatic device selection, and a runtime failure during that automatic accelerator setup is retried explicitly on CPU. The status response is the authority for the selected STT device and records any fallback note.

## Local security and privacy

- The Python service refuses non-loopback bind addresses. The Minecraft transport independently refuses non-loopback or HTTPS-configured service roots.
- Microphone and generated WAV data live in bounded memory buffers. No recording or generated speech file is written permanently.
- Push-to-talk is capped at 30 seconds, and clips shorter than 200 ms are ignored.
- Requests, responses, display strings, speaker names, emotion values, effect strengths, and audio sizes are bounded.
- The Python voice service is never launched automatically, and no speech model is downloaded without the explicit environment opt-in plus model-load request. The separate Ollama text runtime may start an already-installed Windows application as documented in `ollama-autostart.md`.
- Voice transcripts can contribute the same bounded personal preferences as typed chat, but raw microphone audio is never used as profile data. Say `what do you remember about me?` to inspect the profile or `forget what you know about me` to remove it.
- A loopback listener can still be called by another local process. Set `AETHER_VOICE_TOKEN` and the matching client `serviceToken` when local process isolation is not enough. Never commit that token.

## Failure behavior

Unavailable models, service timeouts, a denied microphone, malformed structured output, generation errors, and playback errors are handled as optional voice failures. Warnings are rate-limited. Normal Aether text remains visible and usable. Structured model output falls back only to a sanitized spoken form of the verified display response; it does not switch to a second voice chatbot.

## Diagnostics

`GET /v1/status` and the in-game **Aether Voice Status** binding report:

- microphone line support;
- model load state;
- STT and TTS readiness;
- faster-whisper device and configured voice;
- most recent STT/TTS request latency;
- the most recent bounded service error.

Ollama remains independently observable through the existing Aether chat path and logs. LLM first-token timing is not reported yet because the reliable initial implementation uses non-streaming Ollama responses.

## Intentionally deferred

- Always-listening/wake-word capture.
- Ollama token streaming, sentence boundary detection, and streaming TTS audio.
- A dedicated in-game settings screen; the feature uses the established client config and Controls UI.
- Per-character voice presets beyond the shared A.E.T.H.E.R caretaker profile.
- A second TTS implementation. Minecraft is insulated by the local `/v1/speak` contract, so Piper or another local backend can be added behind that boundary without changing Aether chat.

Streaming should operate on full sentences or natural phrases and retain the existing generation/session checks. Tiny token fragments must never be sent directly to TTS.

## Developer checks

The Java tests cover structured/legacy response parsing, speech sanitization, bounded metadata, voice default-off policy, loopback-only transport, configuration ownership, microphone WAV framing, queue order, and stale-session cancellation. The service includes a dependency-light configuration test for loopback binding and opt-in downloads.

Live end-to-end verification still requires installed speech dependencies, downloaded models, microphone/audio hardware, a running service, Ollama, and a fresh Minecraft client.

## Distribution notes

The mod does not bundle Python, model weights, CUDA, cuDNN, eSpeak NG, or voice datasets. Review and preserve the licenses/notices for every component and model before redistributing them. The service-specific README contains the selected dependency notes and exact environment controls.
