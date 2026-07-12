package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

/**
 * Derives cheap surface samples from compact generated-water metadata.
 *
 * <p>This is the bridge between Unreal-style surface water and real volumetric
 * gameplay. Oceans, lakes, rivers, aquifers, and springs are represented as
 * generated spans with a base surface, exact baseline depth, flow direction,
 * tide/wave profile, and optional local sparse-cell overrides. It deliberately
 * does not tick every internal water block. Detailed cell simulation remains in
 * {@link WaterVolumeChunk} and is used for buckets, flooding, channels, and
 * disturbed local regions.</p>
 */
final class HybridWaterBodyModel {

    private static final float TICKS_PER_SECOND = 20.0f;
    private static final float VISUAL_TIDE_SCALE = 0.18f;
    private HybridWaterBodyModel() {
    }

    /**
     * Samples generated-body volume occupying one block position.
     */
    static LargeBodyCell sampleCell(Level level, BlockPos pos) {
        WildernessWaterAuthority.CellAuthority authority = WildernessWaterAuthority.sampleCellOnly(level, pos);
        if (!authority.water() || !authority.authorityOwned()) {
            return LargeBodyCell.INVALID;
        }
        SurfaceColumn column = findSurfaceColumn(level, pos.getX(), pos.getZ());
        if (!column.valid()
                || pos.getY() > column.surfaceBlockY()
                || pos.getY() <= column.floorY()) {
            return LargeBodyCell.INVALID;
        }

        int amount = authority.volumeUnits();
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
        SurfaceColumn column = findSurfaceColumn(level, columnPos.getX(), columnPos.getZ());
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

    private static SurfaceColumn findSurfaceColumn(Level level, int x, int z) {
        BlockPos chunkProbe = new BlockPos(x, level.getSeaLevel(), z);
        if (level.isOutsideBuildHeight(chunkProbe) || !level.hasChunkAt(chunkProbe)) {
            return SurfaceColumn.INVALID;
        }
        LevelChunk chunk = level.getChunkAt(chunkProbe);
        GeneratedWaterChunk generated = chunk.getExistingData(ModAttachments.GENERATED_WATER).orElse(null);
        if (generated == null) {
            return SurfaceColumn.INVALID;
        }
        int column = (x & 15) | ((z & 15) << 4);
        List<GeneratedWaterChunk.WaterSpan> spans = generated.snapshot().spansForColumn(column);
        if (spans.isEmpty()) {
            return SurfaceColumn.INVALID;
        }
        GeneratedWaterChunk.WaterSpan topSpan = spans.get(spans.size() - 1);
        int surfaceY = topSpan.topY();
        BlockPos surfacePos = new BlockPos(x, surfaceY, z);
        WildernessWaterAuthority.CellAuthority cell = WildernessWaterAuthority.sampleCellOnly(level, surfacePos);
        if (!cell.water() || !cell.authorityOwned() || cell.hostedWater()) {
            return SurfaceColumn.INVALID;
        }

        int floorY = generated.snapshot().floorY(x & 15, z & 15);
        float baseSurfaceFill = Math.max(0.05f, Math.min(1.0f, cell.surfaceFillHeight()));
        float baseSurfaceHeight = surfaceY + baseSurfaceFill;
        float depth = Math.max(0.0f, baseSurfaceHeight - (floorY + 1.0f));
        long estimatedVolume = spans.stream()
                .mapToLong(span -> (long) (span.topY() - span.bottomY() + 1) * span.amountUnits())
                .sum();
        WaterBodyClassifier.WaterType type = classifyWaterType(topSpan.cell().bodyType());
        return new SurfaceColumn(
                true,
                x >> 4,
                z >> 4,
                x & ~15,
                (x & ~15) + 15,
                z & ~15,
                (z & ~15) + 15,
                surfaceY,
                baseSurfaceFill,
                baseSurfaceHeight,
                floorY,
                depth,
                estimatedVolume,
                isShorelineColumn(level, x, surfaceY, z),
                type
        );
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

    private static WaterBodyClassifier.WaterType classifyWaterType(GeneratedWaterChunk.BodyType bodyType) {
        return switch (bodyType) {
            case OCEAN -> WaterBodyClassifier.WaterType.OCEAN;
            case RIVER -> WaterBodyClassifier.WaterType.RIVER;
            case LAKE, AQUIFER, SPRING -> WaterBodyClassifier.WaterType.POND;
        };
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
