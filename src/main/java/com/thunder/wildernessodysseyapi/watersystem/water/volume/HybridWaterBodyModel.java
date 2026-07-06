package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Derives cheap large-body water samples for the central water authority.
 *
 * <p>This is the bridge between Unreal-style surface water and real volumetric
 * gameplay. Large oceans, lakes, rivers, and ponds are represented as bounded
 * columns with a base surface, sampled depth, estimated volume, flow direction,
 * tide/wave profile, and optional local sparse-cell overrides. It deliberately
 * does not tick every internal water block. Detailed cell simulation remains in
 * {@link WaterVolumeChunk} and is used for buckets, flooding, channels, and
 * disturbed local regions.</p>
 */
final class HybridWaterBodyModel {

    private static final float TICKS_PER_SECOND = 20.0f;
    private static final float VISUAL_TIDE_SCALE = 0.18f;
    private static final int SURFACE_SCAN_ABOVE = 6;
    private static final int SURFACE_SCAN_BELOW_SEA_LEVEL = 48;
    private static final int MAX_DEPTH_SCAN = 96;

    private HybridWaterBodyModel() {
    }

    /**
     * Samples large-body volume occupying one block position.
     *
     * <p>The method only trusts water already owned by Wilderness authority as a
     * surface anchor. Pending vanilla water remains conversion input and never
     * becomes a fallback body.</p>
     */
    static LargeBodyCell sampleCell(Level level, BlockPos pos) {
        SurfaceColumn column = findSurfaceColumn(level, pos.getX(), pos.getZ(), pos.getY());
        if (!column.valid()
                || pos.getY() > column.surfaceBlockY()
                || pos.getY() <= column.floorY()) {
            return LargeBodyCell.INVALID;
        }

        int amount = pos.getY() == column.surfaceBlockY()
                ? Math.max(1, Math.min(WaterVolumeChunk.UNITS_PER_BLOCK,
                Math.round(column.baseSurfaceFill() * WaterVolumeChunk.UNITS_PER_BLOCK)))
                : WaterVolumeChunk.UNITS_PER_BLOCK;
        float fillFraction = amount / (float) WaterVolumeChunk.UNITS_PER_BLOCK;
        return new LargeBodyCell(
                true,
                column,
                amount,
                fillFraction,
                pos.getY() == column.surfaceBlockY()
                        ? column.baseSurfaceFill()
                        : 1.0f
        );
    }

    /**
     * Samples the animated surface for one loaded water column.
     *
     * <p>The returned height combines the base body level, tide, waves, and a
     * small local disturbance from mobile/canonical volume. Renderers and
     * gameplay can share this without each reinventing the water equation.</p>
     */
    static SurfaceSample sampleSurface(Level level, double x, double z, float partialTick) {
        BlockPos columnPos = BlockPos.containing(x, level.getSeaLevel(), z);
        SurfaceColumn column = findSurfaceColumn(level, columnPos.getX(), columnPos.getZ(), level.getSeaLevel());
        if (!column.valid()) {
            return SurfaceSample.INVALID;
        }

        float worldX = (float) x;
        float worldZ = (float) z;
        WaterBodyClassifier.WaterType type = column.waterType();
        float tideOffset = type == WaterBodyClassifier.WaterType.OCEAN
                ? TideSystem.getTideOffset(level) * VISUAL_TIDE_SCALE
                : 0.0f;
        WaveSurfaceSample wave = sampleWave(level, worldX, worldZ, type, partialTick);
        float localDisturbance = sampleLocalDisturbance(level, x, column.baseSurfaceHeight(), z);
        float surfaceHeight = column.baseSurfaceHeight() + tideOffset + wave.height() + localDisturbance;
        float[] flow = sampleFlow(level, type, wave);

        return new SurfaceSample(
                true,
                column,
                surfaceHeight,
                tideOffset,
                wave.height(),
                localDisturbance,
                flow[0],
                flow[1],
                wave
        );
    }

