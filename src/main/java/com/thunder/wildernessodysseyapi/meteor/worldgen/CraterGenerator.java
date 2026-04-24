package com.thunder.wildernessodysseyapi.meteor.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all block-level crater and gouge generation for meteor impacts.
 * <p>
 * Crater anatomy:
 *   - Outer ring:  rough terrain, some magma and fire
 *   - Middle ring: crying obsidian, magma, scattered amethyst buds
 *   - Center:      the meteor itself (crying obsidian + magma core), amethyst clusters, fire
 * <p>
 * Gouge trail (angled impacts):
 *   A trench carved backward along the approach vector before the main crater.
 */
public class CraterGenerator {

    // Block states used in crater decoration
    private static final BlockState AIR              = Blocks.AIR.defaultBlockState();
    private static final BlockState MAGMA            = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState CRYING_OBSIDIAN  = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState FIRE             = Blocks.FIRE.defaultBlockState();
    private static final BlockState OBSIDIAN         = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState AMETHYST_CLUSTER = Blocks.AMETHYST_CLUSTER.defaultBlockState();
    private static final BlockState AMETHYST_BUD_L   = Blocks.LARGE_AMETHYST_BUD.defaultBlockState();
    private static final BlockState AMETHYST_BUD_M   = Blocks.MEDIUM_AMETHYST_BUD.defaultBlockState();
    private static final BlockState COBBLESTONE      = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState GRAVEL           = Blocks.GRAVEL.defaultBlockState();

    /**
     * Generate the full impact crater plus optional gouge trail.
     *
     * @param level        the server-side world
     * @param impactPos    the block position of impact
     * @param craterRadius radius of the main crater
     * @param velocity     the meteor's velocity at impact (used to determine gouge direction/length)
     * @param random       random source
     * @param gougeMult    config multiplier for gouge length
     */
    public static void generate(Level level, BlockPos impactPos, int craterRadius,
                                Vec3 velocity, RandomSource random, int gougeMult) {
        // --- 1. Gouge trail (if the meteor came in at an angle) ---
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double verticalSpeed   = Math.abs(velocity.y);

        // Only carve a gouge if horizontal movement is at least 30% of vertical
        if (horizontalSpeed > verticalSpeed * 0.3) {
            int gougeLength = (int)(horizontalSpeed * gougeMult);
            carveGouge(level, impactPos, velocity, gougeLength, craterRadius, random);
        }

        // --- 2. Main crater excavation (sphere carved into ground) ---
        excavateCrater(level, impactPos, craterRadius, random);

        // --- 3. Decorate crater floor and walls ---
        decorateCrater(level, impactPos, craterRadius, random);

        // --- 4. Place the meteor core at center ---
        placeMeteorCore(level, impactPos, random);
    }

    // -------------------------------------------------------------------------
    // Gouge trail
    // -------------------------------------------------------------------------

