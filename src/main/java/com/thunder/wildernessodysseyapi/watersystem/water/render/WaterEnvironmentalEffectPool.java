package com.thunder.wildernessodysseyapi.watersystem.water.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Fixed-cap client pool for river foam, floating debris, and cool mist.
 *
 * <p>Slots contain only short-lived emitter metadata; they are neither world
 * entities nor persistent water state. Reusing the weakest slot prevents storm
 * rivers and confluences from creating unbounded client allocations.</p>
 */
public final class WaterEnvironmentalEffectPool {

    private static final int MAX_EMITTERS = 48;
    private static final Emitter[] EMITTERS = new Emitter[MAX_EMITTERS];

    private WaterEnvironmentalEffectPool() {
    }

    /** Adds or refreshes one bounded environmental emitter. */
    public static void offer(
            Kind kind,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityZ,
            float intensity,
            long gameTime
    ) {
        if (kind == null || !Float.isFinite(intensity) || intensity <= 0.02f) {
            return;
        }
        int slot = -1;
        float weakest = Float.MAX_VALUE;
        for (int index = 0; index < EMITTERS.length; index++) {
            Emitter emitter = EMITTERS[index];
            if (emitter == null || emitter.expiresAt <= gameTime) {
                slot = index;
                break;
            }
            if (emitter.intensity < weakest) {
                weakest = emitter.intensity;
                slot = index;
            }
        }
        float bounded = Math.max(0.0f, Math.min(1.0f, intensity));
        if (EMITTERS[slot] != null && EMITTERS[slot].expiresAt > gameTime
                && EMITTERS[slot].intensity > bounded) {
            return;
        }
        EMITTERS[slot] = new Emitter(
                kind, x, y, z, velocityX, velocityZ, bounded,
                gameTime + kind.lifetimeTicks
        );
    }

    /** Advances all active slots and emits at most one particle per due slot. */
    public static void tick(ClientLevel level) {
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        for (int index = 0; index < EMITTERS.length; index++) {
            Emitter emitter = EMITTERS[index];
            if (emitter == null) {
                continue;
            }
            if (emitter.expiresAt <= gameTime) {
                EMITTERS[index] = null;
                continue;
            }
            if (Math.floorMod(gameTime + index, emitter.kind.intervalTicks) != 0L
                    || level.getRandom().nextFloat() > emitter.intensity) {
                continue;
            }
            double spread = 0.10 + emitter.intensity * 0.24;
            double x = emitter.x + (level.getRandom().nextDouble() - 0.5) * spread;
            double z = emitter.z + (level.getRandom().nextDouble() - 0.5) * spread;
            level.addParticle(
                    emitter.kind.particle,
                    x,
                    emitter.y + emitter.kind.heightOffset,
                    z,
                    emitter.velocityX * emitter.kind.velocityScale,
                    emitter.kind.verticalVelocity * emitter.intensity,
                    emitter.velocityZ * emitter.kind.velocityScale
            );
        }
    }

    /** Clears all ephemeral slots when a client level unloads. */
    public static void clear() {
        java.util.Arrays.fill(EMITTERS, null);
    }

    /** Bounded environmental presentation categories. */
    public enum Kind {
        FOAM(ParticleTypes.SPLASH, 12, 2, 0.035, 0.70, 0.03),
        DEBRIS(ParticleTypes.COMPOSTER, 20, 4, 0.015, 0.50, 0.05),
        MIST(ParticleTypes.CLOUD, 16, 3, 0.025, 0.24, 0.20);

        private final ParticleOptions particle;
        private final int lifetimeTicks;
        private final int intervalTicks;
        private final double verticalVelocity;
        private final double velocityScale;
        private final double heightOffset;

        Kind(
                ParticleOptions particle,
                int lifetimeTicks,
                int intervalTicks,
                double verticalVelocity,
                double velocityScale,
                double heightOffset
        ) {
            this.particle = particle;
            this.lifetimeTicks = lifetimeTicks;
            this.intervalTicks = intervalTicks;
            this.verticalVelocity = verticalVelocity;
            this.velocityScale = velocityScale;
            this.heightOffset = heightOffset;
        }
    }

    private record Emitter(
            Kind kind,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityZ,
            float intensity,
            long expiresAt
    ) {
    }
}