    private static SurfaceColumn findSurfaceColumn(Level level, int x, int z, int referenceY) {
        BlockPos chunkProbe = new BlockPos(x, level.getSeaLevel(), z);
        if (level.isOutsideBuildHeight(chunkProbe) || !level.hasChunkAt(chunkProbe)) {
            return SurfaceColumn.INVALID;
        }

        int heightY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        int terrainHash = terrainHash(level, x, z, heightY);
        long cacheKey = columnKey(x, z);
        if (level instanceof ServerLevel serverLevel) {
            HybridWaterBodyModel.SurfaceColumn cached =
                    LargeWaterBodySavedData.get(serverLevel).getColumn(cacheKey, terrainHash);
            if (cached != null) {
                return cached;
            }
        }

        int startY = Math.min(
                level.getMaxBuildHeight() - 1,
                Math.max(Math.max(referenceY, heightY), level.getSeaLevel()) + SURFACE_SCAN_ABOVE
        );
        int stopY = Math.max(
                level.getMinBuildHeight(),
                Math.min(referenceY, level.getSeaLevel() - SURFACE_SCAN_BELOW_SEA_LEVEL)
        );

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = startY; y >= stopY; y--) {
            cursor.set(x, y, z);
            if (!level.hasChunkAt(cursor)) {
                return SurfaceColumn.INVALID;
            }
            WildernessWaterAuthority.CellAuthority cell = WildernessWaterAuthority.sampleCellOnly(level, cursor);
            if (isLargeBodySurfaceAnchor(cell)) {
                int floorY = findFloorY(level, x, y, z);
                if (floorY >= y) {
                    return SurfaceColumn.INVALID;
                }
                WaterBodyClassifier.WaterType type = classifyWaterType(level, cursor);
                float baseSurfaceFill = Math.max(0.05f, Math.min(1.0f, cell.surfaceFillHeight()));
                float baseSurfaceHeight = y + baseSurfaceFill;
                float depth = Math.max(0.0f, baseSurfaceHeight - (floorY + 1.0f));
                boolean shoreline = isShorelineColumn(level, x, y, z);
                long estimatedVolume = Math.max(0L, Math.round(depth * 16.0f * 16.0f
                        * WaterVolumeChunk.UNITS_PER_BLOCK));
                SurfaceColumn sampled = new SurfaceColumn(
                        true,
                        x >> 4,
                        z >> 4,
                        x & ~15,
                        (x & ~15) + 15,
                        z & ~15,
                        (z & ~15) + 15,
                        y,
                        baseSurfaceFill,
                        baseSurfaceHeight,
                        floorY,
                        depth,
                        estimatedVolume,
                        shoreline,
                        type
                );
                if (level instanceof ServerLevel serverLevel) {
                    LargeWaterBodySavedData.get(serverLevel).putColumn(cacheKey, terrainHash, sampled);
                }
                return sampled;
            }
        }
        return SurfaceColumn.INVALID;
    }

    private static long columnKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static int terrainHash(Level level, int x, int z, int heightY) {
        int hash = 17;
        hash = 31 * hash + heightY;
        hash = 31 * hash + level.getSeaLevel();
        hash = 31 * hash + level.dimension().location().hashCode();
        BlockPos probe = new BlockPos(x, heightY, z);
        if (!level.isOutsideBuildHeight(probe) && level.hasChunkAt(probe)) {
            hash = 31 * hash + level.getBlockState(probe).hashCode();
        }
        return hash;
    }

    private static boolean isLargeBodySurfaceAnchor(WildernessWaterAuthority.CellAuthority cell) {
        return cell.water()
                && cell.authorityOwned()
                && !cell.hostedWater()
                && cell.fillFraction() >= 0.75f
                && (cell.imported()
                || cell.source() == WildernessWaterAuthority.WaterSource.WILDERNESS_PROJECTION);
    }

    private static int findFloorY(Level level, int x, int surfaceY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), surfaceY - MAX_DEPTH_SCAN);
        for (int y = surfaceY - 1; y >= minY; y--) {
            cursor.set(x, y, z);
            if (!level.hasChunkAt(cursor)) {
                break;
            }
            BlockState state = level.getBlockState(cursor);
            if (!state.getCollisionShape(level, cursor).isEmpty()) {
                return y;
            }
        }
        return minY;
    }

    private static boolean isShorelineColumn(Level level, int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[][] offsets = {
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };
        for (int[] offset : offsets) {
            cursor.set(x + offset[0], y, z + offset[1]);
            if (!level.hasChunkAt(cursor)) {
                return true;
            }
            WildernessWaterAuthority.CellAuthority neighbour = WildernessWaterAuthority.sampleCellOnly(level, cursor);
            if (!neighbour.water() || !neighbour.authorityOwned()) {
                return true;
            }
        }
        return false;
    }

    private static WaterBodyClassifier.WaterType classifyWaterType(Level level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        if (biomeHolder.is(BiomeTags.IS_OCEAN)
                || biomeHolder.is(BiomeTags.IS_DEEP_OCEAN)
                || biomeHolder.is(BiomeTags.IS_BEACH)) {
            return WaterBodyClassifier.WaterType.OCEAN;
        }
        if (biomeHolder.is(BiomeTags.IS_RIVER)) {
            return WaterBodyClassifier.WaterType.RIVER;
        }
        return WaterBodyClassifier.WaterType.POND;
    }

    private static WaveSurfaceSample sampleWave(
            Level level,
            float worldX,
            float worldZ,
            WaterBodyClassifier.WaterType type,
            float partialTick
    ) {
        GerstnerWaveProfile profile = profileFor(type);
        WaveSpectrumState spectrum = type == WaterBodyClassifier.WaterType.OCEAN
                ? OceanSeaState.sample(level, partialTick).spectrum()
                : WaveSpectrumState.NEUTRAL;
        float timeSeconds = (level.getGameTime() + partialTick) / TICKS_PER_SECOND;
        return profile.sampleAt(worldX, worldZ, timeSeconds, profile.waveCount, spectrum);
    }

    private static GerstnerWaveProfile profileFor(WaterBodyClassifier.WaterType type) {
        return switch (type) {
            case OCEAN -> GerstnerWaveProfile.OCEAN;
            case RIVER -> GerstnerWaveProfile.RIVER;
            case POND -> GerstnerWaveProfile.POND;
        };
    }

    private static float sampleLocalDisturbance(Level level, double x, float surfaceY, double z) {
        SPHSimulationManager.MobileWaterSample mobile = SPHSimulationManager.get().sampleAt(
                level,
                x,
                surfaceY,
                z
        );
        float mobileRipple = mobile.wet()
                ? Math.max(-0.15f, Math.min(0.15f, mobile.velocityY() * 0.018f))
                : 0.0f;
        WaterVolumeChunk.WaterCell localCell = CanonicalWater.get(
                level,
                BlockPos.containing(x, surfaceY, z)
        );
        float flowRipple = Math.max(-0.08f, Math.min(0.08f, localCell.velocityY() * 0.01f));
        return mobileRipple + flowRipple;
    }

    private static float[] sampleFlow(Level level, WaterBodyClassifier.WaterType type, WaveSurfaceSample wave) {
        float flowX = wave.velocityX();
        float flowZ = wave.velocityZ();
        if (type == WaterBodyClassifier.WaterType.OCEAN) {
            float tideRate = TideSystem.getTideRate(level);
            float[] tideDirection = TideSystem.getTidalCurrentDirection(level);
            flowX += tideDirection[0] * tideRate;
            flowZ += tideDirection[1] * tideRate;
        }
        return new float[]{flowX, flowZ};
    }

    /** Large-body occupancy for one block position. */
    record LargeBodyCell(
            boolean valid,
            SurfaceColumn column,
            int amountUnits,
            float fillFraction,
            float surfaceFillHeight
    ) {
        static final LargeBodyCell INVALID = new LargeBodyCell(
                false,
                SurfaceColumn.INVALID,
                0,
                0.0f,
                0.0f
        );
    }

    /** Surface and metadata for one large water-body column. */
    record SurfaceSample(
            boolean valid,
            SurfaceColumn column,
            float surfaceHeight,
            float tideOffset,
            float waveHeight,
            float localDisturbance,
            float flowX,
            float flowZ,
            WaveSurfaceSample wave
    ) {
        static final SurfaceSample INVALID = new SurfaceSample(
                false,
                SurfaceColumn.INVALID,
                Float.NaN,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                WaveSurfaceSample.flat()
        );
    }

    /** Derived high-level volume body data for a loaded chunk column. */
    record SurfaceColumn(
            boolean valid,
            int chunkX,
            int chunkZ,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int surfaceBlockY,
            float baseSurfaceFill,
            float baseSurfaceHeight,
            int floorY,
            float depth,
            long estimatedVolumeUnits,
            boolean shoreline,
            WaterBodyClassifier.WaterType waterType
    ) {
        static final SurfaceColumn INVALID = new SurfaceColumn(
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0f,
                Float.NaN,
                0,
                0.0f,
                0L,
                false,
                WaterBodyClassifier.WaterType.POND
        );
    }
}
