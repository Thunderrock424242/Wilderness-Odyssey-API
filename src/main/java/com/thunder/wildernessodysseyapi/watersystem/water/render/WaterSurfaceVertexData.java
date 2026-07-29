package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * Packs slowly changing surface metadata into the low bits of block vertex color.
 *
 * <p>The active mesh must retain {@code DefaultVertexFormat.BLOCK} so Minecraft's
 * stock translucent shader remains a safe fallback after a resource reload.
 * Five high bits preserve the optical color while three visually insignificant
 * low bits carry current, shore, and depth cues for the custom shader.</p>
 */
final class WaterSurfaceVertexData {

    static final float MAX_RENDER_CURRENT = 1.5f;
    static final float DEPTH_NORMALIZATION_BLOCKS = 24.0f;
    private static final float LOW_RENDER_CURRENT = 0.10f;
    private static final float MEDIUM_RENDER_CURRENT = 0.45f;
    private static final int PAYLOAD_MASK = 0x07;
    private static final int DISPLAY_MASK = 0xF8;

    private WaterSurfaceVertexData() {
    }

    static int encodeColor(
            int argb,
            float velocityX,
            float velocityZ,
            float shoreFactor,
            float depth
    ) {
        int alpha = packChannel((argb >>> 24) & 0xFF,
                quantizeUnit(depth / DEPTH_NORMALIZATION_BLOCKS));
        int red = packChannel((argb >>> 16) & 0xFF, quantizeSigned(velocityX));
        int green = packChannel((argb >>> 8) & 0xFF, quantizeSigned(velocityZ));
        int blue = packChannel(argb & 0xFF, quantizeUnit(shoreFactor));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    static int displayChannel(int encodedChannel) {
        return Math.min(255, (encodedChannel & DISPLAY_MASK) + 4);
    }

    static float decodeCurrentX(int encodedArgb) {
        return decodeSigned((encodedArgb >>> 16) & 0xFF);
    }

    static float decodeCurrentZ(int encodedArgb) {
        return decodeSigned((encodedArgb >>> 8) & 0xFF);
    }

    static float decodeShoreFactor(int encodedArgb) {
        return decodeUnit(encodedArgb & 0xFF);
    }

    static float decodeDepthFactor(int encodedArgb) {
        return decodeUnit((encodedArgb >>> 24) & 0xFF);
    }

    private static int packChannel(int display, int payload) {
        return (display & DISPLAY_MASK) | (payload & PAYLOAD_MASK);
    }

    // Code zero is exactly still water. Codes 1..3 are positive and 4..6
    // negative, avoiding the permanent drift caused by an even signed range.
    private static int quantizeSigned(float value) {
        float bounded = clamp(finiteOrZero(value), -MAX_RENDER_CURRENT, MAX_RENDER_CURRENT);
        float magnitudeValue = Math.abs(bounded);
        if (magnitudeValue < LOW_RENDER_CURRENT * 0.5f) {
            return 0;
        }
        int magnitude;
        if (magnitudeValue < (LOW_RENDER_CURRENT + MEDIUM_RENDER_CURRENT) * 0.5f) {
            magnitude = 1;
        } else if (magnitudeValue < (MEDIUM_RENDER_CURRENT + MAX_RENDER_CURRENT) * 0.5f) {
            magnitude = 2;
        } else {
            magnitude = 3;
        }
        return bounded > 0.0f ? magnitude : magnitude + 3;
    }

    private static float decodeSigned(int encodedChannel) {
        int code = encodedChannel & PAYLOAD_MASK;
        if (code == 0 || code == 7) {
            return 0.0f;
        }
        int magnitude = code <= 3 ? code : code - 3;
        float decoded = switch (magnitude) {
            case 1 -> LOW_RENDER_CURRENT;
            case 2 -> MEDIUM_RENDER_CURRENT;
            default -> MAX_RENDER_CURRENT;
        };
        return code <= 3 ? decoded : -decoded;
    }

    private static int quantizeUnit(float value) {
        return Math.round(clamp(finiteOrZero(value), 0.0f, 1.0f) * 7.0f);
    }

    private static float decodeUnit(int encodedChannel) {
        return (encodedChannel & PAYLOAD_MASK) / 7.0f;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
