package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * WaveVertexConsumer
 *
 * A delegating VertexConsumer that intercepts addVertex calls and
 * applies a Y-axis wave displacement to water surface (top-face) vertices.
 *
 * Only vertices whose local Y is near the top of the block (≥ 0.8f) are
 * displaced — this preserves the flat bottom and side faces.
 *
 * Usage:
 *   WaveVertexConsumer wvc = new WaveVertexConsumer(originalConsumer, blockX, blockZ);
 *   // pass wvc wherever the original consumer was used
 */
public class WaveVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final int blockX;
    private final int blockZ;

    // Track the last X/Z seen per addVertex so we can compute world position
    private float pendingX;
    private float pendingY;
    private float pendingZ;
    private boolean hasPending = false;

    public WaveVertexConsumer(VertexConsumer delegate, int blockX, int blockZ) {
        this.delegate = delegate;
        this.blockX = blockX;
        this.blockZ = blockZ;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        // Local y >= 0.8 means this vertex is on the top face of the fluid
        float worldX = blockX + x;
        float worldZ = blockZ + z;

        if (y >= 0.8f) {
            y += WaveAnimator.getWaveHeight(worldX, worldZ);
        }

        pendingX = x;
        pendingY = y;
        pendingZ = z;
        hasPending = true;

        return delegate.addVertex(x, y, z);
    }

    // ---- Delegate all other methods unchanged ----

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return delegate.setColor(r, g, b, a);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return delegate.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(float nx, float ny, float nz) {
        return delegate.setNormal(nx, ny, nz);
    }
}
