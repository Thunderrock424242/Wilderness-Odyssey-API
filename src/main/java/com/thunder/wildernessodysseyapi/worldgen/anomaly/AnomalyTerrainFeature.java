package com.thunder.wildernessodysseyapi.worldgen.anomaly;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyBlocks;
import com.thunder.wildernessodysseyapi.worldgen.anomaly.config.AnomalyWorldgenConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds restrained reality faults to native terrain during generation, never a runtime terrain simulation.
 * A single origin chunk owns the pass; only natural dry terrain and preflighted empty space can change.
 * Structure-bearing chunks are conservatively vetoed before surface, fracture, debris or island writes.
 */
public final class AnomalyTerrainFeature extends Feature<NoneFeatureConfiguration> {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public AnomalyTerrainFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return placeChunk(context.level(), context.origin(), null, context.level().getSeed());
    }

    /**
     * Operator preview of the bounded terrain pass in an already-available chunk.
     * Does not run native trees, carvers, ores or other biome generation stages.
     */
    public static boolean placeForZone(ServerLevelAccessor level, BlockPos chunkOrigin, AnomalyZone zone, long seed) {
        return placeChunk(level, chunkOrigin, zone, seed);
    }

    private static boolean placeChunk(ServerLevelAccessor level, BlockPos origin, AnomalyZone override, long seed) {
        int minimumX = Math.floorDiv(origin.getX(), 16) * 16;
        int minimumZ = Math.floorDiv(origin.getZ(), 16) * 16;
        ChunkAccess chunk = AnomalyIslandPlacement.availableChunk(level, minimumX >> 4, minimumZ >> 4);
        if (chunk == null || AnomalyIslandPlacement.structureProtected(chunk)) {
            return false;
        }
        double density = AnomalyWorldgenConfig.DISTORTION_DENSITY.get();
        boolean changed = false;
        if (AnomalyWorldgenConfig.ENABLE_SURFACE_DISTORTION.get() && density > 0.0) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    BlockPos top = naturalSurface(level, chunk, minimumX + localX, minimumZ + localZ);
                    if (top == null) {
                        continue;
                    }
                    AnomalyZone zone = zoneAt(level, top, override);
                    if (zone == null || unit(seed, top.getX() >> 1, top.getZ() >> 1, 0xB50A53E49FL)
                            >= density * (0.08 + zone.intensity() * 0.22)) {
                        continue;
                    }
                    changed |= distortColumn(level, chunk, top, zone, seed);
                }
            }
        }
        if (AnomalyWorldgenConfig.ENABLE_DETAILS.get() && density > 0.0) {
            BlockPos formationSurface = naturalSurface(level, chunk, minimumX + 8, minimumZ + 8);
            if (formationSurface != null && zoneAt(level, formationSurface, override) == AnomalyZone.RIFT_CORE
                    && unit(seed, minimumX, minimumZ, 0x583AF613CL) < density * 0.12) {
                changed |= riftFormation(level, chunk, formationSurface, minimumX, minimumZ);
            }
            // Sixteen anchors bound the detail budget regardless of biome strength.
            for (int cellZ = 0; cellZ < 16; cellZ += 4) {
                for (int cellX = 0; cellX < 16; cellX += 4) {
                    BlockPos top = naturalSurface(level, chunk, minimumX + cellX + 2, minimumZ + cellZ + 2);
                    if (top == null) {
                        continue;
                    }
                    AnomalyZone zone = zoneAt(level, top, override);
                    if (zone == null || unit(seed, top.getX(), top.getZ(), 0xC82D4527AL)
                            > density * (0.10 + zone.intensity() * 0.22)) {
                        continue;
                    }
                    double choice = unit(seed, top.getX(), top.getZ(), 0xEB86D391BL);
                    if (zone == AnomalyZone.RIFT_CORE && choice < 0.23) {
                        changed |= fracture(level, chunk, top);
                    } else if (zone.intensity() >= AnomalyZone.FLOATING_FRINGE.intensity() && choice < 0.48) {
                        changed |= suspendedDebris(level, chunk, top, minimumX, minimumZ);
                    } else {
                        changed |= growth(level, chunk, top, minimumX, minimumZ, choice < 0.66, zone);
                    }
                }
            }
        }
        if (AnomalyWorldgenConfig.ENABLE_FLOATING_ISLANDS.get()) {
            int x = minimumX + 3;
            int z = minimumZ + 3;
            int ground = surfaceHeight(level, chunk, x, z);
            AnomalyZone zone = zoneAt(level, new BlockPos(x, ground, z), override);
            double strength = zone == null ? 0.0 : switch (zone) {
                case FOREST, PLAINS -> 0.08;
                case HILLS -> 0.25;
                case FLOATING_FRINGE, RIFT_CORE -> 1.0;
            };
            if (strength > 0.0 && unit(seed, minimumX, minimumZ, 0xD5E38A401L)
                    < AnomalyWorldgenConfig.ISLAND_CHANCE.get() * strength) {
                int highest = ground;
                // Every source template is nine blocks wide; ten includes its air margin.
                for (int dz = 2; dz <= 12; dz++) {
                    for (int dx = 2; dx <= 12; dx++) {
                        highest = Math.max(highest, surfaceHeight(level, chunk, minimumX + dx, minimumZ + dz));
                    }
                }
                int variant = Math.min(2, (int) (unit(seed, minimumX, minimumZ, 0xA495F218CL) * 3));
                BlockPos islandOrigin = new BlockPos(x, highest + 12
                        + (int) (unit(seed, x, z, 0x827316C59L) * 10), z);
                changed |= AnomalyIslandPlacement.place(level, AnomalyIslandPlacement.templates().get(variant), islandOrigin).allowed();
            }
        }
        return changed;
    }

    private static AnomalyZone zoneAt(ServerLevelAccessor level, BlockPos top, AnomalyZone override) {
        return override != null ? override : level.getBiome(top).unwrapKey().flatMap(AnomalyZone::fromBiome).orElse(null);
    }

    private static int surfaceHeight(ServerLevelAccessor level, ChunkAccess chunk, int x, int z) {
        Heightmap.Types type = level instanceof ServerLevel ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
        return chunk.getHeight(type, x & 15, z & 15);
    }

    private static BlockPos naturalSurface(ServerLevelAccessor level, ChunkAccess chunk, int x, int z) {
        int surface = surfaceHeight(level, chunk, x, z);
        // Native spruce canopies can obscure the ground heightmap; inspect at most twelve local cells.
        for (int depth = 0; depth <= 12 && surface - depth >= level.getMinBuildHeight(); depth++) {
            BlockPos pos = new BlockPos(x, surface - depth, z);
            BlockState state = chunk.getBlockState(pos);
            if (natural(state)) {
                return dryWritable(level, chunk, pos) ? pos : null;
            }
            if (!state.isAir() && !state.is(net.minecraft.tags.BlockTags.LEAVES)
                    && !state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.TALL_GRASS)
                    && !state.is(Blocks.FERN) && !state.is(Blocks.LARGE_FERN)) {
                return null;
            }
        }
        return null;
    }

    private static boolean distortColumn(ServerLevelAccessor level, ChunkAccess chunk, BlockPos top, AnomalyZone zone, long seed) {
        // Preserve native tree/plant support and avoid replacing the ground beneath existing content.
        if (!empty(level, chunk, top.above())) {
            return false;
        }
        BlockState existing = chunk.getBlockState(top);
        BlockState replacement;
        if (existing.is(Blocks.GRASS_BLOCK) || existing.is(Blocks.PODZOL) || existing.is(Blocks.MOSS_BLOCK)) {
            replacement = unit(seed, top.getX(), top.getZ(), 0xA17FA312L) < 0.18
                    ? AnomalyBlocks.DISTORTED_MOSS.get().defaultBlockState()
                    : AnomalyBlocks.DISTORTED_GRASS.get().defaultBlockState();
        } else if (existing.is(Blocks.DIRT) || existing.is(Blocks.COARSE_DIRT)) {
            replacement = AnomalyBlocks.DISTORTED_DIRT.get().defaultBlockState();
        } else {
            replacement = (zone == AnomalyZone.HILLS ? AnomalyBlocks.DISTORTED_CLIFF_STONE
                    : AnomalyBlocks.DISTORTED_STONE).get().defaultBlockState();
        }
        List<Change> changes = new ArrayList<>();
        changes.add(new Change(top, replacement));
        for (int depth = 1; depth <= 2; depth++) {
            BlockPos below = top.below(depth);
            if (!dryWritable(level, chunk, below) || !natural(chunk.getBlockState(below))) {
                break;
            }
            BlockState state = chunk.getBlockState(below);
            changes.add(new Change(below, (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                    ? AnomalyBlocks.DISTORTED_DIRT : AnomalyBlocks.DISTORTED_STONE).get().defaultBlockState()));
        }
        return apply(level, changes);
    }

    private static boolean growth(ServerLevelAccessor level, ChunkAccess chunk, BlockPos top,
                                  int minX, int minZ, boolean crystal, AnomalyZone zone) {
        List<Change> changes = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (Math.abs(dx) + Math.abs(dz) > 1) {
                    continue;
                }
                BlockPos ground = top.offset(dx, 0, dz);
                BlockPos pos = ground.above();
                if (!inside(pos, minX, minZ) || !dryWritable(level, chunk, ground)
                        || !natural(chunk.getBlockState(ground)) || !empty(level, chunk, pos)) {
                    return false;
                }
                BlockState state = crystal && dx == 0 && dz == 0
                        ? AnomalyBlocks.ANOMALY_CRYSTAL_BLOCK.get().defaultBlockState()
                        : AnomalyBlocks.DISTORTED_CLIFF_STONE.get().defaultBlockState();
                changes.add(new Change(pos, state));
            }
        }
        if (crystal) {
            BlockPos cap = top.above(2);
            if (!empty(level, chunk, cap)) {
                return false;
            }
            changes.add(new Change(cap, (zone == AnomalyZone.RIFT_CORE
                    ? AnomalyBlocks.FRACTURED_ENERGY_BLOCK : AnomalyBlocks.ANOMALY_CRYSTAL_BLOCK).get().defaultBlockState()));
        }
        return apply(level, changes);
    }

    private static boolean fracture(ServerLevelAccessor level, ChunkAccess chunk, BlockPos top) {
        List<Change> changes = new ArrayList<>();
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos surface = top.offset(offset, 0, 0);
            BlockPos floor = surface.below();
            if (!dryWritable(level, chunk, surface) || !dryWritable(level, chunk, floor)
                    || !natural(chunk.getBlockState(surface)) || !natural(chunk.getBlockState(floor))
                    || !empty(level, chunk, surface.above())) {
                return false;
            }
            changes.add(new Change(surface, Blocks.AIR.defaultBlockState()));
            changes.add(new Change(floor, AnomalyBlocks.UNSTABLE_RIFT_STONE.get().defaultBlockState()));
        }
        return apply(level, changes);
    }

    private static boolean suspendedDebris(ServerLevelAccessor level, ChunkAccess chunk, BlockPos top, int minX, int minZ) {
        List<Change> changes = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            BlockPos pos = top.offset(dx, 5 + (dx == 0 ? 1 : 0), 0);
            if (!inside(pos, minX, minZ) || !empty(level, chunk, pos)
                    || !empty(level, chunk, pos.below()) || !empty(level, chunk, pos.above())) {
                return false;
            }
            changes.add(new Change(pos, AnomalyBlocks.DISTORTED_CLIFF_STONE.get().defaultBlockState()));
        }
        return apply(level, changes);
    }

    /** A rare decorative stone tear; authoritative temporal-rift gameplay is deliberately not invoked. */
    private static boolean riftFormation(ServerLevelAccessor level, ChunkAccess chunk, BlockPos top, int minX, int minZ) {
        // Require the entire five-wide arch and its surrounding air volume before the first write.
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                BlockPos ground = top.offset(dx, 0, dz);
                if (!inside(ground, minX, minZ) || !dryWritable(level, chunk, ground)
                        || !natural(chunk.getBlockState(ground))) {
                    return false;
                }
                for (int dy = 1; dy <= 7; dy++) {
                    if (!empty(level, chunk, ground.above(dy))) {
                        return false;
                    }
                }
            }
        }
        List<Change> changes = new ArrayList<>();
        for (int side : new int[]{-2, 2}) {
            for (int dy = 1; dy <= 5; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    BlockState material = dy == 1
                            ? AnomalyBlocks.DISTORTED_CLIFF_STONE.get().defaultBlockState()
                            : AnomalyBlocks.UNSTABLE_RIFT_STONE.get().defaultBlockState();
                    changes.add(new Change(top.offset(side, dy, dz), material));
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            changes.add(new Change(top.offset(dx, 6, 0), (dx == 0
                    ? AnomalyBlocks.FRACTURED_ENERGY_BLOCK : AnomalyBlocks.UNSTABLE_RIFT_STONE).get().defaultBlockState()));
            changes.add(new Change(top.offset(dx, 6, 1), AnomalyBlocks.DISTORTED_STONE.get().defaultBlockState()));
        }
        return apply(level, changes);
    }

    private static boolean apply(ServerLevelAccessor level, List<Change> changes) {
        boolean changed = false;
        for (Change change : changes) {
            changed |= level.setBlock(change.position, change.state, FLAGS);
        }
        return changed;
    }

    private static boolean dryWritable(ServerLevelAccessor level, ChunkAccess chunk, BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);
        return AnomalyIslandPlacement.canWrite(level, pos) && !state.hasBlockEntity()
                && chunk.getBlockEntity(pos) == null && state.getFluidState().isEmpty();
    }

    private static boolean empty(ServerLevelAccessor level, ChunkAccess chunk, BlockPos pos) {
        return dryWritable(level, chunk, pos) && chunk.getBlockState(pos).isAir();
    }

    private static boolean inside(BlockPos pos, int minX, int minZ) {
        return pos.getX() >= minX && pos.getX() < minX + 16 && pos.getZ() >= minZ && pos.getZ() < minZ + 16;
    }

    private static boolean natural(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE);
    }

    private static double unit(long seed, int x, int z, long salt) {
        long value = seed ^ salt ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private record Change(BlockPos position, BlockState state) { }
}
