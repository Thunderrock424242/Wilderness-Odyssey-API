from __future__ import annotations

import argparse
import json
import math
import os
from importlib.metadata import version
from pathlib import Path
from shutil import rmtree
from uuid import uuid4

import numpy as np
import soundfile as sf


SAMPLE_RATE = 24_000
SUBTITLE_FADE_TICKS = 6
NARRATION_PREFIX = "cinematic.wildernessodysseyapi.cryo.narration."
EXPECTED_KOKORO_VERSION = "0.9.4"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate version-pinned neural A.E.T.H.E.R cryo narration assets."
    )
    parser.add_argument("--voice", default="af_bella")
    parser.add_argument("--speed", type=float, default=1.0)
    parser.add_argument("--allow-downloads", action="store_true")
    return parser.parse_args()


def prepare_model_cache(allow_downloads: bool) -> Path:
    model_root = Path(
        os.environ.get(
            "AETHER_VOICE_MODEL_DIR",
            str(Path.home() / ".cache" / "aether-voice"),
        )
    )
    huggingface_root = model_root / "huggingface"
    huggingface_root.mkdir(parents=True, exist_ok=True)
    os.environ["HF_HOME"] = str(huggingface_root)
    if allow_downloads:
        os.environ.pop("HF_HUB_OFFLINE", None)
    else:
        os.environ["HF_HUB_OFFLINE"] = "1"
    return model_root


def spoken_version(text: str) -> str:
    # Keep the acronym in subtitles while giving the neural voice a natural
    # spoken name instead of asking it to pronounce six isolated letters.
    return text.replace("A.E.T.H.E.R.", "Aether").replace("A.E.T.H.E.R", "Aether")


def master_voice(chunks: list[np.ndarray]) -> np.ndarray:
    if not chunks:
        raise RuntimeError("Kokoro produced no audio")
    gap = np.zeros(round(SAMPLE_RATE * 0.10), dtype=np.float32)
    joined: list[np.ndarray] = []
    for index, chunk in enumerate(chunks):
        if index > 0:
            joined.append(gap)
        joined.append(np.asarray(chunk, dtype=np.float32).reshape(-1))
    samples = np.concatenate(joined)
    if samples.size == 0:
        raise RuntimeError("Kokoro produced an empty audio buffer")

    # Preserve the voice's breath and dynamics. Only tame DC offset, apply a
    # restrained peak ceiling, and hide file-boundary clicks with short fades.
    samples -= float(np.mean(samples))
    peak = float(np.max(np.abs(samples)))
    if peak > 0.0:
        samples *= min(1.35, 0.88 / peak)
    fade_samples = min(samples.size // 2, round(SAMPLE_RATE * 0.012))
    if fade_samples > 0:
        fade = np.linspace(0.0, 1.0, fade_samples, dtype=np.float32)
        samples[:fade_samples] *= fade
        samples[-fade_samples:] *= fade[::-1]
    return np.clip(samples, -1.0, 1.0).astype(np.float32, copy=False)


def create_staging_directory(repo_root: Path) -> Path:
    staging_parent = repo_root / "build" / "tmp" / "aether_voice"
    staging_parent.mkdir(parents=True, exist_ok=True)
    staging_directory = staging_parent / uuid4().hex
    # Use an ordinary inherited directory instead of TemporaryDirectory's
    # private Windows ACL. os.replace preserves the staged file's ACL.
    staging_directory.mkdir()
    return staging_directory


def main() -> None:
    arguments = parse_arguments()
    if not 0.75 <= arguments.speed <= 1.25:
        raise ValueError("speed must be between 0.75 and 1.25")
    installed_kokoro = version("kokoro")
    if installed_kokoro != EXPECTED_KOKORO_VERSION:
        raise RuntimeError(
            "authored cryo generation requires Kokoro "
            f"{EXPECTED_KOKORO_VERSION}, found {installed_kokoro}"
        )

    repo_root = Path(__file__).resolve().parents[2]
    language_path = (
        repo_root
        / "src/main/resources/assets/wildernessodysseyapi/lang/en_us.json"
    )
    output_directory = (
        repo_root
        / "src/main/resources/assets/wildernessodysseyapi/voice/cryo"
    )
    prepare_model_cache(arguments.allow_downloads)

    # Import after the cache and offline policy are set. KPipeline downloads
    # only during the explicit authoring run that passes --allow-downloads.
    from kokoro import KPipeline

    with language_path.open("r", encoding="utf-8") as input_file:
        language = json.load(input_file)
    narration = {
        key[len(NARRATION_PREFIX) :]: value
        for key, value in language.items()
        if key.startswith(NARRATION_PREFIX)
    }
    if len(narration) != 20:
        raise RuntimeError(f"expected 20 cryo narration lines, found {len(narration)}")

    output_directory.mkdir(parents=True, exist_ok=True)
    pipeline = KPipeline(lang_code="a", device="cpu")
    pipeline.load_voice(arguments.voice)

    clips: dict[str, dict[str, object]] = {}
    staging_directory = create_staging_directory(repo_root)
    try:
        for cue, subtitle in narration.items():
            source_text = str(subtitle)
            chunks = [
                np.asarray(audio, dtype=np.float32)
                for _, _, audio in pipeline(
                    spoken_version(source_text),
                    voice=arguments.voice,
                    speed=arguments.speed,
                )
            ]
            audio = master_voice(chunks)
            staged_path = staging_directory / f"{cue}.wav"
            sf.write(staged_path, audio, SAMPLE_RATE, format="WAV", subtype="PCM_16")
            duration_seconds = audio.size / SAMPLE_RATE
            clips[cue] = {
                "file": staged_path.name,
                "source_text": source_text,
                "duration_ticks": math.ceil(duration_seconds * 20.0)
                + SUBTITLE_FADE_TICKS,
                "duration_seconds": round(duration_seconds, 3),
            }
            print(f"{cue}: {duration_seconds:.3f}s")

        # Keep a failed model pass from leaving a half-regenerated authored set.
        # All inference and encoding succeeds before source assets are replaced.
        for cue in narration:
            os.replace(
                staging_directory / f"{cue}.wav",
                output_directory / f"{cue}.wav",
            )
    finally:
        rmtree(staging_directory, ignore_errors=True)

    manifest = {
        "engine": f"Kokoro-82M {installed_kokoro}",
        "model": "hexgrad/Kokoro-82M",
        "voice": arguments.voice,
        "style": "grounded_conversational_caretaker",
        "speed": arguments.speed,
        "sample_rate": SAMPLE_RATE,
        "clips": clips,
    }
    manifest_path = output_directory / "manifest.json"
    with manifest_path.open("w", encoding="utf-8", newline="\n") as output_file:
        json.dump(manifest, output_file, indent=2)
        output_file.write("\n")
    print(f"Generated {len(clips)} neural cryo clips with {arguments.voice}.")


if __name__ == "__main__":
    main()
