package com.thunder.wildernessodysseyapi.watersystem.water.render;

/** Tracks scene-copy provenance without allocating a key or touching the graphics API. */
final class WaterSceneCaptureState {

    private boolean valid;
    private long capturedFrame;
    private long capturedBackend;
    private int capturedWidth;
    private int capturedHeight;

    boolean canReuse(long frameIndex, long backendGeneration, int width, int height) {
        return valid && capturedFrame == frameIndex && capturedBackend == backendGeneration
                && capturedWidth == width && capturedHeight == height;
    }

    void captured(long frameIndex, long backendGeneration, int width, int height) {
        capturedFrame = frameIndex;
        capturedBackend = backendGeneration;
        capturedWidth = width;
        capturedHeight = height;
        valid = width > 0 && height > 0;
    }

    void invalidate() {
        valid = false;
    }
}
