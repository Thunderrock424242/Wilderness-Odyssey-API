package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/**
 * Emits a small client-only layer of water ambience from synchronized snapshots.
 *
 * <p>Vanilla's underwater loop, entry/exit sounds, and rare additions already
 * read {@link LocalPlayer#isUnderWater()}; the entity-water parity hook feeds
 * that state from Wilderness water. This class deliberately adds no competing
 * audio loop. It only supplies missing namespaced-fluid particles under a
 * quality-scaled hard budget.</p>
 */
public final class WaterAmbientEffects {

    private static final int EMISSION_INTERVAL_TICKS = 2;
    private static final int MAX_ATTEMPTS_PER_PARTICLE = 5;
    private static final double EFFECT_RADIUS = 9.0;

    private WaterAmbientEffects() {
    }

    /** Emits one bounded ambience sample after the normal client water tick. */
    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        int budget = WaterRenderingConfig.ambientWaterParticleBudget();
        if (budget <= 0 || !shouldEmitAt(level.getGameTime(), EMISSION_INTERVAL_TICKS)) {
            return;
        }

        LocalPlayer player = minecraft.player;
        RandomSource random = level.getRandom();
        if (player.isUnderWater()) {
            emitUnderwaterMotes(level, player, random, budget);
        } else {
            emitSurfaceSpray(level, player, random, budget);
        }
    }

    // Suspended particles are normally supplied by vanilla WaterFluid's own
    // animateTick. BaseFlowingFluid does not inherit that visual behavior, so
    // snapshot validation keeps these particles inside real Wilderness water.
    private static void emitUnderwaterMotes(
            ClientLevel level,
            LocalPlayer player,
            RandomSource random,
            int budget
    ) {
        int emitted = 0;
        int attempts = attemptBudget(budget);
        for (int attempt = 0; attempt < attempts && emitted < budget; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * EFFECT_RADIUS * 0.65;
            double x = player.getX() + Math.cos(angle) * distance;
            double y = player.getEyeY() + random.nextDouble() * 4.0 - 2.0;
            double z = player.getZ() + Math.sin(angle) * distance;
            int blockX = (int) Math.floor(x);
            int blockY = (int) Math.floor(y);
            int blockZ = (int) Math.floor(z);
            ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(
                    level,
                    blockX,
                    blockZ
            );
            if (snapshot == null || !snapshot.contains(blockX & 15, blockY, blockZ & 15)) {
                continue;
            }

            ClientWaterChunkSnapshot.Column column = snapshot.column(blockX & 15, blockZ & 15);
            float currentSpeed = column.currentSpeed();
            float bubbleChance = bubbleProbability(
                    currentSpeed,
                    (float) player.getDeltaMovement().horizontalDistance()
            );
            double velocityX = column.velocityX() * 0.025 + (random.nextDouble() - 0.5) * 0.006;
            double velocityZ = column.velocityZ() * 0.025 + (random.nextDouble() - 0.5) * 0.006;
            if (random.nextFloat() < bubbleChance) {
                level.addParticle(
                        ParticleTypes.BUBBLE,
                        x,
                        y,
                        z,
                        velocityX,
                        0.018 + random.nextDouble() * 0.025,
                        velocityZ
                );
            } else {
                level.addParticle(
                        ParticleTypes.UNDERWATER,
                        x,
                        y,
                        z,
                        velocityX,
                        (random.nextDouble() - 0.5) * 0.004,
                        velocityZ
                );
            }
            emitted++;
        }
    }

    // Spray is sampled from actual visible columns. Weather supplies ocean
    // breaking energy, while canonical current and wet/dry neighbors cover fast
    // rivers and shore wash without scanning blocks or generating new meshes.
    private static void emitSurfaceSpray(
            ClientLevel level,
            LocalPlayer player,
            RandomSource random,
            int budget
    ) {
        OceanSeaState.Sample sea = ClientOceanSeaState.sampleAt(
                level, player.getX(), player.getZ());
        int emitted = 0;
        int attempts = attemptBudget(budget);
        for (int attempt = 0; attempt < attempts && emitted < budget; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * EFFECT_RADIUS;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);
            ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(
                    level,
                    blockX,
                    blockZ
            );
            if (snapshot == null) {
                continue;
            }
            ClientWaterChunkSnapshot.Column column = snapshot.column(blockX & 15, blockZ & 15);
            if (!column.wet() || column.surfaceCovered()) {
                continue;
            }

            float shore = shorelineFactor(level, blockX, blockZ);
            float intensity = surfaceSprayIntensity(
                    sea.strength(),
                    sea.breakingStrength(),
                    column.oceanWeight() / 255.0f,
                    column.currentSpeed(),
                    shore
            );
            if (random.nextFloat() >= intensity) {
                continue;
            }

            float surfaceY = ClientWaterImmersion.visibleSurfaceHeight(
                    level, column, x, z, 0.0f);
            if (!Float.isFinite(surfaceY)) {
                continue;
            }
            if (Math.abs(surfaceY - player.getY()) > 12.0) {
                continue;
            }
            double velocityX = sea.windDirectionX() * intensity * 0.055
                    + column.velocityX() * 0.035;
            double velocityZ = sea.windDirectionZ() * intensity * 0.055
                    + column.velocityZ() * 0.035;
            level.addParticle(
                    ParticleTypes.SPLASH,
                    x,
                    surfaceY + 0.03,
                    z,
                    velocityX,
                    0.05 + intensity * 0.16 + random.nextDouble() * 0.04,
                    velocityZ
            );
            emitted++;
        }
    }

    private static float shorelineFactor(ClientLevel level, int blockX, int blockZ) {
        int known = 0;
        int dry = 0;
        int[] offsets = {-1, 0, 1, 0, -1};
        for (int index = 0; index < 4; index++) {
            int sampleX = blockX + offsets[index];
            int sampleZ = blockZ + offsets[index + 1];
            ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(
                    level,
                    sampleX,
                    sampleZ
            );
            if (snapshot == null) {
                continue;
            }
            known++;
            if (!snapshot.column(sampleX & 15, sampleZ & 15).wet()) {
                dry++;
            }
        }
        return known == 0 ? 0.0f : dry / (float) known;
    }

    static boolean shouldEmitAt(long gameTime, int intervalTicks) {
        return intervalTicks > 0 && Math.floorMod(gameTime, intervalTicks) == 0;
    }

    static int attemptBudget(int particleBudget) {
        return Math.max(0, particleBudget) * MAX_ATTEMPTS_PER_PARTICLE;
    }

    static float bubbleProbability(float currentSpeed, float playerSpeed) {
        float energy = clamp(currentSpeed / 1.2f + playerSpeed / 0.65f, 0.0f, 1.0f);
        return 0.08f + energy * 0.42f;
    }

    static float surfaceSprayIntensity(
            float seaStrength,
            float breakingStrength,
            float oceanWeight,
            float currentSpeed,
            float shoreline
    ) {
        float sea = clamp(seaStrength, 0.0f, 1.0f);
        float shore = clamp(shoreline, 0.0f, 1.0f);
        float ocean = clamp(oceanWeight, 0.0f, 1.0f);
        float windDriven = ocean
                * smoothStep(0.24f, 0.82f, breakingStrength)
                * (0.24f + shore * 0.56f + sea * 0.20f);
        float currentDriven = smoothStep(0.45f, 1.45f, currentSpeed)
                * (0.20f + shore * 0.80f);
        return clamp(Math.max(windDriven, currentDriven), 0.0f, 1.0f);
    }

    private static float smoothStep(float minimum, float maximum, float value) {
        float factor = clamp((value - minimum) / (maximum - minimum), 0.0f, 1.0f);
        return factor * factor * (3.0f - 2.0f * factor);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
