package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.entity.BoatWaveRenderState;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the mod's wave response to Minecraft's boat render-state snapshot.
 *
 * <p>This bridge is required by the 1.21 render-state API: the eventual render
 * method receives a state object rather than the original boat entity.</p>
 */
@Mixin(BoatRenderState.class)
public class BoatRenderStateMixin implements BoatWaveRenderState {

    @Unique
    private float wildernessodysseyapi$wavePitch;
    @Unique
    private float wildernessodysseyapi$waveRoll;
    @Unique
    private float wildernessodysseyapi$waveBob;

    @Override
    public void wildernessodysseyapi$setWaveResponse(float pitch, float roll, float bob) {
        wildernessodysseyapi$wavePitch = pitch;
        wildernessodysseyapi$waveRoll = roll;
        wildernessodysseyapi$waveBob = bob;
    }

    @Override
    public float wildernessodysseyapi$getWavePitch() {
        return wildernessodysseyapi$wavePitch;
    }

    @Override
    public float wildernessodysseyapi$getWaveRoll() {
        return wildernessodysseyapi$waveRoll;
    }

    @Override
    public float wildernessodysseyapi$getWaveBob() {
        return wildernessodysseyapi$waveBob;
    }
}
