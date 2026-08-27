from __future__ import annotations

import asyncio
import secrets
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response, status
from pydantic import BaseModel, Field

from . import __version__
from .config import ServiceSettings
from .models import VoiceModels

SETTINGS = ServiceSettings.from_environment()
MODELS = VoiceModels(SETTINGS)
MAX_AUDIO_BYTES = 12 * 1024 * 1024
ALLOWED_EMOTIONS = {"normal", "concerned", "urgent", "damaged", "weak", "mysterious"}


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield
    await asyncio.to_thread(MODELS.close)


app = FastAPI(
    title="Aether Local Voice Service",
    version=__version__,
    docs_url=None,
    redoc_url=None,
    lifespan=lifespan,
)


class SpeakRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2_000)
    voice: str = Field(default="", max_length=80)
    speed: float = Field(default=1.0, ge=0.75, le=1.25)
    emotion: str = Field(default="normal")
    radio_effect: float = Field(default=0.0, ge=0.0, le=0.35)
    effects_enabled: bool = True


def require_token(authorization: str | None = Header(default=None)) -> None:
    if not SETTINGS.token:
        return
    expected = f"Bearer {SETTINGS.token}"
    if authorization is None or not secrets.compare_digest(authorization, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid service token")


@app.get("/v1/status", dependencies=[Depends(require_token)])
async def service_status() -> dict[str, object]:
    model_status = MODELS.status()
    return {
        "service": "ready",
        "version": __version__,
        "model_state": model_status.state,
        "microphone": "captured_by_minecraft",
        "speech_recognition_ready": model_status.stt_ready,
        "text_to_speech_ready": model_status.tts_ready,
        "device": model_status.device,
        "compute_type": model_status.compute_type,
        "voice_model": SETTINGS.default_voice,
        "downloads_allowed": SETTINGS.allow_model_downloads,
        "last_error": model_status.last_error,
        "latency_ms": {"stt": MODELS.last_stt_ms, "tts": MODELS.last_tts_ms},
    }


@app.post("/v1/models/load", dependencies=[Depends(require_token)])
async def load_models() -> dict[str, object]:
    model_status = await asyncio.to_thread(MODELS.load)
    if model_status.state != "ready":
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"state": model_status.state, "error": model_status.last_error},
        )
    return {
        "state": model_status.state,
        "device": model_status.device,
        "compute_type": model_status.compute_type,
    }


@app.post("/v1/transcribe", dependencies=[Depends(require_token)])
async def transcribe(request: Request) -> dict[str, object]:
    if not MODELS.status().stt_ready:
        raise HTTPException(status_code=503, detail="speech recognition model is not loaded; call /v1/models/load")
    if request.headers.get("content-type", "").split(";", 1)[0].strip().lower() != "audio/wav":
        raise HTTPException(status_code=415, detail="expected audio/wav")
    audio_chunks: list[bytes] = []
    audio_size = 0
    async for chunk in request.stream():
        audio_size += len(chunk)
        if audio_size > MAX_AUDIO_BYTES:
            raise HTTPException(status_code=413, detail="microphone audio is empty or too large")
        audio_chunks.append(chunk)
    audio = b"".join(audio_chunks)
    if not audio:
        raise HTTPException(status_code=413, detail="microphone audio is empty or too large")
    try:
        text = await asyncio.to_thread(MODELS.transcribe, audio)
    except Exception as exc:
        detail = MODELS.record_error(exc)
        raise HTTPException(status_code=422, detail=detail) from exc
    return {"text": text, "latency_ms": MODELS.last_stt_ms}


@app.post("/v1/speak", dependencies=[Depends(require_token)])
async def speak(request: SpeakRequest) -> Response:
    if not MODELS.status().tts_ready:
        raise HTTPException(status_code=503, detail="text-to-speech model is not loaded; call /v1/models/load")
    emotion = request.emotion.strip().lower()
    if emotion not in ALLOWED_EMOTIONS:
        emotion = "normal"
    try:
        wav = await asyncio.to_thread(
            MODELS.speak,
            request.text.strip(),
            request.voice.strip() or SETTINGS.default_voice,
            request.speed,
            emotion,
            request.radio_effect,
            request.effects_enabled,
        )
    except Exception as exc:
        detail = MODELS.record_error(exc)
        raise HTTPException(status_code=422, detail=detail) from exc
    return Response(
        content=wav,
        media_type="audio/wav",
        headers={"X-Aether-TTS-Milliseconds": f"{MODELS.last_tts_ms or 0.0:.1f}"},
    )


def main() -> None:
    import uvicorn

    uvicorn.run(app, host=SETTINGS.host, port=SETTINGS.port, access_log=False)


if __name__ == "__main__":
    main()
