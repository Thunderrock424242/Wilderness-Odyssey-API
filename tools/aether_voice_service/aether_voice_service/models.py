from __future__ import annotations

import io
import os
import threading
import time
from dataclasses import dataclass
from typing import Any

from .config import ServiceSettings
from .effects import adjusted_speed, apply_voice_effects


@dataclass(frozen=True, slots=True)
class ModelStatus:
    state: str
    stt_ready: bool
    tts_ready: bool
    device: str
    compute_type: str
    last_error: str


class VoiceModels:
    """Lazily owns one reusable STT model and one reusable TTS pipeline."""

    def __init__(self, settings: ServiceSettings) -> None:
        self._settings = settings
        self._lock = threading.Lock()
        self._stt_lock = threading.Lock()
        self._tts_lock = threading.Lock()
        self._whisper: Any = None
        self._kokoro: Any = None
        self._state = "not_loaded"
        self._device = "unknown"
        self._compute_type = "unknown"
        self._last_error = ""
        self.last_stt_ms: float | None = None
        self.last_tts_ms: float | None = None

    def status(self) -> ModelStatus:
        return ModelStatus(
            state=self._state,
            stt_ready=self._whisper is not None,
            tts_ready=self._kokoro is not None,
            device=self._device,
            compute_type=self._compute_type,
            last_error=self._last_error,
        )

    def record_error(self, error: Exception) -> str:
        message = f"{type(error).__name__}: {error}"[:500]
        self._last_error = message
        return message

    def load(self) -> ModelStatus:
        with self._lock:
            if self._whisper is not None and self._kokoro is not None:
                return self.status()
            self._last_error = ""
            try:
                self._settings.model_directory.mkdir(parents=True, exist_ok=True)
                os.environ.setdefault(
                    "HF_HOME",
                    str(self._settings.model_directory / "huggingface"),
                )
                if not self._settings.allow_model_downloads:
                    os.environ.setdefault("HF_HUB_OFFLINE", "1")

                self._state = "loading_speech_recognition"
                from faster_whisper import WhisperModel

                self._device, self._compute_type = self._detect_whisper_device()
                try:
                    self._whisper = self._load_whisper(WhisperModel)
                except Exception as accelerator_error:
                    if self._device != "cuda":
                        raise
                    self._device, self._compute_type = "cpu", "int8"
                    self._last_error = self._fallback_note("faster-whisper", accelerator_error)
                    self._whisper = self._load_whisper(WhisperModel)

                self._state = "loading_text_to_speech"
                from kokoro import KPipeline

                try:
                    self._kokoro = KPipeline(lang_code=self._settings.kokoro_language)
                except RuntimeError as accelerator_error:
                    self._last_error = self._fallback_note("Kokoro", accelerator_error)
                    self._kokoro = KPipeline(
                        lang_code=self._settings.kokoro_language,
                        device="cpu",
                    )
                self._kokoro.load_voice(self._settings.default_voice)
                self._state = "ready"
            except Exception as exc:
                self._last_error = f"{type(exc).__name__}: {exc}"[:500]
                self._state = "failed"
                self._whisper = None
                self._kokoro = None
            return self.status()

    def transcribe(self, wav_bytes: bytes) -> str:
        if self._whisper is None:
            raise RuntimeError("speech recognition model is not loaded")
        import numpy as np
        import soundfile as sf

        with self._stt_lock:
            started = time.perf_counter()
            audio, sample_rate = sf.read(io.BytesIO(wav_bytes), dtype="float32", always_2d=False)
            samples = np.asarray(audio, dtype=np.float32)
            if samples.ndim > 1:
                samples = samples.mean(axis=1)
            if sample_rate != 16_000:
                raise ValueError("microphone WAV must use a 16000 Hz sample rate")
            segments, _ = self._whisper.transcribe(
                samples,
                language=self._settings.whisper_language or None,
                beam_size=1,
                vad_filter=True,
                condition_on_previous_text=False,
            )
            text = " ".join(segment.text.strip() for segment in segments if segment.text.strip()).strip()
            self.last_stt_ms = (time.perf_counter() - started) * 1_000.0
            return text[:2_000]

    def speak(
        self,
        text: str,
        voice: str,
        base_speed: float,
        emotion: str,
        radio_effect: float,
        effects_enabled: bool,
    ) -> bytes:
        if self._kokoro is None:
            raise RuntimeError("text-to-speech model is not loaded")
        import numpy as np
        import soundfile as sf

        with self._tts_lock:
            started = time.perf_counter()
            speed = adjusted_speed(base_speed, emotion)
            chunks = [
                np.asarray(audio, dtype=np.float32).reshape(-1)
                for _, _, audio in self._kokoro(text, voice=voice, speed=speed)
            ]
            if not chunks:
                raise RuntimeError("Kokoro produced no audio")
            audio = apply_voice_effects(
                np.concatenate(chunks),
                24_000,
                emotion,
                radio_effect,
                effects_enabled,
            )
            output = io.BytesIO()
            sf.write(output, audio, 24_000, format="WAV", subtype="PCM_16")
            self.last_tts_ms = (time.perf_counter() - started) * 1_000.0
            return output.getvalue()

    def close(self) -> None:
        with self._lock:
            self._whisper = None
            self._kokoro = None
            self._state = "not_loaded"

    def _load_whisper(self, model_class: Any) -> Any:
        return model_class(
            self._settings.whisper_model,
            device=self._device,
            compute_type=self._compute_type,
            download_root=str(self._settings.model_directory / "whisper"),
            local_files_only=not self._settings.allow_model_downloads,
        )

    @staticmethod
    def _fallback_note(component: str, error: Exception) -> str:
        return (
            f"{component} accelerator initialization failed; using CPU: "
            f"{type(error).__name__}: {error}"
        )[:500]

    @staticmethod
    def _detect_whisper_device() -> tuple[str, str]:
        try:
            import ctranslate2

            if ctranslate2.get_cuda_device_count() > 0:
                return "cuda", "float16"
        except Exception:
            pass
        return "cpu", "int8"
