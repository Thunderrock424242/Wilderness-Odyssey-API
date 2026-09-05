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
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(velocityX) || !Double.isFinite(velocityZ)) return;
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
            if (!advect(level, emitter)) {
                EMITTERS[index] = null;
                continue;
            }
            float remaining = Math.min(1.0f, (emitter.expiresAt - gameTime) / (float) emitter.kind.lifetimeTicks);
            if (Math.floorMod(gameTime + index, emitter.kind.intervalTicks) != 0L
                    || level.getRandom().nextFloat() > emitter.intensity * remaining) {
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
        FOAM(ParticleTypes.SPLASH, 80, 4, 0.035, 0.70, 0.03),
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

    // Move emitter metadata through the existing immutable water snapshots.
    // No block scans occur, and dry obstacles stop the sheet rather than
    // allowing persistent foam to travel through a wall or unloaded chunk.
    private static boolean advect(ClientLevel level, Emitter emitter) {
        net.minecraft.core.BlockPos position = net.minecraft.core.BlockPos.containing(emitter.x, emitter.y, emitter.z);
        if (!level.hasChunkAt(position)) return false;
        if (emitter.kind == Kind.MIST) return true;
        var snapshot = com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore
                .getAtBlock(level, position.getX(), position.getZ());
        if (snapshot == null) return false;
        var column = snapshot.column(position.getX() & 15, position.getZ() & 15);
        if (!column.wet()) return false;
        double targetX = column.velocityX() / 20.0;
        double targetZ = column.velocityZ() / 20.0;
        emitter.velocityX += (targetX - emitter.velocityX) * 0.08;
        emitter.velocityZ += (targetZ - emitter.velocityZ) * 0.08;
        double nextX = emitter.x + Math.max(-0.15, Math.min(0.15, emitter.velocityX));
        double nextZ = emitter.z + Math.max(-0.15, Math.min(0.15, emitter.velocityZ));
        int blockX = (int) Math.floor(nextX);
        int blockZ = (int) Math.floor(nextZ);
        var next = com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore.getAtBlock(level, blockX, blockZ);
        if (next == null) return false;
        if (next.column(blockX & 15, blockZ & 15).wet()) {
            emitter.x = nextX;
            emitter.z = nextZ;
            emitter.y = next.column(blockX & 15, blockZ & 15).baseSurfaceY();
        } else {
            emitter.velocityX *= -0.25;
            emitter.velocityZ *= -0.25;
        }
        return true;
    }

    /** Returns the bounded active emitter count for profiling. */
    public static int activeCount() {
        int count = 0;
        for (Emitter emitter : EMITTERS) if (emitter != null) count++;
        return count;
    }

    private static final class Emitter {
        private final Kind kind;
        private double x, y, z, velocityX, velocityZ;
        private final float intensity;
        private final long expiresAt;

        private Emitter(Kind kind, double x, double y, double z, double velocityX, double velocityZ,
                        float intensity, long expiresAt) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.velocityX = velocityX;
            this.velocityZ = velocityZ;
            this.intensity = intensity;
            this.expiresAt = expiresAt;
        }
    }
}
