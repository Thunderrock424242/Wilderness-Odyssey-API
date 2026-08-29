package com.thunder.wildernessodysseyapi.cinematic.sequence;

import java.util.Optional;

/** Resolution-independent layout rules for the cryo medical instrument overlay. */
final class CryoWakeupHudLayout {
    private CryoWakeupHudLayout() {
    }

    static Bounds boot(int screenWidth, int screenHeight) {
        int margin = Math.max(16, screenWidth / 12);
        int width = Math.min(280, Math.max(180, screenWidth - margin * 2));
        int height = 52;
        int x = Math.min(margin, Math.max(8, screenWidth - width - 8));
        int y = Math.min(screenHeight - height - 12, Math.max(12, Math.round(screenHeight * 0.61F)));
        return new Bounds(x, y, width, height);
    }

    static Optional<Bounds> telemetry(int screenWidth, int screenHeight) {
        if (screenWidth < 260 || screenHeight < 210) {
            return Optional.empty();
        }
        int width = Math.min(226, Math.max(198, screenWidth / 3));
        int height = 112;
        int x = 12;
        int y = Math.max(12, screenHeight / 14);
        return Optional.of(new Bounds(x, y, width, height));
    }

    static Bounds subtitle(
            int screenWidth,
            int screenHeight,
            int contentWidth,
            int lineCount
    ) {
        int width = Math.min(screenWidth - 32, Math.max(220, contentWidth + 24));
        int height = 28 + Math.max(1, lineCount) * 11;
        int x = (screenWidth - width) / 2;
        int preferredY = Math.round(screenHeight * 0.76F);
        int y = Math.min(preferredY, screenHeight - height - 14);
        return new Bounds(x, Math.max(8, y), width, height);
    }

    record Bounds(int x, int y, int width, int height) {
        Bounds {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("HUD bounds require positive dimensions");
            }
        }

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }
}
