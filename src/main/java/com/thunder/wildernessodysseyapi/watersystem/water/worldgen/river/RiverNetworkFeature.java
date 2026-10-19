package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Predicate;

/**
 * Generation-only entry point. The first FEATURES invocation preflights and writes
 * a whole fixed 2x2-chunk tile inside Minecraft's declared one-chunk write radius.
 * It never requests additional terrain or edits a completed chunk.
 */
public final class RiverNetworkFeature extends Feature<NoneFeatureConfiguration> {
    // Fixed stripes serialize same-tile invocations without a growing world cache.
    private static final Object[] LOCKS = new Object[64];
    static { for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new Object(); }

    public RiverNetworkFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!RiverWorldgenConfig.enabled()) return false;
        var settings = RiverWorldgenConfig.snapshot();
        if (!supports(context.level(), context.chunkGenerator())) return false;
        WorldGenRegion region = (WorldGenRegion) context.level();
        int chunkX = Math.floorDiv(context.origin().getX() >> 4, 2) * 2;
        int chunkZ = Math.floorDiv(context.origin().getZ() >> 4, 2) * 2;
        int deltaX = chunkX - region.getCenter().x;
        int deltaZ = chunkZ - region.getCenter().z;
        if (deltaX < -1 || deltaX > 0 || deltaZ < -1 || deltaZ > 0) return false;
        // All four are guaranteed CARVERS dependencies of this FEATURES call.
        // WorldGenRegion throws for undeclared dependencies, even require=false.
        ChunkAccess[] chunks = new ChunkAccess[4];
        for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
            chunks[x + z * 2] = region.getChunk(chunkX + x, chunkZ + z, ChunkStatus.CARVERS);
        }
        return placeTile(region.getSeed(), chunkX << 4, chunkZ << 4, chunks, settings, region::ensureCanWrite);
    }

    /** The existing glacial carver is superseded only where this generation contract applies. */
    public static boolean supports(WorldGenLevel level, ChunkGenerator generator) {
        return level instanceof WorldGenRegion region && region.getLevel().dimension() == Level.OVERWORLD
                && generator.getClass() == NoiseBasedChunkGenerator.class;
    }

    /**
     * Applies an already admitted tile. Exposed for world-backed generation tests;
     * callers must supply exactly the four new ProtoChunks in spatial order.
     */
    public static boolean placeTile(long seed, int minimumX, int minimumZ, ChunkAccess[] chunks,
                                    RiverGenerationSettings settings, Predicate<BlockPos> canWrite) {
        if (chunks.length != 4 || !settings.enabled() || Math.floorMod(minimumX, 32) != 0
                || Math.floorMod(minimumZ, 32) != 0) return false;
        int stripe = (((minimumX >> 5) * 31) + (minimumZ >> 5)) & (LOCKS.length - 1);
        synchronized (LOCKS[stripe]) {
            RiverGenerationChunk[] captures = new RiverGenerationChunk[4];
            for (int i = 0; i < 4; i++) {
                ChunkAccess chunk = chunks[i];
                if (!(chunk instanceof ProtoChunk) || chunk.isUpgrading()
                        || chunk.getPos().x != (minimumX >> 4) + i % 2
                        || chunk.getPos().z != (minimumZ >> 4) + i / 2
                        || chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) return false;
                captures[i] = chunk.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
                if (captures[i] == null || !captures[i].hasTerrain(settings.fingerprint())) return false;
            }
            var terrain = RiverTileTerrain.create((x, z) -> capture(captures, x, z).height(x & 15, z & 15),
                    (x, z) -> capture(captures, x, z).water(x & 15, z & 15),
                    (x, z) -> capture(captures, x, z).naturalMask(x & 15, z & 15));
            boolean protectedTile = false;
            for (var chunk : chunks) {
                protectedTile |= chunk.getAllStarts().values().stream().anyMatch(start -> start.isValid())
                        || chunk.getAllReferences().values().stream().anyMatch(references -> !references.isEmpty());
            }
            var plan = protectedTile ? RiverCatchmentPlanner.Plan.EMPTY
                    : RiverCatchmentPlanner.plan(seed, minimumX, minimumZ, terrain, settings);
            if (!preflight(plan, chunks, terrain, minimumX, minimumZ, canWrite)) plan = RiverCatchmentPlanner.Plan.EMPTY;
            // No writes occur until every terrain, support and boundary check passed.
            for (var column : plan.columns()) {
                var chunk = chunks[(column.x() >> 4) + (column.z() >> 4) * 2];
                int x = minimumX + column.x();
                int z = minimumZ + column.z();
                var pos = new BlockPos.MutableBlockPos(x, column.floorY(), z);
                BlockState bed = settings.naturalBanks() ? bed(chunk, pos, column.kind()) : chunk.getBlockState(pos);
                store(chunk, pos, bed, column);
                int top = Math.max(terrain.height(column.x(), column.z()), column.topY());
                for (int y = column.floorY() + 1; y <= top; y++) {
                    pos.setY(y);
                    BlockState state = y <= column.topY()
                            ? GenerationWaterStateMapper.map(Blocks.WATER.defaultBlockState())
                            : Blocks.AIR.defaultBlockState();
                    if (y > column.waterY() && y <= column.topY()) {
                        state = state.getFluidState().getType() instanceof FlowingFluid fluid
                                ? fluid.getFlowing(8, true).createLegacyBlock() : state;
                    }
                    store(chunk, pos, state, column);
                }
            }
            for (int i = 0; i < 4; i++) {
                captures[i].complete(plan.basinId(), (i % 2) * 16, (i / 2) * 16, plan.columns());
                chunks[i].setUnsaved(true);
            }
            return !plan.columns().isEmpty();
        }
    }

    private static boolean preflight(RiverCatchmentPlanner.Plan plan, ChunkAccess[] chunks,
            RiverTileTerrain terrain, int minimumX, int minimumZ, Predicate<BlockPos> canWrite) {
        var pos = new BlockPos.MutableBlockPos();
        RiverChannelColumn[] planned = new RiverChannelColumn[1024];
        for (var column : plan.columns()) planned[column.x() + column.z() * 32] = column;
        for (var column : plan.columns()) {
            var chunk = chunks[(column.x() >> 4) + (column.z() >> 4) * 2];
            int top = Math.max(terrain.height(column.x(), column.z()), column.topY()) + 1;
            for (int y = column.floorY() - 1; y <= top; y++) {
                pos.set(minimumX + column.x(), y, minimumZ + column.z());
                if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight() || !canWrite.test(pos)) return false;
                BlockState state = chunk.getBlockState(pos);
                if (y <= terrain.height(column.x(), column.z())) {
                    if (!RiverTerrainCapture.natural(state)) return false;
                } else if (!state.isAir()) return false;
                // Check actual walls too: neighboring features may have altered
                // a captured column before this tile's first invocation.
                if (y > column.floorY() && y <= column.waterY()) {
                    for (int direction = 0; direction < 4; direction++) {
                        int nx = column.x() + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
                        int nz = column.z() + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
                        var next = planned[nx + nz * 32];
                        if (next != null && y > next.floorY() && y <= next.topY()) continue;
                        ChunkAccess neighbor = chunks[(nx >> 4) + (nz >> 4) * 2];
                        if (terrain.water(nx, nz) && !sourceWater(neighbor.getBlockState(
                                new BlockPos(minimumX + nx, terrain.height(nx, nz), minimumZ + nz)))) return false;
                        BlockState wall = neighbor.getBlockState(new BlockPos(minimumX + nx, y, minimumZ + nz));
                        boolean receivingWater = terrain.water(nx, nz) && terrain.height(nx, nz) >= y
                                && sourceWater(wall);
                        if (!receivingWater && !RiverTerrainCapture.natural(wall)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean sourceWater(BlockState state) {
        return (state.is(Blocks.WATER) || GenerationWaterStateMapper.isWildernessWater(state))
                && state.getFluidState().isSource();
    }

    private static RiverGenerationChunk capture(RiverGenerationChunk[] captures, int x, int z) {
        return captures[(x >> 4) + (z >> 4) * 2];
    }

    private static BlockState bed(ChunkAccess chunk, BlockPos pos, RiverChannelColumn.Kind kind) {
        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
        long variation = pos.asLong() ^ (pos.asLong() >>> 23);
        if (biome.is(BiomeTags.IS_BADLANDS)) return Blocks.RED_SAND.defaultBlockState();
        if (biome.value().getBaseTemperature() > 1.0f && biome.value().getModifiedClimateSettings().downfall() < 0.3f) {
            return (variation % 5 == 0 ? Blocks.CLAY : Blocks.SAND).defaultBlockState();
        }
        if (biome.is(BiomeTags.IS_JUNGLE) || biome.is(net.minecraft.world.level.biome.Biomes.SWAMP)
                || biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) {
            return (variation % 4 == 0 ? Blocks.CLAY : Blocks.MUD).defaultBlockState();
        }
        return (kind == RiverChannelColumn.Kind.WATERFALL || biome.is(BiomeTags.IS_MOUNTAIN)
                ? Blocks.STONE : variation % 7 == 0 ? Blocks.CLAY : Blocks.GRAVEL).defaultBlockState();
    }

    private static void store(ChunkAccess chunk, BlockPos pos, BlockState state, RiverChannelColumn column) {
        chunk.setBlockState(pos, state, false);
        GenerationWaterStateMapper.recordStoredState(chunk, pos, state);
        if (GenerationWaterStateMapper.isWildernessWater(state)) {
            var fluid = state.getFluidState();
            var biome = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
            chunk.getData(ModAttachments.GENERATED_WATER).recordCell(pos, GeneratedWaterChunk.Cell.of(
                    fluid.getAmount(), fluid.getValue(FlowingFluid.FALLING),
                    column.kind() == RiverChannelColumn.Kind.LAKE ? GeneratedWaterChunk.BodyType.LAKE : GeneratedWaterChunk.BodyType.RIVER,
                    biome.value().getWaterColor()));
        }
    }
}
