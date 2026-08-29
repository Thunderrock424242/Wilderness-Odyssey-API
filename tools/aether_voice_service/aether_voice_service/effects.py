from __future__ import annotations

import numpy as np


EMOTION_SPEED = {
    "normal": 1.00,
    "calm": 0.98,
    "concerned": 0.99,
    "urgent": 1.06,
    "damaged": 0.99,
    "weak": 0.97,
    "mysterious": 0.97,
}


def adjusted_speed(base_speed: float, emotion: str) -> float:
    multiplier = EMOTION_SPEED.get(emotion, 1.0)
    return float(np.clip(base_speed * multiplier, 0.70, 1.35))


def apply_voice_effects(
    audio: np.ndarray,
    sample_rate: int,
    emotion: str,
    radio_effect: float,
    enabled: bool,
) -> np.ndarray:
    """Applies bounded presentation effects without changing the TTS model."""
    samples = np.asarray(audio, dtype=np.float32).reshape(-1).copy()
    if samples.size == 0:
        return samples

    if emotion == "weak":
        samples *= 0.82
    if not enabled:
        return np.clip(samples, -1.0, 1.0)

    strength = float(np.clip(radio_effect, 0.0, 0.35))
    if emotion == "damaged":
        strength = max(strength, 0.08)
    elif emotion == "mysterious":
        strength = max(strength, 0.035)

    if strength <= 0.0:
        return np.clip(samples, -1.0, 1.0)

    difference = np.empty_like(samples)
    difference[0] = samples[0]
    difference[1:] = samples[1:] - samples[:-1]
    samples = samples * (1.0 - 0.16 * strength) + difference * (0.16 * strength)
    samples = np.tanh(samples * (1.0 + 0.24 * strength))

    noise = np.random.default_rng(0xAE7E).normal(0.0, 0.0025 * strength, samples.size)
    samples += noise.astype(np.float32)

    if emotion == "damaged" and strength >= 0.16:
        dropout_samples = max(1, int(sample_rate * 0.012))
        for fraction in (0.29, 0.57, 0.81):
            start = int(samples.size * fraction)
            end = min(samples.size, start + dropout_samples)
            samples[start:end] *= 0.35

    return np.clip(samples, -1.0, 1.0).astype(np.float32, copy=False)
