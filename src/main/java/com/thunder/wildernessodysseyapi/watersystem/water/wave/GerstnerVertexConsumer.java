package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * GerstnerVertexConsumer
 *
 * Wraps a VertexConsumer and applies Gerstner wave Y-displacement
 * to the top face of water blocks.
 *
 * Replaces the simpler WaveVertexConsumer from the first system.
 * The key difference: displacement is looked up from the classified
 * water body type so ocean/river/pond each get the right amplitude.
 */
public class GerstnerVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final int blockX, blockZ;
    private final WaterBodyClassifier.WaterType waterType;

    public GerstnerVertexConsumer(VertexConsumer delegate, int blockX, int blockZ,
                                   WaterBodyClassifier.WaterType waterType) {
        this.delegate  = delegate;
        this.blockX    = blockX;
        this.blockZ    = blockZ;
        this.waterType = waterType;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        // Only displace top-face vertices
        if (y >= 0.8f) {
            float worldX = blockX + x;
            float worldZ = blockZ + z;
            y += GerstnerWaveAnimator.getHeightAt(worldX, worldZ, waterType);
        }
        return delegate.addVertex(x, y, z);
    }

    @Override public VertexConsumer setColor(int r, int g, int b, int a)   { return delegate.setColor(r,g,b,a); }
    @Override public VertexConsumer setUv(float u, float v)                { return delegate.setUv(u, v); }
    @Override public VertexConsumer setUv1(int u, int v)                   { return delegate.setUv1(u, v); }
    @Override public VertexConsumer setUv2(int u, int v)                   { return delegate.setUv2(u, v); }
    @Override public VertexConsumer setNormal(float nx, float ny, float nz){ return delegate.setNormal(nx, ny, nz); }
}
