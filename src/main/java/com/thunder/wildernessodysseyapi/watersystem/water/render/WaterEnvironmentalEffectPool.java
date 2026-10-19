package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedServices;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

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
    private static int emissionCursor;

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
        var player = Minecraft.getInstance().player;
        if (level == null || player == null) {
            clear();
            return;
        }
        Settings settings = new Settings(WaterRenderingConfig.environmentalParticleBudget(),
                WaterRenderingConfig.riverEffectDistanceBlocks(), WaterRenderingConfig.riverFoamEnabled(),
                WaterRenderingConfig.riverSprayEnabled(), WaterRenderingConfig.riverDebrisEnabled());
        advance(level.getGameTime(), player.getX(), player.getEyeY(), player.getZ(), settings,
                (x, z) -> surface(level, x, z),
                (kind, x, y, z, vx, vy, vz) -> level.addParticle(particle(kind, level.getRandom()),
                        x, y, z, vx, vy, vz), level.getRandom());
    }

    /** Advances the fixed pool against immutable surface samples under one shared emission budget. */
    static int advance(long gameTime, double cameraX, double cameraY, double cameraZ,
                       Settings settings, SurfaceSampler sampler, EmissionSink sink, RandomSource random) {
        if (settings.budget() <= 0 || settings.distance() <= 0) {
            clear();
            return 0;
        }
        int emitted = 0;
        for (int offset = 0; offset < EMITTERS.length; offset++) {
            int index = (emissionCursor + offset) % EMITTERS.length;
            Emitter emitter = EMITTERS[index];
            if (emitter == null) continue;
            double dx = emitter.x - cameraX;
            double dy = emitter.y - cameraY;
            double dz = emitter.z - cameraZ;
            if (!admitted(gameTime, emitter.expiresAt, dx * dx + dy * dy + dz * dz,
                    settings.distance(), settings.enabled(emitter.kind))) {
                EMITTERS[index] = null;
                continue;
            }
            if (emitter.kind == Kind.FOAM || emitter.kind == Kind.DEBRIS) {
                Surface current = sampler.sample(emitter.x, emitter.z);
                if (current == null) {
                    EMITTERS[index] = null;
                    continue;
                }
                Advection next = advect(emitter.x, emitter.y, emitter.z,
                        emitter.velocityX, emitter.velocityZ, current.column(),
                        current.backgroundX(), current.backgroundZ(), (x, z) -> {
                            Surface destination = sampler.sample(x, z);
                            return destination == null ? Float.NaN : destination.height();
                        });
                if (!next.present()) {
                    EMITTERS[index] = null;
                    continue;
                }
                emitter.x = next.x();
                emitter.y = next.y();
                emitter.z = next.z();
                emitter.velocityX = next.velocityX();
                emitter.velocityZ = next.velocityZ();
            }
            float remaining = Math.min(1.0f, (emitter.expiresAt - gameTime) / (float) emitter.kind.lifetimeTicks);
            if (emitted >= settings.budget()
                    || Math.floorMod(gameTime + index, emitter.kind.intervalTicks) != 0L
                    || random.nextFloat() > emitter.intensity * remaining) {
                continue;
            }
            double spread = 0.10 + emitter.intensity * 0.24;
            double x = emitter.x + (random.nextDouble() - 0.5) * spread;
            double z = emitter.z + (random.nextDouble() - 0.5) * spread;
            sink.emit(emitter.kind,
                    x,
                    emitter.y + emitter.kind.heightOffset,
                    z,
                    emitter.velocityX * emitter.kind.velocityScale,
                    emitter.kind.verticalVelocity * emitter.intensity,
                    emitter.velocityZ * emitter.kind.velocityScale
            );
            emitted++;
        }
        emissionCursor = (emissionCursor + 1) % EMITTERS.length;
        return emitted;
    }

    /** Clears all ephemeral slots when a client level unloads. */
    public static void clear() {
        java.util.Arrays.fill(EMITTERS, null);
        emissionCursor = 0;
    }

    /** Bounded environmental presentation categories. */
    public enum Kind {
        FOAM(80, 4, 0.035, 0.70, 0.03),
        DEBRIS(24, 4, 0.004, 0.50, 0.025),
        MIST(16, 3, 0.025, 0.24, 0.20);

        private final int lifetimeTicks;
        private final int intervalTicks;
        private final double verticalVelocity;
        private final double velocityScale;
        private final double heightOffset;

        Kind(
                int lifetimeTicks,
                int intervalTicks,
                double verticalVelocity,
                double velocityScale,
                double heightOffset
        ) {
            this.lifetimeTicks = lifetimeTicks;
            this.intervalTicks = intervalTicks;
            this.verticalVelocity = verticalVelocity;
            this.velocityScale = velocityScale;
            this.heightOffset = heightOffset;
        }
    }

    private static Surface surface(ClientLevel level, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        var snapshot = ClientWaterSnapshotStore.getAtBlock(level, blockX, blockZ);
        if (snapshot == null || !level.hasChunk(blockX >> 4, blockZ >> 4)) return null;
        var column = snapshot.column(blockX & 15, blockZ & 15);
        if (!column.wet() || column.surfaceCovered() || column.falling()) return null;
        var flow = WatershedServices.localFlow(level, new BlockPos(blockX, column.surfaceBlockY(), blockZ));
        float height = ClientWaterImmersion.visibleSurfaceHeight(level, column, x, z, 0);
        return Float.isFinite(height) ? new Surface(column, flow.currentX(), flow.currentZ(), height) : null;
    }

    private static ParticleOptions particle(Kind kind, RandomSource random) {
        if (kind == Kind.FOAM) return ParticleTypes.SPLASH;
        if (kind == Kind.MIST) return ParticleTypes.CLOUD;
        // Vanilla block fragments provide small leaf/twig flecks without entities,
        // item ownership, custom particle registries, or a second render pass.
        return new BlockParticleOption(ParticleTypes.BLOCK,
                (random.nextBoolean() ? Blocks.OAK_LEAVES : Blocks.OAK_LOG).defaultBlockState());
    }

    /** Loaded boundaries, quality switches and distance culling all remove stale slots before emission. */
    static boolean admitted(long gameTime, long expiresAt, double distanceSquared, int distance, boolean enabled) {
        return enabled && expiresAt > gameTime && distance > 0 && Double.isFinite(distanceSquared)
                && distanceSquared >= 0 && distanceSquared <= (double) distance * distance;
    }

    /** Floating metadata follows the shared physical current and visible surface, never a falling curtain. */
    static Advection advect(double x, double y, double z, double velocityX, double velocityZ,
                            ClientWaterChunkSnapshot.Column column, float backgroundX, float backgroundZ,
                            HeightSampler sampler) {
        if (column == null || !column.wet() || column.falling()) {
            return new Advection(x, y, z, 0, 0, false);
        }
        double previousX = Double.isFinite(velocityX) ? velocityX : 0;
        double previousZ = Double.isFinite(velocityZ) ? velocityZ : 0;
        double nextVelocityX = previousX + (column.currentX(backgroundX) / 20.0 - previousX) * 0.08;
        double nextVelocityZ = previousZ + (column.currentZ(backgroundZ) / 20.0 - previousZ) * 0.08;
        nextVelocityX = Math.max(-0.15, Math.min(0.15, nextVelocityX));
        nextVelocityZ = Math.max(-0.15, Math.min(0.15, nextVelocityZ));
        double nextX = x + nextVelocityX;
        double nextZ = z + nextVelocityZ;
        float height = sampler.sample(nextX, nextZ);
        if (!Float.isFinite(height) || Math.abs(height - y) > 2.0) {
            return new Advection(x, y, z, 0, 0, false);
        }
        return new Advection(nextX, height, nextZ, nextVelocityX, nextVelocityZ, true);
    }

    record Settings(int budget, int distance, boolean foam, boolean spray, boolean debris) {
        boolean enabled(Kind kind) { return switch (kind) { case FOAM -> foam; case MIST -> spray; case DEBRIS -> debris; }; }
    }

    record Surface(ClientWaterChunkSnapshot.Column column, float backgroundX, float backgroundZ, float height) { }
    record Advection(double x, double y, double z, double velocityX, double velocityZ, boolean present) { }
    @FunctionalInterface interface HeightSampler { float sample(double x, double z); }
    @FunctionalInterface interface SurfaceSampler { Surface sample(double x, double z); }
    @FunctionalInterface interface EmissionSink {
        void emit(Kind kind, double x, double y, double z, double velocityX, double velocityY, double velocityZ);
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
