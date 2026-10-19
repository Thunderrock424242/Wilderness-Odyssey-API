package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * One feature invocation's bounded terrain snapshot and write boundary.
 * Reads at most a 56 by 56 area already available in the worldgen region;
 * ocean contacts are captured before terrain, pools, rocks or trees can alter them.
 * Nothing is retained after placement or requested from unavailable chunks.
 */
final class CoastalGenerationArea {
    static final int REACH = 20;
    private static final int SIZE = 16 + REACH * 2;
    private final WorldGenLevel level;
    final int minimumX;
    final int minimumZ;
    private final Column[] columns = new Column[SIZE * SIZE];
    private final CoastalWaveProfile.ShoreType[] shoreTypes = new CoastalWaveProfile.ShoreType[SIZE * SIZE];
    private final boolean[] biomeSampled = new boolean[SIZE * SIZE];
    private final Contact[] contacts = new Contact[256];
    private final int[] surfaces = new int[256];

    CoastalGenerationArea(WorldGenLevel level, int minimumX, int minimumZ) {
        this.level = level;
        this.minimumX = minimumX;
        this.minimumZ = minimumZ;
        List<Column> waterEdge = new ArrayList<>();
        for (int z = minimumZ - REACH + 2; z < minimumZ + 16 + REACH - 2; z++) {
            for (int x = minimumX - REACH + 2; x < minimumX + 16 + REACH - 2; x++) {
                Column column = column(x, z);
                if (!column.water()) continue;
                // Two-by-two tide pools cannot become a new coastline. Require
                // open water behind a boundary, at the normal sea surface.
                if ((dry(x + 1, z) && wet(x - 2, z))
                        || (dry(x - 1, z) && wet(x + 2, z))
                        || (dry(x, z + 1) && wet(x, z - 2))
                        || (dry(x, z - 1) && wet(x, z + 2))) {
                    waterEdge.add(column);
                }
            }
        }
        for (int z = minimumZ; z < minimumZ + 16; z++) {
            for (int x = minimumX; x < minimumX + 16; x++) {
                Column land = column(x, z);
                surfaces[index(x, z)] = land.y();
                int nearestSquared = REACH * REACH + 1;
                Column nearest = null;
                for (Column water : waterEdge) {
                    int dx = water.x() - x;
                    int dz = water.z() - z;
                    int squared = dx * dx + dz * dz;
                    if (squared < nearestSquared && Math.abs(water.y() - land.y()) <= 8) {
                        nearest = water;
                        nearestSquared = squared;
                    }
                }
                if (nearest != null) {
                    contacts[index(x, z)] = new Contact(Math.sqrt(nearestSquared),
                            nearest.x() - x, nearest.z() - z, nearest.x(), nearest.z());
                }
            }
        }
    }

    Contact contact(int x, int z) {
        return inside(x, z) ? contacts[index(x, z)] : null;
    }

    BlockPos surface(int x, int z) {
        return new BlockPos(x, surfaces[index(x, z)], z);
    }

    void surface(BlockPos position) {
        surfaces[index(position.getX(), position.getZ())] = position.getY();
    }

    Column column(int x, int z) {
        int localX = x - minimumX + REACH;
        int localZ = z - minimumZ + REACH;
        if (localX < 0 || localX >= SIZE || localZ < 0 || localZ >= SIZE) {
            return new Column(x, z, level.getMinBuildHeight(), false, false);
        }
        int index = localZ * SIZE + localX;
        Column cached = columns[index];
        if (cached == null) {
            boolean available = level.hasChunk(x >> 4, z >> 4);
            int y = available ? level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1
                    : level.getMinBuildHeight();
            boolean water = available && Math.abs(y - (level.getSeaLevel() - 1)) <= 1
                    && level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
            cached = new Column(x, z, y, available, water);
            columns[index] = cached;
        }
        return cached;
    }

    CoastalWaveProfile.ShoreType shoreType(int x, int z) {
        Column column = column(x, z);
        if (!column.available()) return null;
        int index = (z - minimumZ + REACH) * SIZE + x - minimumX + REACH;
        if (!biomeSampled[index]) {
            shoreTypes[index] = ModBiomes.coastalShoreType(
                    level.getBiome(new BlockPos(x, column.y(), z))).orElse(null);
            biomeSampled[index] = true;
        }
        return shoreTypes[index];
    }

    /** Tapers earthwork at actual biome boundaries, never at chunk coordinates. */
    double biomeWeight(int x, int z) {
        for (int distance = 1; distance <= 4; distance++) {
            for (int[] direction : CARDINALS) {
                int sampleX = x + direction[0] * distance;
                int sampleZ = z + direction[1] * distance;
                if (column(sampleX, sampleZ).available() && shoreType(sampleX, sampleZ) == null) {
                    return (distance - 1) / 4.0;
                }
            }
        }
        return 1.0;
    }

    boolean canWrite(BlockPos position) {
        return inside(position.getX(), position.getZ())
                && position.getY() >= level.getMinBuildHeight()
                && position.getY() < level.getMaxBuildHeight()
                && level.ensureCanWrite(position);
    }

    boolean inside(int x, int z) {
        return x >= minimumX && x < minimumX + 16 && z >= minimumZ && z < minimumZ + 16;
    }

    static boolean natural(BlockState state) {
        return !state.hasBlockEntity() && (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE));
    }

    static boolean clear(BlockState state) {
        // Short plants and snow may be replaced by details, never logs, leaves,
        // water, buildings or block entities. Palm crowns still require air.
        return state.isAir() || state.is(Blocks.SNOW) || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.DEAD_BUSH);
    }

    private boolean wet(int x, int z) { return column(x, z).water(); }
    private boolean dry(int x, int z) {
        Column column = column(x, z);
        return column.available() && !column.water() && column.y() >= level.getSeaLevel() - 1;
    }
    private int index(int x, int z) { return (z - minimumZ) * 16 + x - minimumX; }
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    record Column(int x, int z, int y, boolean available, boolean water) { }
    record Contact(double distance, int directionX, int directionZ, int x, int z) { }
}
