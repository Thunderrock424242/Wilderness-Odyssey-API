"""Author original mono surf and rain-on-water textures with NumPy and FFmpeg.

No recordings or Minecraft samples are used. This is an offline asset-authoring
tool; Minecraft never synthesizes audio or launches FFmpeg at runtime.
"""

from pathlib import Path
import subprocess
import wave

import numpy as np


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/assets/wildernessodysseyapi/sounds/ambient"
SCRATCH = ROOT / ".codex-build/audio-authoring"
SAMPLE_RATE = 48000


def water_noise(count, random, low, high):
    frequencies = np.fft.rfftfreq(count, 1.0 / SAMPLE_RATE)
    spectrum = np.fft.rfft(random.standard_normal(count))
    band = (frequencies / (frequencies + low)) ** 2 / (1.0 + (frequencies / high) ** 4)
    band *= (np.maximum(frequencies, 100.0) / 100.0) ** -0.22
    samples = np.fft.irfft(spectrum * band, n=count)
    return samples / max(float(np.std(samples)), 1e-9)


def surf(duration, seed, attack, decay, high, froth):
    random = np.random.default_rng(seed)
    count = round(duration * SAMPLE_RATE)
    time = np.arange(count) / SAMPLE_RATE
    # Broad rising wash and a long retreat avoid the sharp onset of an impact splash.
    envelope = (1.0 - np.exp(-time / attack)) ** 2 * np.exp(-time / decay)
    rolling = 0.78 + 0.12 * np.sin(time * 8.2 + random.uniform(0.0, 6.0))
    rolling += 0.10 * np.sin(time * 19.0 + random.uniform(0.0, 6.0))
    body = water_noise(count, random, 90.0, high) * envelope * rolling
    foam_envelope = (1.0 - np.exp(-time / (attack * 1.4))) * np.exp(-time / (decay * 0.75))
    foam = water_noise(count, random, 1100.0, 5500.0) * foam_envelope * froth
    return body + foam


def rain(duration, seed):
    random = np.random.default_rng(seed)
    count = round(duration * SAMPLE_RATE)
    samples = water_noise(count, random, 900.0, 6200.0) * 0.018
    # Tiny damped bubble resonances form a bed of drops, without a body-impact burst.
    for onset in sorted(random.uniform(0.025, duration - 0.065, 58)):
        drop_count = round(random.uniform(0.025, 0.055) * SAMPLE_RATE)
        time = np.arange(drop_count) / SAMPLE_RATE
        frequency = random.uniform(1400.0, 3200.0)
        phase = 2.0 * np.pi * frequency * (time + 0.004 * (1.0 - np.exp(-time / 0.004)))
        envelope = (1.0 - np.exp(-time / 0.0007)) * np.exp(-time / random.uniform(0.007, 0.014))
        bubble = np.sin(phase) + 0.08 * np.sin(phase * 1.91)
        transient = water_noise(drop_count, random, 1100.0, 8000.0) * 0.12
        drop = (bubble + transient) * envelope * random.uniform(0.12, 0.40)
        start = round(onset * SAMPLE_RATE)
        samples[start:start + drop_count] += drop
    return samples


def save(name, samples, peak):
    samples -= float(np.mean(samples))
    fade = min(round(0.025 * SAMPLE_RATE), len(samples) // 4)
    samples[:fade] *= np.linspace(0.0, 1.0, fade)
    samples[-fade:] *= np.linspace(1.0, 0.0, fade)
    samples *= peak / max(float(np.max(np.abs(samples))), 1e-9)
    if not np.all(np.isfinite(samples)):
        raise ValueError("Non-finite audio: " + name)
    pcm = np.rint(np.clip(samples, -1.0, 1.0) * 32767).astype("<i2")
    wav_path = SCRATCH / (name + ".wav")
    with wave.open(str(wav_path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(pcm.tobytes())
    ogg_path = OUTPUT / (name + ".ogg")
    subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                    "-i", str(wav_path), "-c:a", "libvorbis", "-q:a", "4", "-ac", "1",
                    str(ogg_path)], check=True)
    print(f"{name}: {len(samples) / SAMPLE_RATE:.2f}s, mono Vorbis, peak {peak:.2f}")


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    SCRATCH.mkdir(parents=True, exist_ok=True)
    profiles = {
        "coast_wash_soft": (2.4, 0.38, 0.95, 2100.0, 0.12),
        "coast_break": (3.2, 0.28, 1.15, 2900.0, 0.19),
        "coast_break_rocky": (3.6, 0.22, 1.20, 3400.0, 0.25),
        "coast_break_storm": (4.2, 0.34, 1.65, 3200.0, 0.23),
    }
    for index, (name, settings) in enumerate(profiles.items()):
        duration, attack, decay, high, froth = settings
        for variant in (1, 2):
            save(f"{name}_{variant}", surf(duration, 13102026 + index * 10 + variant,
                                           attack, decay, high, froth), 0.38)
    for variant in (1, 2, 3):
        save(f"rain_on_water_{variant}", rain(1.2, 13103026 + variant), 0.28)


if __name__ == "__main__":
    main()
