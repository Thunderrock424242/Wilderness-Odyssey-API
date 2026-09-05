package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedServices;
import com.thunder.wildernessodysseyapi.watersystem.water.environment.WaterEnvironmentState;
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
    private static final int[][] CARDINAL_OFFSETS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };
    private static final ThreadLocal<MutableShorelineDirection> SHORELINE_DIRECTION_SCRATCH =
            ThreadLocal.withInitial(MutableShorelineDirection::new);
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
        SurfaceColumn column = findSurfaceColumn(
                level,
                pos.getX(),
                pos.getZ(),
                SHORELINE_DIRECTION_SCRATCH.get()
        );
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
        MutableShorelineDirection shoreline = SHORELINE_DIRECTION_SCRATCH.get();
        SurfaceColumn column = findSurfaceColumn(
                level,
                columnPos.getX(),
                columnPos.getZ(),
                shoreline
        );
        if (!column.valid()) {
            return SurfaceSample.INVALID;
        }
        float shoreNormalX = shoreline.normalX;
        float shoreNormalZ = shoreline.normalZ;

        WaterBodyClassifier.WaterType type = column.waterType();
        float requestedTideOffset = WaterBodyClassifier.isOceanic(type)
                ? TideSystem.getTideOffset(level) * VISUAL_TIDE_SCALE
                : 0.0f;
        BlockPos surfacePosition = BlockPos.containing(x, column.baseSurfaceHeight(), z);
        WatershedConditions watershed = WatershedServices.conditions(level, surfacePosition);
        WatershedLocalFlow localFlow = WatershedServices.localFlow(level, surfacePosition);
        WaterVolumeChunk.WaterCell localCell = CanonicalWater.get(level, surfacePosition);
        float canonicalCurrentX = localCell.velocityX() + localFlow.currentX();
        float canonicalCurrentZ = localCell.velocityZ() + localFlow.currentZ();
        OceanSeaState.Sample seaState = type == WaterBodyClassifier.WaterType.RIVER
                ? OceanSeaState.CALM
                : OceanSeaState.sampleAt(level, x, z, partialTick);
        WaterEnvironmentState environment = WaterEnvironmentState.derive(
                type,
                seaState,
                level.getRainLevel(partialTick),
                requestedTideOffset,
                WaterBodyClassifier.isOceanic(type) ? TideSystem.getTideRate(level) : 0.0f,
                canonicalCurrentX,
                canonicalCurrentZ,
                column.depth(),
                column.estimatedVolumeUnits(),
                column.shoreline()
        );
        float tideOffset = environment.tideHeight();
        WaveSurfaceSample wave = sampleWave(
                level,
                x,
                z,
                type,
                partialTick,
                canonicalCurrentX,
                canonicalCurrentZ,
                environment.waveSpectrum(),
                column.depth()
        );
        float localDisturbance = sampleLocalDisturbance(
                level,
                x,
                column.baseSurfaceHeight(),
                z,
                localCell
        );
        float surfaceHeight = column.baseSurfaceHeight()
                + watershed.waterLevelOffset()
                + tideOffset
                + wave.height()
                + localDisturbance;
        float[] flow = sampleFlow(
                level,
                type,
                wave,
                canonicalCurrentX,
                canonicalCurrentZ,
                shoreNormalX,
                shoreNormalZ
        );

        return new SurfaceSample(
                true,
                column,
                surfaceHeight,
                tideOffset,
                wave.height(),
                localDisturbance,
                flow[0],
                flow[1],
                wave,
                environment
        );
    }

    private static SurfaceColumn findSurfaceColumn(
            Level level,
            int x,
            int z,
            MutableShorelineDirection shorelineDirection
    ) {
        if (shorelineDirection != null) {
            shorelineDirection.clear();
        }
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
        WaterVolumeChunk runtimeVolume = chunk.getExistingData(ModAttachments.WATER_VOLUME).orElse(null);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && runtimeVolume != null) {
            floorY = com.thunder.wildernessodysseyapi.watersystem.water.surface.WaterDepthSampler.floor(
                    serverLevel, x, z, surfaceY, floorY, runtimeVolume.revision());
        }
        float baseSurfaceFill = Math.max(0.05f, Math.min(1.0f, cell.surfaceFillHeight()));
        float baseSurfaceHeight = surfaceY + baseSurfaceFill;
        float depth = Math.max(0.0f, baseSurfaceHeight - (floorY + 1.0f));
        long estimatedVolume = 0L;
        for (GeneratedWaterChunk.WaterSpan span : spans) {
            estimatedVolume += (long) (span.topY() - span.bottomY() + 1)
                    * span.amountUnits();
        }
        WaterBodyClassifier.WaterType type = classifyWaterType(topSpan.cell().bodyType());
        boolean shoreline = sampleShorelineDirection(
                level,
                x,
                surfaceY,
                z,
                shorelineDirection
        );
        if (shoreline && type == WaterBodyClassifier.WaterType.OCEAN) {
            type = WaterBodyClassifier.WaterType.COAST;
        }
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
                shoreline,
                type
        );
    }

    private static boolean sampleShorelineDirection(
            Level level,
            int x,
            int y,
            int z,
            MutableShorelineDirection result
    ) {
        BlockPos.MutableBlockPos cursor = result == null
                ? new BlockPos.MutableBlockPos()
                : result.cursor;
        boolean shoreline = false;
        float normalX = 0.0f;
        float normalZ = 0.0f;
        for (int[] offset : CARDINAL_OFFSETS) {
            cursor.set(x + offset[0], y, z + offset[1]);
            if (!level.hasChunkAt(cursor)) {
                shoreline = true;
                continue;
            }
            WildernessWaterAuthority.CellAuthority neighbour = WildernessWaterAuthority.sampleCellOnly(level, cursor);
            if (!neighbour.water() || !neighbour.authorityOwned()) {
                shoreline = true;
                normalX += offset[0];
                normalZ += offset[1];
            }
        }
        float lengthSquared = normalX * normalX + normalZ * normalZ;
        if (lengthSquared > 1.0e-8f) {
            float inverseLength = 1.0f / (float) Math.sqrt(lengthSquared);
            normalX *= inverseLength;
            normalZ *= inverseLength;
        }
        if (result != null) {
            result.normalX = normalX;
            result.normalZ = normalZ;
        }
        return shoreline;
    }

    private static WaterBodyClassifier.WaterType classifyWaterType(GeneratedWaterChunk.BodyType bodyType) {
        return switch (bodyType) {
            case OCEAN -> WaterBodyClassifier.WaterType.OCEAN;
            case RIVER -> WaterBodyClassifier.WaterType.RIVER;
            case LAKE -> WaterBodyClassifier.WaterType.LAKE;
            case AQUIFER, SPRING -> WaterBodyClassifier.WaterType.POND;
        };
    }

    private static WaveSurfaceSample sampleWave(
            Level level,
            double worldX,
            double worldZ,
            WaterBodyClassifier.WaterType type,
            float partialTick,
            float canonicalCurrentX,
            float canonicalCurrentZ,
            WaveSpectrumState spectrum,
            float waterDepth
    ) {
        GerstnerWaveProfile profile = profileFor(type);
        double timeSeconds = (level.getGameTime() + (double) partialTick) / TICKS_PER_SECOND;
        return profile.sampleAt(
                worldX,
                worldZ,
                timeSeconds,
                profile.waveCount,
                spectrum,
                type == WaterBodyClassifier.WaterType.RIVER ? canonicalCurrentX : 0.0f,
                type == WaterBodyClassifier.WaterType.RIVER ? canonicalCurrentZ : 0.0f,
                waterDepth
        );
    }

    private static GerstnerWaveProfile profileFor(WaterBodyClassifier.WaterType type) {
        return switch (type) {
            case OCEAN -> GerstnerWaveProfile.OCEAN;
            case RIVER -> GerstnerWaveProfile.RIVER;
            case POND -> GerstnerWaveProfile.POND;
            case COAST -> GerstnerWaveProfile.COAST;
            case LAKE -> GerstnerWaveProfile.LAKE;
        };
    }

    private static float sampleLocalDisturbance(
            Level level,
            double x,
            float surfaceY,
            double z,
            WaterVolumeChunk.WaterCell localCell
    ) {
        SPHSimulationManager.MobileWaterSample mobile = SPHSimulationManager.get().sampleAt(
                level,
                x,
                surfaceY,
                z
        );
        float mobileRipple = mobile.wet()
                ? Math.max(-0.15f, Math.min(0.15f, mobile.velocityY() * 0.018f))
                : 0.0f;
        float flowRipple = Math.max(-0.08f, Math.min(0.08f, localCell.velocityY() * 0.01f));
        return mobileRipple + flowRipple;
    }

    private static float[] sampleFlow(
            Level level,
            WaterBodyClassifier.WaterType type,
            WaveSurfaceSample wave,
            float canonicalCurrentX,
            float canonicalCurrentZ,
            float shoreNormalX,
            float shoreNormalZ
    ) {
        float[] flow = combineFlow(
                type,
                wave,
                canonicalCurrentX,
                canonicalCurrentZ,
                0.0f,
                0.0f,
                0.0f
        );
        if (WaterBodyClassifier.isOceanic(type)) {
            TideSystem.addTidalCurrent(
                    TideSystem.getTideRate(level),
                    shoreNormalX,
                    shoreNormalZ,
                    flow
            );
        }
        return flow;
    }

    static float[] combineFlow(
            WaterBodyClassifier.WaterType type,
            WaveSurfaceSample wave,
            float canonicalCurrentX,
            float canonicalCurrentZ,
            float tideRate,
            float tideDirectionX,
            float tideDirectionZ
    ) {
        WaveSurfaceSample safeWave = wave == null ? WaveSurfaceSample.flat() : wave;
        float flowX = finiteOrZero(canonicalCurrentX) + safeWave.velocityX();
        float flowZ = finiteOrZero(canonicalCurrentZ) + safeWave.velocityZ();
        if (WaterBodyClassifier.isOceanic(type)) {
            flowX += finiteOrZero(tideDirectionX) * finiteOrZero(tideRate);
            flowZ += finiteOrZero(tideDirectionZ) * finiteOrZero(tideRate);
        }
        return new float[]{flowX, flowZ};
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
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
            WaveSurfaceSample wave,
            WaterEnvironmentState environment
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
                WaveSurfaceSample.flat(),
                WaterEnvironmentState.CALM_POND
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

    private static final class MutableShorelineDirection {
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private float normalX;
        private float normalZ;

        private void clear() {
            normalX = 0.0f;
            normalZ = 0.0f;
        }
    }
}
