package com.thunder.wildernessodysseyapi.anomaly;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Temporary per-player developer presentation overrides; no terrain or save data is changed. */
public final class AnomalyPreviewOverrides {
    public record Preview(String dimension, int zone, double intensity, long createdTick, long expiresTick) { }

    private final Map<UUID, Preview> previews = new HashMap<>();

    public void set(UUID player, String dimension, int zone, double intensity, long tick, long expiresTick) {
        if (zone < 0 || zone > 4 || !Double.isFinite(intensity) || intensity < 0 || intensity > 1
                || expiresTick <= tick) {
            throw new IllegalArgumentException("Invalid anomaly preview");
        }
        previews.put(player, new Preview(dimension, zone, intensity, tick, expiresTick));
    }

    public Optional<Preview> get(UUID player, String dimension, long tick) {
        Preview preview = previews.get(player);
        if (preview == null) return Optional.empty();
        if (!preview.dimension().equals(dimension) || tick < preview.createdTick() || tick >= preview.expiresTick()) {
            previews.remove(player);
            return Optional.empty();
        }
        return Optional.of(preview);
    }

    public void clear(UUID player) {
        previews.remove(player);
    }
}
