package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.thunder.wildernessodysseyapi.worldgen.config.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Coordinates the one-time landscaping pass that turns the starter bunker island into an overgrown jungle ruin.
 *
 * <p>The server owns this pass immediately after the bunker template is placed. Ground and vegetation work live in
 * separate feature-specific helpers, while this class owns deterministic seeding and shared placement boundaries.</p>
 */
public final class StarterIslandJungleDecorator {
    static final int TREE_CLEARING = 8;
    static final int GROUND_CLEARING = 3;
    static final int ENTRANCE_PATH_LENGTH = 26;
    static final int ENTRANCE_HALF_WIDTH = 5;
    static final int TREE_EDGE_MARGIN = 6;
    static final int BLOCK_UPDATE_FLAGS = 2;
    private static final long DECORATION_SALT = 0x4A554E474C454CL;

    private StarterIslandJungleDecorator() {
    }

    /**
     * Adds ground variation, a readable entrance trail, jungle trees, bamboo, rocks, and undergrowth.
     *
     * @param level the authoritative overworld level receiving the blocks
     * @param centerX the generated starter island center on the X axis
     * @param centerZ the generated starter island center on the Z axis
     * @param flatRadius the radius of the island's stable grass platform
     * @param bunkerBounds the placed bunker bounds used to protect traversal space
     */
    public static void decorate(ServerLevel level,
                                int centerX,
                                int centerZ,
                                int flatRadius,
                                AABB bunkerBounds) {
        if (!StructureConfig.STARTER_ISLAND_JUNGLE_ENABLED.get() || flatRadius <= TREE_EDGE_MARGIN) {
            return;
        }

        double density = StructureConfig.STARTER_ISLAND_JUNGLE_DENSITY.get();
        if (density <= 0.0D) {
            return;
        }

        RandomSource random = RandomSource.create(
                level.getSeed() ^ BlockPos.asLong(centerX, level.getSeaLevel(), centerZ) ^ DECORATION_SALT);
        DecorationArea area = new DecorationArea(centerX, centerZ, flatRadius, bunkerBounds);

        // Ground is detailed first so rocks and terrain patches can reserve natural gaps in the later vegetation.
        StarterIslandGroundDecorator.decorate(level, random, area, density);
        StarterIslandVegetationDecorator.decorate(level, random, area, density);
    }

    static int targetTreeCount(int flatRadius, double density) {
        int usableRadius = Math.max(0, flatRadius - TREE_EDGE_MARGIN);
        double area = Math.PI * usableRadius * usableRadius;
        return Math.max(0, Math.min(96, (int) Math.round((area / 260.0D) * density)));
    }

    static boolean isProtectedPosition(int x,
                                       int z,
                                       AABB bunkerBounds,
                                       int clearing,
                                       boolean protectEntrance) {
        int minX = (int) Math.floor(bunkerBounds.minX);
        int maxX = (int) Math.ceil(bunkerBounds.maxX) - 1;
        int minZ = (int) Math.floor(bunkerBounds.minZ);
        int maxZ = (int) Math.ceil(bunkerBounds.maxZ) - 1;

        int dx = x < minX ? minX - x : Math.max(0, x - maxX);
        int dz = z < minZ ? minZ - z : Math.max(0, z - maxZ);
        if (Math.max(dx, dz) <= clearing) {
            return true;
        }

        if (!protectEntrance) {
            return false;
        }
        int entranceX = (minX + maxX) / 2;
        return Math.abs(x - entranceX) <= ENTRANCE_HALF_WIDTH + clearing
                && z <= minZ
                && z >= minZ - ENTRANCE_PATH_LENGTH - clearing;
    }

    static int surfaceY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    static int randomCoordinate(RandomSource random, int center, int radius) {
        return center + random.nextInt(radius * 2 + 1) - radius;
    }

    static boolean insideCircle(int x, int z, int centerX, int centerZ, int radius) {
        long dx = x - centerX;
        long dz = z - centerZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    static boolean isPlantableGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT);
    }

    static boolean isVegetationReplaceable(BlockState state) {
        return state.isAir()
                || state.canBeReplaced()
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.VINE);
    }

    /** Shared immutable island geometry passed to the ground and vegetation helpers. */
    record DecorationArea(int centerX, int centerZ, int flatRadius, AABB bunkerBounds) {
    }
}
