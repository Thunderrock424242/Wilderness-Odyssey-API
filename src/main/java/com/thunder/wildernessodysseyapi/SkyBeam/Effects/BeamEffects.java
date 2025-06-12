package com.thunder.wildernessodysseyapi.SkyBeam.Effects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class BeamEffects {
    public static void spawnBeam(ServerLevel world, BlockPos pos) {
        // play sounds
        world.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f,1f);
        // flash + particle column (reuse the AngledBeamRenderer approach or simple particles)
        world.sendParticles(ParticleTypes.END_ROD,
                pos.getX()+.5, pos.getY(), pos.getZ()+.5,
                200, 0, 128, 0, 0.02);
    }
}
