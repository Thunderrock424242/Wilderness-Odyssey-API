package com.thunder.wildernessodysseyapi.weather.surface;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Applies bounded snow accumulation and freeze/thaw changes in loaded player areas. */
public final class SurfaceWeatheringScheduler {

    /** Samples no more than the configured number of columns around each player. */
    public void tick(
            ServerLevel level,
            long gameTime,
            WeatherAuthority weather,
            WeatherConfig.FeatureSettings settings
    ) {
        if (!settings.surfaceWeatheringEnabled()
                || Math.floorMod(gameTime, settings.surfaceWeatheringIntervalTicks()) != 0L) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            for (int attempt = 0; attempt < settings.surfaceWeatheringAttemptsPerPlayer(); attempt++) {
                long mixed = mix(gameTime, player.getUUID().getLeastSignificantBits(), attempt);
                int x = player.getBlockX() + (int) Math.floorMod(mixed, 97L) - 48;
                int z = player.getBlockZ() + (int) Math.floorMod(mixed >>> 11, 97L) - 48;
                BlockPos probe = new BlockPos(x, player.getBlockY(), z);
                if (!level.hasChunkAt(probe)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                applyColumn(level, new BlockPos(x, y, z), weather.sample(level, new BlockPos(x, y, z)), settings, mixed);
            }
        }
    }

    private static void applyColumn(
            ServerLevel level,
            BlockPos top,
            WeatherSample sample,
            WeatherConfig.FeatureSettings settings,
            long randomBits
    ) {
        SurfaceWeatherState surface = sample.surface();
        BlockState topState = level.getBlockState(top);
        BlockState belowState = level.getBlockState(top.below());
        boolean snowing = sample.precipitationType() == PrecipitationType.SNOW
                && sample.precipitationIntensity() >= 0.18;

        // Accumulation uses normal snow-layer blocks and never forces chunk loads.
        if ((snowing || surface.snowpack() >= 0.42) && sample.temperature() <= 1.0) {
            if (topState.is(Blocks.SNOW)) {
                int layers = topState.getValue(SnowLayerBlock.LAYERS);
                if (layers < settings.maximumSnowLayers() && (randomBits & 3L) == 0L) {
                    level.setBlockAndUpdate(top, topState.setValue(SnowLayerBlock.LAYERS, layers + 1));
                }
            } else if (topState.isAir()) {
                BlockState snow = Blocks.SNOW.defaultBlockState();
                if (snow.canSurvive(level, top)) {
                    level.setBlockAndUpdate(top, snow);
                }
            }
        } else if (topState.is(Blocks.SNOW) && sample.temperature() >= 2.0 && (randomBits & 1L) == 0L) {
            int layers = topState.getValue(SnowLayerBlock.LAYERS);
            level.setBlockAndUpdate(top, layers <= 1
                    ? Blocks.AIR.defaultBlockState()
                    : topState.setValue(SnowLayerBlock.LAYERS, layers - 1));
        }

        // Frosted ice owns its own vanilla melt ticks, giving gradual thawing
        // without permanently replacing lakes or Wilderness water bodies.
        if (surface.frozenFraction() >= 0.58
                && sample.temperature() <= -1.5
                && belowState.getFluidState().is(FluidTags.WATER)
                && belowState.getFluidState().isSource()
                && topState.isAir()) {
            BlockPos water = top.below();
            level.setBlockAndUpdate(water, Blocks.FROSTED_ICE.defaultBlockState());
            level.scheduleTick(water, Blocks.FROSTED_ICE, 80 + (int) Math.floorMod(randomBits, 80L));
        }
    }

    private static long mix(long gameTime, long playerBits, int attempt) {
        long value = gameTime * 0x9E3779B97F4A7C15L ^ playerBits ^ attempt * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 29;
        value *= 0x165667B19E3779F9L;
        return value ^ value >>> 32;
    }
}
