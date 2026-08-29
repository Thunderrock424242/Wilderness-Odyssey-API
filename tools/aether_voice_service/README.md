# Aether local voice service

This optional service keeps faster-whisper speech recognition and Kokoro text-to-speech outside the Minecraft JVM. It binds only to a numeric loopback address and never starts automatically from the mod. Typed Aether chat continues to work when the service is stopped.

## Windows setup

Use Python 3.10, 3.11, or 3.12. Python 3.11 is the recommended Windows baseline.

```powershell
cd tools\aether_voice_service
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -e .
```

Kokoro's English pipeline uses eSpeak NG for fallback phonemization. Install the current Windows x64 MSI from the [official eSpeak NG releases](https://github.com/espeak-ng/espeak-ng/releases), then open a new terminal so the library is discoverable.

Models are not silently downloaded. The service defaults to offline/cache-only loading. For the first deliberate model download:

```powershell
$env:AETHER_VOICE_ALLOW_MODEL_DOWNLOADS='true'
.\start.ps1
```

In a second terminal, explicitly start model loading:

```powershell
Invoke-RestMethod -Method Post http://127.0.0.1:8765/v1/models/load
```

The default faster-whisper model is `small.en`; the default Kokoro voice is the grounded `af_bella` caretaker profile. `/v1/models/load` also preloads that voice so the first later speech request does not trigger a hidden download. Model caches are stored outside the repository at `%USERPROFILE%\.cache\aether-voice` unless `AETHER_VOICE_MODEL_DIR` or an existing `HF_HOME` overrides the relevant cache location. To use a different voice offline, set `AETHER_KOKORO_VOICE` to the same ID during the deliberate download/load step and use that ID in the Minecraft client config.

After the initial download, stop the service, remove `AETHER_VOICE_ALLOW_MODEL_DOWNLOADS`, and start it again in cache-only mode.

## Authored cryo narration

The cryo awakening packages neural voice assets, so normal players do not need to run this service. After the virtual environment is installed, the first deliberate authoring run downloads Kokoro into the external model cache and generates the 20 English clips:

```powershell
.\generate_cryo_voice.ps1 -AllowModelDownloads
```

Later regenerations are offline by default:

```powershell
.\generate_cryo_voice.ps1
```

The generator uses `af_bella` at normal 1.0 speed, reads the translation file as the dialogue source of truth, applies no radio or corruption filter, and rewrites the manifest with measured durations. Java cue durations and tests must be updated when generated durations change. Only the WAV files and manifest are shipped; `.venv`, model weights, and the external cache remain local authoring dependencies.

## Optional local token

Loopback prevents network clients from reaching the service, but another local process could otherwise call it. To require a shared bearer token:

```powershell
$env:AETHER_VOICE_TOKEN='choose-a-long-random-local-token'
.\start.ps1
```

Put the same value in `aether_voice.serviceToken` inside `config/wildernessodysseyapi/wildernessodysseyapi-client.toml`. Do not commit the token.

## GPU and CPU behavior

The service asks CTranslate2 whether a CUDA device is usable. It selects CUDA/float16 when available and CPU/int8 otherwise. If CUDA is visible but model initialization fails, it retries faster-whisper on CPU/int8 and reports the fallback in status. Current faster-whisper GPU releases require compatible CUDA 12 cuBLAS and cuDNN 9 libraries. CPU requires no CUDA setup.

Kokoro is loaded once and reused. The upstream package controls its automatic PyTorch device selection; if automatic accelerator initialization raises a runtime error, the service retries Kokoro explicitly on CPU. `/v1/status` reports the CTranslate2 STT device and any bounded fallback note.

## Run and diagnose

```powershell
.\start.ps1
Invoke-RestMethod http://127.0.0.1:8765/v1/status
```

Status reports model loading state, STT/TTS readiness, selected CTranslate2 device, last safe error, and most recent STT/TTS latency. The Minecraft client never waits on these requests from its render thread.

The service endpoints are:

- `GET /v1/status`
- `POST /v1/models/load`
- `POST /v1/transcribe` with a bounded 16 kHz mono WAV request body
- `POST /v1/speak` with structured JSON and an in-memory WAV response

No microphone recording or generated speech file is retained by the service.

## Licensing and distribution

- [faster-whisper](https://github.com/SYSTRAN/faster-whisper) is MIT licensed. Whisper model files have their own upstream model terms and should be reviewed before redistribution.
- The upstream [Kokoro inference package and Kokoro-82M weights](https://github.com/hexgrad/kokoro) are Apache-2.0 licensed. Keep required notices if weights or package code are redistributed.
- [eSpeak NG](https://github.com/espeak-ng/espeak-ng) is GPL-3.0-or-later for the main synthesizer, with separately identified component licenses upstream. This project does not bundle it; users install it separately. Review distribution obligations before shipping it inside a modpack installer.
- This repository does not bundle Python, CUDA, cuDNN, model weights, or voice datasets.
