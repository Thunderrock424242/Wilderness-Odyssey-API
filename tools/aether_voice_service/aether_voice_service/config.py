from __future__ import annotations

import ipaddress
import os
from dataclasses import dataclass
from pathlib import Path


def _boolean(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True, slots=True)
class ServiceSettings:
    host: str
    port: int
    token: str
    model_directory: Path
    allow_model_downloads: bool
    whisper_model: str
    whisper_language: str
    kokoro_language: str
    default_voice: str

    @classmethod
    def from_environment(cls) -> "ServiceSettings":
        host = os.getenv("AETHER_VOICE_HOST", "127.0.0.1").strip()
        try:
            if not ipaddress.ip_address(host).is_loopback:
                raise ValueError("AETHER_VOICE_HOST must be a numeric loopback address")
        except ValueError as exc:
            raise ValueError("AETHER_VOICE_HOST must be 127.0.0.1 or ::1") from exc

        port = int(os.getenv("AETHER_VOICE_PORT", "8765"))
        if port < 1024 or port > 65535:
            raise ValueError("AETHER_VOICE_PORT must be between 1024 and 65535")

        model_directory = Path(
            os.getenv("AETHER_VOICE_MODEL_DIR", str(Path.home() / ".cache" / "aether-voice"))
        ).expanduser().resolve()
        return cls(
            host=host,
            port=port,
            token=os.getenv("AETHER_VOICE_TOKEN", "").strip(),
            model_directory=model_directory,
            allow_model_downloads=_boolean("AETHER_VOICE_ALLOW_MODEL_DOWNLOADS", False),
            whisper_model=os.getenv("AETHER_WHISPER_MODEL", "small.en").strip(),
            whisper_language=os.getenv("AETHER_WHISPER_LANGUAGE", "en").strip(),
            kokoro_language=os.getenv("AETHER_KOKORO_LANGUAGE", "a").strip(),
            default_voice=os.getenv("AETHER_KOKORO_VOICE", "af_heart").strip(),
        )
