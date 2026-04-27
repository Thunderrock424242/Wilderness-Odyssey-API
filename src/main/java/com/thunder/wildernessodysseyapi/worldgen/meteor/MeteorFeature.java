package com.thunder.wildernessodysseyapi.worldgen.meteor;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.util.SimplexNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class MeteorFeature extends Feature<NoneFeatureConfiguration> {

    // ---- Block palette — all vanilla ----
    private static final BlockState STONE         = Blocks.STONE.defaultBlockState();
    private static final BlockState COBBLE        = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY_COBBLE  = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState STONE_BRICKS  = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED_BRICKS= Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState GRAVEL        = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState COARSE_DIRT   = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState DIRT          = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS         = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DEEPSLATE     = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState COBBLED_DEEP  = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    private static final BlockState OBSIDIAN      = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState BLACKSTONE    = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState MOSS_BLOCK    = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState LAVA          = Blocks.LAVA.defaultBlockState();
    private static final BlockState MAGMA         = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState AIR           = Blocks.AIR.defaultBlockState();

    public MeteorFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel world  = ctx.level();
        BlockPos      origin = ctx.origin();
        RandomSource  rng    = ctx.random();

        // ---- Size parameters ----
        int craterRadius = 60 + rng.nextInt(66);         // 60–125 blocks
        int bowlDepth    = craterRadius / 5 + rng.nextInt(8); // ~15–33 blocks
        int rimHeight    = 8 + rng.nextInt(13);          // 8–20 blocks
        int ejectaRange  = (int)(craterRadius * 0.4) + rng.nextInt(20);

        BlockPos center = snapToSurface(world, origin);

        long seed = (long) center.getX() * 341873128712L ^ (long) center.getZ() * 132897987541L;
        SimplexNoise noise = new SimplexNoise(seed);

        ModConstants.LOGGER.info("Generating meteor impact at {} radius={}", center, craterRadius);

        // ---- Generation passes ----
        excavateBowl(world, center, craterRadius, bowlDepth, noise);
        buildRim(world, center, craterRadius, rimHeight, noise, rng);
        resurfaceBowlFloor(world, center, craterRadius, rng);
        placeMeteorFragment(world, center, craterRadius, bowlDepth, noise, rng);
        placeLavaPools(world, center, craterRadius, rng, noise);
        placeEjecta(world, center, craterRadius, ejectaRange, rng, noise);
        ageSite(world, center, craterRadius, rimHeight, noise, rng);
        carveUndergroundCave(world, center, craterRadius, bowlDepth);

        // ---- Persist site for radiation system ----
        if (world instanceof ServerLevel serverLevel) {
            MeteorSavedData.get(serverLevel).addMeteor(center, craterRadius);
        }

        return true;
    }

    // =========================================================
    //  PASS 1 — Excavate the bowl
    // =========================================================
    private void excavateBowl(WorldGenLevel world, BlockPos center,
                               int craterRadius, int bowlDepth, SimplexNoise noise) {
        long r2 = (long) craterRadius * craterRadius;

        for (int x = -craterRadius; x <= craterRadius; x++) {
            for (int z = -craterRadius; z <= craterRadius; z++) {
                long distSq = (long) x * x + (long) z * z;
                if (distSq > r2) continue;

                double ratio         = Math.sqrt(distSq) / craterRadius;
                double depthFraction = 1.0 - (ratio * ratio); // parabolic
                double nv = noise.fractal(x * 0.04, z * 0.04, 3, 0.5) * 0.15;
                int depth = Math.max(0, (int)(bowlDepth * (depthFraction + nv)));

                int surfY = getSurfaceY(world, center.getX() + x, center.getZ() + z);

                for (int dy = 0; dy <= depth + 2; dy++) {
                    world.setBlock(
                        new BlockPos(center.getX() + x, surfY - dy, center.getZ() + z),
                        AIR, Block.UPDATE_CLIENTS
                    );
                }
            }
        }
    }

    // =========================================================
    //  PASS 2 — Build raised rim (Barringer-style ejecta wall)
    // =========================================================
    private void buildRim(WorldGenLevel world, BlockPos center,
                          int craterRadius, int rimHeight,
                          SimplexNoise noise, RandomSource rng) {
        int rimWidth = rimHeight + 6;

        for (int x = -(craterRadius + rimWidth); x <= craterRadius + rimWidth; x++) {
            for (int z = -(craterRadius + rimWidth); z <= craterRadius + rimWidth; z++) {
                double dist = Math.sqrt((double) x * x + (double) z * z);
                if (dist < craterRadius - 2 || dist > craterRadius + rimWidth) continue;

                double rimCenter = craterRadius + rimWidth * 0.3;
                double rimSigma  = rimWidth * 0.45;
                double rimFactor = Math.exp(-0.5 * Math.pow((dist - rimCenter) / rimSigma, 2));

                double nv = noise.fractal(x * 0.06, z * 0.06, 4, 0.5);
                int height = (int)(rimHeight * rimFactor * (0.75 + nv * 0.35));
                if (height <= 0) continue;

                int surfY = getSurfaceY(world, center.getX() + x, center.getZ() + z);

                for (int dy = 0; dy < height; dy++) {
                    BlockPos bp = new BlockPos(center.getX() + x, surfY + dy, center.getZ() + z);
                    setIfReplaceable(world, bp, pickRimBlock(dy, height, rng));
                }
            }
        }
    }

    private BlockState pickRimBlock(int dy, int totalHeight, RandomSource rng) {
        float t = (float) dy / totalHeight;
        int r = rng.nextInt(10);
        if (t > 0.8f)      return switch (r) { case 0, 1 -> MOSSY_COBBLE; case 2 -> CRACKED_BRICKS; default -> COBBLE; };
        else if (t > 0.4f) return switch (r) { case 0 -> COBBLE; case 1 -> STONE_BRICKS; default -> STONE; };
        else               return r < 2 ? COBBLE : STONE;
    }

    // =========================================================
    //  PASS 3 — Re-surface the bowl floor
    // =========================================================
    private void resurfaceBowlFloor(WorldGenLevel world, BlockPos center,
                                    int craterRadius, RandomSource rng) {
        long r2 = (long) craterRadius * craterRadius;
        for (int x = -craterRadius; x <= craterRadius; x++) {
            for (int z = -craterRadius; z <= craterRadius; z++) {
                if ((long) x * x + (long) z * z > r2) continue;
                int surfY  = getSurfaceY(world, center.getX() + x, center.getZ() + z);
                int layers = 1 + rng.nextInt(3);
                for (int dy = 0; dy < layers; dy++) {
                    BlockPos bp = new BlockPos(center.getX() + x, surfY - dy, center.getZ() + z);
                    BlockState bs = dy == 0
                        ? (rng.nextInt(3) == 0 ? COARSE_DIRT : GRAVEL)
                        : (rng.nextInt(4) == 0 ? DIRT : GRAVEL);
                    world.setBlock(bp, bs, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // =========================================================
    //  PASS 4 — Meteor fragment spire at center
    // =========================================================
    private void placeMeteorFragment(WorldGenLevel world, BlockPos center,
                                     int craterRadius, int bowlDepth,
                                     SimplexNoise noise, RandomSource rng) {
        int spireHeight    = 5 + rng.nextInt(11);
        int spireBaseWidth = 6 + rng.nextInt(5);
        int surfY = getSurfaceY(world, center.getX(), center.getZ());

        // Above-ground spire
        for (int dy = 0; dy <= spireHeight; dy++) {
            float progress = (float) dy / spireHeight;
            double radius  = spireBaseWidth * (1.0 - progress * 0.85);
            int ir = (int) Math.ceil(radius);

            for (int x = -ir; x <= ir; x++) {
                for (int z = -ir; z <= ir; z++) {
                    double nv  = noise.fractal(x * 0.3 + dy * 0.15, z * 0.3 + dy * 0.15, 2, 0.5);
                    double effR = radius * (0.7 + nv * 0.4);
                    if ((double) x * x + (double) z * z > effR * effR) continue;
                    BlockPos bp = new BlockPos(center.getX() + x, surfY + dy, center.getZ() + z);
                    world.setBlock(bp, pickMeteorBlock(progress, rng), Block.UPDATE_CLIENTS);
                }
            }
        }

        // Buried root
        int rootDepth = 4 + rng.nextInt(5);
        for (int dy = 1; dy <= rootDepth; dy++) {
            int r = spireBaseWidth - dy;
            if (r <= 0) break;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if ((double) x * x + (double) z * z > (double) r * r * 1.2) continue;
                    world.setBlock(
                        new BlockPos(center.getX() + x, surfY - dy, center.getZ() + z),
                        pickMeteorBlock(0f, rng), Block.UPDATE_CLIENTS
                    );
                }
            }
        }
    }

    private BlockState pickMeteorBlock(float progress, RandomSource rng) {
        int r = rng.nextInt(10);
        if (progress > 0.7f) return switch (r) { case 0, 1 -> OBSIDIAN; case 2 -> COBBLED_DEEP; default -> DEEPSLATE; };
        else                 return switch (r) { case 0, 1 -> BLACKSTONE; case 2 -> OBSIDIAN; case 3 -> COBBLED_DEEP; default -> DEEPSLATE; };
    }

    // =========================================================
    //  PASS 5 — Lava pools
    // =========================================================
    private void placeLavaPools(WorldGenLevel world, BlockPos center,
                                int craterRadius, RandomSource rng, SimplexNoise noise) {
        int poolCount = 2 + rng.nextInt(4);

        for (int p = 0; p < poolCount; p++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist  = rng.nextDouble() * craterRadius * 0.6;
            int px = (int)(Math.cos(angle) * dist);
            int pz = (int)(Math.sin(angle) * dist);
            int py = getSurfaceY(world, center.getX() + px, center.getZ() + pz);
            int poolR = 2 + rng.nextInt(5);

            for (int x = -poolR; x <= poolR; x++) {
                for (int z = -poolR; z <= poolR; z++) {
                    double nv  = noise.noise(px * 0.1 + x * 0.2, pz * 0.1 + z * 0.2);
                    double eff = poolR * (0.8 + nv * 0.3);
                    if ((double) x * x + (double) z * z > eff * eff) continue;

                    world.setBlock(new BlockPos(center.getX() + px + x, py - 1, center.getZ() + pz + z), MAGMA, Block.UPDATE_CLIENTS);
                    world.setBlock(new BlockPos(center.getX() + px + x, py,     center.getZ() + pz + z), LAVA,  Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // =========================================================
    //  PASS 6 — Ejecta debris field
    // =========================================================
    private void placeEjecta(WorldGenLevel world, BlockPos center,
                              int craterRadius, int ejectaRange,
                              RandomSource rng, SimplexNoise noise) {
        int debrisCount = 200 + rng.nextInt(200);
        for (int i = 0; i < debrisCount; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist  = craterRadius + rng.nextDouble() * ejectaRange;

            // Density falls off with distance
            double densityFactor = 1.0 - (dist - craterRadius) / ejectaRange;
            if (rng.nextDouble() > densityFactor) continue;

            int dx = (int)(Math.cos(angle) * dist);
            int dz = (int)(Math.sin(angle) * dist);
            int worldX = center.getX() + dx;
            int worldZ = center.getZ() + dz;
            int surfY  = getSurfaceY(world, worldX, worldZ);

            int boulderSize = 1 + rng.nextInt(4);
            for (int b = 0; b < boulderSize; b++) {
                int bx = worldX + rng.nextInt(3) - 1;
                int bz = worldZ + rng.nextInt(3) - 1;
                int by = getSurfaceY(world, bx, bz) + 1;
                world.setBlock(new BlockPos(bx, by, bz), rng.nextInt(3) == 0 ? COBBLE : STONE, Block.UPDATE_CLIENTS);
            }
        }
    }

    // =========================================================
    //  PASS 7 — Age the site (50 years of regrowth)
    // =========================================================
    private void ageSite(WorldGenLevel world, BlockPos center,
                         int craterRadius, int rimHeight,
                         SimplexNoise noise, RandomSource rng) {
        int totalRange = craterRadius + rimHeight + 8;

        for (int x = -totalRange; x <= totalRange; x++) {
            for (int z = -totalRange; z <= totalRange; z++) {
                double dist  = Math.sqrt((double) x * x + (double) z * z);
                int worldX   = center.getX() + x;
                int worldZ   = center.getZ() + z;
                int surfY    = getSurfaceY(world, worldX, worldZ);
                BlockPos sp  = new BlockPos(worldX, surfY, worldZ);
                BlockState bs = world.getBlockState(sp);

                // Rim zone — moss coverage
                if (dist >= craterRadius - 4 && dist <= craterRadius + rimHeight + 8) {
                    double mv = noise.fractal(x * 0.12, z * 0.12, 3, 0.5);
                    if (mv > 0.0 && rng.nextFloat() < 0.4f) {
                        if      (bs.is(Blocks.COBBLESTONE))  world.setBlock(sp, MOSSY_COBBLE, Block.UPDATE_CLIENTS);
                        else if (bs.is(Blocks.STONE))         world.setBlock(sp, MOSS_BLOCK,   Block.UPDATE_CLIENTS);
                    }
                }

                // Inner slope — grass reclaims disturbed soil
                if (dist > craterRadius * 0.6 && dist < craterRadius - 2) {
                    double gv = noise.fractal(x * 0.09, z * 0.09, 2, 0.6);
                    if (gv > 0.1 && rng.nextFloat() < 0.5f) {
                        if (bs.is(Blocks.DIRT) || bs.is(Blocks.COARSE_DIRT)) {
                            world.setBlock(sp, GRASS, Block.UPDATE_CLIENTS);
                        }
                    }
                }

                // Meteor surface — partial moss coverage
                if (dist < craterRadius * 0.25) {
                    if (bs.is(Blocks.DEEPSLATE) || bs.is(Blocks.COBBLED_DEEPSLATE)) {
                        if (noise.noise(x * 0.2, z * 0.2) > 0.4 && rng.nextFloat() < 0.25f) {
                            world.setBlock(sp, MOSSY_COBBLE, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }

    // =========================================================
    //  PASS 8 — Underground cave void
    // =========================================================
    private void carveUndergroundCave(WorldGenLevel world, BlockPos center,
                                       int craterRadius, int bowlDepth) {
        int caveRadius  = craterRadius / 3;
        int surfY       = getSurfaceY(world, center.getX(), center.getZ());
        int caveDepth   = bowlDepth + 8 + caveRadius;
        int caveCenterY = surfY - caveDepth;
        int r2          = caveRadius * caveRadius;

        // Flattened ellipsoid
        for (int x = -caveRadius; x <= caveRadius; x++) {
            for (int y = -(caveRadius / 2); y <= caveRadius / 2; y++) {
                for (int z = -caveRadius; z <= caveRadius; z++) {
                    double check = (double) x * x + (double) y * y * 4.0 + (double) z * z;
                    if (check > r2) continue;
                    world.setBlock(new BlockPos(center.getX() + x, caveCenterY + y, center.getZ() + z), AIR, Block.UPDATE_CLIENTS);
                }
            }
        }

        // 1–3 crack tunnels to the surface
        int crackCount = 1 + (int)(Math.random() * 2);
        for (int c = 0; c < crackCount; c++) {
            double angle = c * (Math.PI * 2.0 / crackCount) + Math.PI / 6;
            int crackX = (int)(Math.cos(angle) * caveRadius * 0.5);
            int crackZ = (int)(Math.sin(angle) * caveRadius * 0.5);
            int topY   = getSurfaceY(world, center.getX() + crackX, center.getZ() + crackZ);

            for (int y = caveCenterY + caveRadius / 2; y <= topY; y++) {
                world.setBlock(new BlockPos(center.getX() + crackX, y, center.getZ() + crackZ), AIR, Block.UPDATE_CLIENTS);
                if (y % 3 == 0) {
                    world.setBlock(new BlockPos(center.getX() + crackX + 1, y, center.getZ() + crackZ), AIR, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // =========================================================
    //  Helpers
    // =========================================================
    private int getSurfaceY(WorldGenLevel world, int x, int z) {
        int y = world.getMaxBuildHeight() - 1;
        while (y > world.getMinBuildHeight() && world.getBlockState(new BlockPos(x, y, z)).isAir()) {
            y--;
        }
        return y;
    }

    private void setIfReplaceable(WorldGenLevel world, BlockPos pos, BlockState state) {
        BlockState existing = world.getBlockState(pos);
        if (existing.isAir()
            || existing.is(Blocks.GRASS_BLOCK)
            || existing.is(Blocks.DIRT)
            || existing.is(Blocks.STONE)) {
            world.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
