package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Applies Gerstner displacement and analytic normals while vanilla builds a
 * water chunk mesh.
 *
 * <p>{@code LiquidBlockRenderer} emits section-local coordinates, so this
 * wrapper reconstructs the true world position before sampling. The previous
 * implementation added the block position to an already section-relative
 * coordinate, which changed wave phase at every section boundary. Only top and
 * upper side vertices move; bottom faces remain anchored to terrain.</p>
 */
public final class GerstnerVertexConsumer implements VertexConsumer {

    private static final float SURFACE_VERTEX_EPSILON = 0.02f;
    private static final float FULL_WAVE_HEIGHT = 0.85f;

    private final VertexConsumer delegate;
    private final int sectionOriginX;
    private final int sectionOriginZ;
    private final int blockLocalY;
    private final WaterBodyClassifier.WaterType waterType;
    private final boolean dynamicOceanSurface;

    private boolean currentVertexIsSurface;
    private WaveSurfaceSample currentSample = WaveSurfaceSample.flat();

    /**
     * Creates a water vertex wrapper for one tessellated fluid block.
     *
     * @param delegate destination chunk vertex consumer
     * @param blockX fluid block world X
     * @param blockY fluid block world Y
     * @param blockZ fluid block world Z
     * @param waterType classified water-body type
     */
    public GerstnerVertexConsumer(VertexConsumer delegate, int blockX, int blockY, int blockZ,
                                  WaterBodyClassifier.WaterType waterType,
                                  boolean dynamicOceanSurface) {
        this.delegate = delegate;
        this.sectionOriginX = blockX & ~15;
        this.sectionOriginZ = blockZ & ~15;
        this.blockLocalY = blockY & 15;
        this.waterType = waterType;
        this.dynamicOceanSurface = dynamicOceanSurface;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        float localSurfaceHeight = y - blockLocalY;
        currentVertexIsSurface = localSurfaceHeight > SURFACE_VERTEX_EPSILON;

        if (currentVertexIsSurface && dynamicOceanSurface) {
            currentSample = WaveSurfaceSample.flat();
            delegate.addVertex(x, y, z);
            return this;
        }

        if (currentVertexIsSurface) {
            float worldX = sectionOriginX + x;
            float worldZ = sectionOriginZ + z;
            float waveBlend = smoothStep(
                    SURFACE_VERTEX_EPSILON,
                    FULL_WAVE_HEIGHT,
                    localSurfaceHeight
            );
            currentSample = GerstnerWaveAnimator
                    .getTerrainSurfaceSampleAt(worldX, worldZ, waterType)
                    .attenuated(waveBlend);

            delegate.addVertex(
                    x + currentSample.displacementX(),
                    y + currentSample.height(),
                    z + currentSample.displacementZ()
            );
        } else {
            currentSample = WaveSurfaceSample.flat();
            delegate.addVertex(x, y, z);
        }

        // Keep the chain on this wrapper so the later normal call can use the
        // exact sample that displaced this vertex.
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        // Keep the flat baked surface as the distant/failure-safe ocean. The
        // per-frame pass adds animated crests near the camera, while troughs
        // naturally depth-test behind this stable base instead of exposing gaps.
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        if (currentVertexIsSurface) {
            delegate.setNormal(
                    currentSample.normalX(),
                    currentSample.normalY(),
                    currentSample.normalZ()
            );
        } else {
            delegate.setNormal(normalX, normalY, normalZ);
        }
        return this;
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Math.max(0.0f, Math.min(1.0f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
}