    private static void carveGouge(Level level, BlockPos impactPos, Vec3 velocity,
                                   int gougeLength, int craterRadius, RandomSource random) {
        // Normalize horizontal approach direction (opposite of travel = where it came from)
        double len = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (len == 0) return;
        double dx = -velocity.x / len;  // backward along approach
        double dz = -velocity.z / len;

        // Gouge width tapers from craterRadius at impact to 1 at the far end
        for (int step = 1; step <= gougeLength; step++) {
            double progress = (double) step / gougeLength;            // 0 near impact → 1 far
            int gougeWidth  = Math.max(1, (int)(craterRadius * (1.0 - progress * 0.75)));
            int gougeDepth  = Math.max(1, (int)((craterRadius * 0.6) * (1.0 - progress * 0.8)));

            double cx = impactPos.getX() + dx * step;
            double cz = impactPos.getZ() + dz * step;

            for (int rx = -gougeWidth; rx <= gougeWidth; rx++) {
                for (int rz = -gougeWidth; rz <= gougeWidth; rz++) {
                    if (rx * rx + rz * rz > gougeWidth * gougeWidth) continue;
                    int bx = (int)(cx + rx);
                    int bz = (int)(cz + rz);

                    for (int ry = -gougeDepth; ry <= gougeDepth / 2; ry++) {
                        BlockPos pos = new BlockPos(bx, impactPos.getY() + ry, bz);
                        if (!level.isOutsideBuildHeight(pos)) {
                            level.setBlock(pos, AIR, 3);
                        }
                    }

                    // Scatter some debris along the gouge edges
                    BlockPos surfacePos = new BlockPos(bx, impactPos.getY() + gougeDepth / 2 + 1, bz);
                    if (!level.isOutsideBuildHeight(surfacePos) && random.nextFloat() < 0.15f) {
                        if (random.nextFloat() < 0.5f) {
                            level.setBlock(surfacePos, MAGMA, 3);
                        } else {
                            level.setBlock(surfacePos, GRAVEL, 3);
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Crater excavation
    // -------------------------------------------------------------------------

    private static void excavateCrater(Level level, BlockPos center, int radius, RandomSource random) {
        // We carve a slightly flattened sphere (squash Y by 0.6 so craters look wide and shallow)
        double ySquash = 0.6;
        int r2 = radius * radius;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -(int)(radius * ySquash); y <= (int)(radius * ySquash * 0.5); y++) {
                    double dist2 = x * x + (y / ySquash) * (y / ySquash) + z * z;

                    // Add noise to the edge so craters look organic
                    double noise = 1.0 + (random.nextDouble() - 0.5) * 0.3;
                    if (dist2 < r2 * noise) {
                        BlockPos pos = center.offset(x, y, z);
                        if (!level.isOutsideBuildHeight(pos)) {
                            level.setBlock(pos, AIR, 3);
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Crater decoration
    // -------------------------------------------------------------------------

    private static void decorateCrater(Level level, BlockPos center, int radius, RandomSource random) {
        double ySquash = 0.6;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -(int)(radius * ySquash); y <= (int)(radius * ySquash * 0.4); y++) {
                    double dist = Math.sqrt(x * x + (y / ySquash) * (y / ySquash) + z * z);
                    double normalized = dist / radius; // 0 = center, 1 = edge

                    BlockPos pos = center.offset(x, y, z);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState existing = level.getBlockState(pos);

                    // Only replace solid non-air blocks (crater walls/floor)
                    if (!existing.isAir()) {
                        placeDecoration(level, pos, normalized, random);
                    }

                    // On the crater floor surface, scatter fire and amethyst upward
                    if (existing.isAir()) {
                        BlockPos below = pos.below();
                        if (!level.getBlockState(below).isAir() && !level.isOutsideBuildHeight(below)) {
                            scatterSurface(level, pos, normalized, random);
                        }
                    }
                }
            }
        }
    }

    /** Replace a wall/floor block with crater material based on distance from center */
    private static void placeDecoration(Level level, BlockPos pos, double normalized, RandomSource random) {
        float r = random.nextFloat();

        if (normalized < 0.35) {
            // Deep center: heavy magma and crying obsidian
            if (r < 0.45f)      level.setBlock(pos, MAGMA, 3);
            else if (r < 0.75f) level.setBlock(pos, CRYING_OBSIDIAN, 3);
            else if (r < 0.90f) level.setBlock(pos, OBSIDIAN, 3);
            // else leave excavated air

        } else if (normalized < 0.65) {
            // Mid ring: mix of magma, crying obsidian, cobble
            if (r < 0.30f)      level.setBlock(pos, MAGMA, 3);
            else if (r < 0.50f) level.setBlock(pos, CRYING_OBSIDIAN, 3);
            else if (r < 0.70f) level.setBlock(pos, COBBLESTONE, 3);
            else if (r < 0.80f) level.setBlock(pos, OBSIDIAN, 3);

        } else {
            // Outer rim: mostly cobble and gravel, rare magma
            if (r < 0.12f)      level.setBlock(pos, MAGMA, 3);
            else if (r < 0.25f) level.setBlock(pos, CRYING_OBSIDIAN, 3);
            else if (r < 0.55f) level.setBlock(pos, COBBLESTONE, 3);
            else if (r < 0.70f) level.setBlock(pos, GRAVEL, 3);
        }
    }

    /** Place fire and amethyst on top of crater floor surface blocks */
    private static void scatterSurface(Level level, BlockPos airPos, double normalized, RandomSource random) {
        float r = random.nextFloat();

        if (normalized < 0.25) {
            // Center: fire and amethyst clusters
            if (r < 0.30f)      level.setBlock(airPos, FIRE, 3);
            else if (r < 0.50f) level.setBlock(airPos, AMETHYST_CLUSTER, 3);
            else if (r < 0.60f) level.setBlock(airPos, AMETHYST_BUD_L, 3);

        } else if (normalized < 0.55) {
            // Mid: some fire and amethyst buds
            if (r < 0.18f)      level.setBlock(airPos, FIRE, 3);
            else if (r < 0.30f) level.setBlock(airPos, AMETHYST_BUD_M, 3);
            else if (r < 0.38f) level.setBlock(airPos, AMETHYST_BUD_L, 3);

        } else {
            // Outer: sparse fire
            if (r < 0.08f) level.setBlock(airPos, FIRE, 3);
        }
    }

    // -------------------------------------------------------------------------
    // Meteor core
    // -------------------------------------------------------------------------

    /**
     * Places the physical meteor remnant at the center of the crater.
     * Looks like a jagged clump of crying obsidian and magma with
     * amethyst crystals poking out.
     */
    private static void placeMeteorCore(Level level, BlockPos center, RandomSource random) {
        int coreRadius = 2;

        // Solid core
        for (int x = -coreRadius; x <= coreRadius; x++) {
            for (int y = -coreRadius; y <= coreRadius; y++) {
                for (int z = -coreRadius; z <= coreRadius; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (dist > coreRadius + random.nextDouble() * 0.6 - 0.3) continue;

                    BlockPos pos = center.offset(x, y, z);
                    if (level.isOutsideBuildHeight(pos)) continue;

                    float r = random.nextFloat();
                    if (r < 0.55f)      level.setBlock(pos, CRYING_OBSIDIAN, 3);
                    else if (r < 0.90f) level.setBlock(pos, MAGMA, 3);
                    else                level.setBlock(pos, OBSIDIAN, 3);
                }
            }
        }

        // Amethyst clusters poking out of the core surface (6 cardinal directions + diagonals)
        List<BlockPos> surfaceCandidates = new ArrayList<>();
        for (int x = -coreRadius - 1; x <= coreRadius + 1; x++) {
            for (int y = -coreRadius - 1; y <= coreRadius + 1; y++) {
                for (int z = -coreRadius - 1; z <= coreRadius + 1; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    if (!level.getBlockState(pos).isAir()) continue;

                    // Check if adjacent to a core block
                    boolean adjacentToCore = false;
                    for (BlockPos neighbor : List.of(pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below())) {
                        BlockState ns = level.getBlockState(neighbor);
                        if (ns.is(Blocks.CRYING_OBSIDIAN) || ns.is(Blocks.MAGMA_BLOCK) || ns.is(Blocks.OBSIDIAN)) {
                            adjacentToCore = true;
                            break;
                        }
                    }
                    if (adjacentToCore) surfaceCandidates.add(pos);
                }
            }
        }

        // Randomly pick ~8-14 spots for crystals/fire
        int numCrystals = 8 + random.nextInt(7);
        for (int i = 0; i < numCrystals && !surfaceCandidates.isEmpty(); i++) {
            int idx = random.nextInt(surfaceCandidates.size());
            BlockPos pos = surfaceCandidates.remove(idx);
            float r = random.nextFloat();
            if (r < 0.50f)      level.setBlock(pos, AMETHYST_CLUSTER, 3);
            else if (r < 0.75f) level.setBlock(pos, AMETHYST_BUD_L, 3);
            else if (r < 0.90f) level.setBlock(pos, FIRE, 3);
        }

        // Fire on top of the core
        BlockPos topFire = center.above(coreRadius + 1);
        if (!level.isOutsideBuildHeight(topFire)) {
            level.setBlock(topFire, FIRE, 3);
        }
    }
}