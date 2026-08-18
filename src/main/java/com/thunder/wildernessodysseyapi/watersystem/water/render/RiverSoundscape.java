package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.environment.client.ClientEnvironmentState;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedServices;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Plays sparse vanilla river-water accents from synchronized watershed state. */
public final class RiverSoundscape {

    private static final int[] OFFSETS = {0, 4, -4, 8, -8};
    private static long nextSoundTick;

    private RiverSoundscape() {
    }

    /** Samples a small fixed neighborhood and plays at most one local accent. */
    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || level.getGameTime() < nextSoundTick) {
            return;
        }
        Candidate best = null;
        for (int offsetX : OFFSETS) {
            for (int offsetZ : OFFSETS) {
                if (Math.abs(offsetX) + Math.abs(offsetZ) > 8) {
                    continue;
                }
                int blockX = minecraft.player.getBlockX() + offsetX;
                int blockZ = minecraft.player.getBlockZ() + offsetZ;
                ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(level, blockX, blockZ);
                if (snapshot == null) {
                    continue;
                }
                ClientWaterChunkSnapshot.Column column = snapshot.column(blockX & 15, blockZ & 15);
                if (!column.wet() || column.surfaceCovered()) {
                    continue;
                }
                BlockPos position = new BlockPos(blockX, column.surfaceBlockY(), blockZ);
                WatershedConditions conditions = WatershedServices.conditions(level, position);
                WatershedLocalFlow flow = WatershedServices.localFlow(level, position);
                if (!conditions.hasSurfaceWater()) {
                    continue;
                }
                float rain = (float) ClientWeatherCoordinator.sampleAt(
                        level, position).precipitationIntensity();
                float intensity = RiverSoundscapeModel.intensity(
                        conditions.riverDischarge(),
                        flow.currentStrength(),
                        flow.confluence(),
                        conditions.flooding(),
                        rain
                );
                float sharedWaterActivity = ClientEnvironmentState.current(level)
                        .map(environment -> 0.72F
                                + environment.waterAvailability() * 0.18F
                                + environment.aquaticActivity() * 0.10F)
                        .orElse(1.0F);
                intensity *= sharedWaterActivity;
                if (best == null || intensity > best.intensity) {
                    best = new Candidate(position, intensity);
                }
            }
        }
        if (best == null || best.intensity < 0.16f) {
            nextSoundTick = level.getGameTime() + 20L;
            return;
        }
        level.playLocalSound(
                best.position,
                SoundEvents.WATER_AMBIENT,
                SoundSource.AMBIENT,
                0.05f + best.intensity * 0.18f,
                0.82f + level.getRandom().nextFloat() * 0.22f,
                false
        );
        nextSoundTick = level.getGameTime() + RiverSoundscapeModel.intervalTicks(best.intensity);
    }

    /** Clears cadence state when leaving a client level. */
    public static void clear() {
        nextSoundTick = 0L;
    }

    private record Candidate(BlockPos position, float intensity) {
    }
}
