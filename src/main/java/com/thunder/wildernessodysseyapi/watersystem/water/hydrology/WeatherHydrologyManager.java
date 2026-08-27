package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterBody;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterInteractionResult;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterUnits;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Exchanges loaded finite water with sustained rain, thaw, and evaporation.
 *
 * <p>Each deterministic probe represents one loaded chunk-scale catchment.
 * Fractional flux is persisted before a bounded transfer is attempted through
 * the public {@link WaterAccess}; no scan, lookup, or mutation force-loads a
 * chunk, and large oceans remain neutral reservoirs.</p>
 */
public final class WeatherHydrologyManager {

    private static final int SAMPLE_RADIUS_BLOCKS = 48;
    private static final int SURFACE_SEARCH_DEPTH = 8;
    private static final int MAX_TRANSFER_UNITS = 512;

    private WeatherHydrologyManager() {
    }

    /** Runs one configured loaded-only hydrology pass when due. */
    public static void tickLevel(ServerLevel level) {
        if (level == null
                || !WaterSimulationConfig.weatherHydrologyEnabled()
                || !WeatherConfig.dimensionEnabled(level.dimension())) {
            return;
        }
        long gameTime = level.getGameTime();
        int interval = WaterSimulationConfig.hydrologyIntervalTicks();
        if (Math.floorMod(gameTime, interval) != 0L || level.players().isEmpty()) {
            return;
        }

        WaterAccess water = WaterServices.access();
        HydrologySavedData ledger = HydrologySavedData.get(level);
        Set<Long> sampledChunks = new HashSet<>();
        int transfers = 0;
        int maximumTransfers = WaterSimulationConfig.hydrologyMaxTransfersPerTick();
        int probes = WaterSimulationConfig.hydrologyProbesPerPlayer();
        long cycle = Math.floorDiv(gameTime, interval);

        for (var player : level.players()) {
            for (int attempt = 0; attempt < probes; attempt++) {
                long mixed = mix(cycle, player.getUUID().getLeastSignificantBits(), attempt);
                int x = player.getBlockX()
                        + (int) Math.floorMod(mixed, SAMPLE_RADIUS_BLOCKS * 2L + 1L)
                        - SAMPLE_RADIUS_BLOCKS;
                int z = player.getBlockZ()
                        + (int) Math.floorMod(mixed >>> 17, SAMPLE_RADIUS_BLOCKS * 2L + 1L)
                        - SAMPLE_RADIUS_BLOCKS;
                long chunkKey = ChunkPos.asLong(x >> 4, z >> 4);
                if (!sampledChunks.add(chunkKey)) {
                    continue;
                }

                BlockPos surfaceWater = findSurfaceWater(level, water, x, z);
                if (surfaceWater == null) {
                    continue;
                }
                Optional<WaterBody> body = water.getWaterBody(level, surfaceWater);
                if (body.isEmpty()
                        || body.get().kind() == WaterBody.Kind.LARGE_OCEAN
                        || body.get().kind() == WaterBody.Kind.LARGE_COAST) {
                    continue;
                }
                WeatherSample weather = WeatherServices.query().sample(
                        level,
                        surfaceWater.above()
                );
                double flux = WaterCycleFluxModel.fluxUnits(
                        weather,
                        body.get().kind(),
                        WaterSimulationConfig.hydrologyRainUnitsPerProbe(),
                        WaterSimulationConfig.hydrologyEvaporationUnitsPerProbe()
                );
                double balance = ledger.accumulate(
                        chunkKey,
                        surfaceWater,
                        flux,
                        gameTime
                );
                if (transfers >= maximumTransfers
                        || Math.abs(balance) < WaterSimulationConfig.hydrologyMinTransferUnits()) {
                    continue;
                }

                long requested = Math.min(
                        MAX_TRANSFER_UNITS,
                        Math.min(WaterUnits.UNITS_PER_BLOCK, (long) Math.floor(Math.abs(balance)))
                );
                if (requested <= 0L) {
                    continue;
                }
                WaterInteractionResult result;
                long signedTransfer;
                if (balance > 0.0) {
                    BlockPos target = additionTarget(level, water, surfaceWater);
                    if (target == null) {
                        continue;
                    }
                    result = water.addWater(level, target, requested);
                    signedTransfer = result.transferredUnits();
                } else {
                    result = water.removeWater(level, surfaceWater, requested);
                    signedTransfer = -result.transferredUnits();
                }
                if (result.successful()) {
                    ledger.consume(chunkKey, signedTransfer);
                    transfers++;
                }
            }
        }
    }

    private static BlockPos findSurfaceWater(
            ServerLevel level,
            WaterAccess water,
            int blockX,
            int blockZ
    ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
        if (chunk == null) {
            return null;
        }
        int topY = chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE,
                blockX & 15,
                blockZ & 15
        ) - 1;
        int minimumY = Math.max(level.getMinBuildHeight(), topY - SURFACE_SEARCH_DEPTH);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(blockX, topY, blockZ);
        for (int y = Math.min(level.getMaxBuildHeight() - 1, topY); y >= minimumY; y--) {
            cursor.setY(y);
            if (water.isWaterAt(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static BlockPos additionTarget(
            ServerLevel level,
            WaterAccess water,
            BlockPos surfaceWater
    ) {
        long existing = water.getWaterUnits(level, surfaceWater);
        if (existing > 0L && existing < WaterUnits.UNITS_PER_BLOCK
                && water.canAddWater(level, surfaceWater)) {
            return surfaceWater;
        }
        BlockPos above = surfaceWater.above();
        return water.canAddWater(level, above) ? above : null;
    }

    private static long mix(long cycle, long playerBits, int attempt) {
        long value = cycle * 0x9E3779B97F4A7C15L
                ^ playerBits
                ^ attempt * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
