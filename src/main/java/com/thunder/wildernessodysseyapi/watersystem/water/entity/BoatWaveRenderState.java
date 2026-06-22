package com.thunder.wildernessodysseyapi.watersystem.water.entity;

/**
 * Carries wave response through Minecraft's 1.21 entity render-state pipeline.
 *
 * <p>Boat entities are converted into reusable {@code BoatRenderState}
 * instances before rendering, so the renderer cannot safely look up an entity
 * ID. A client mixin implements this bridge on the render state itself.</p>
 */
public interface BoatWaveRenderState {

    /** Stores the wave response copied from the source boat entity. */
    void wildernessodysseyapi$setWaveResponse(float pitch, float roll, float bob);

    /** Returns the boat-local pitch in degrees. */
    float wildernessodysseyapi$getWavePitch();

    /** Returns the boat-local roll in degrees. */
    float wildernessodysseyapi$getWaveRoll();

    /** Returns the vertical render-only bob offset in blocks. */
    float wildernessodysseyapi$getWaveBob();
}
