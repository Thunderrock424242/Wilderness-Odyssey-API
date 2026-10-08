package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyBlocks;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteServices;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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
        WorldGenLevel level = ctx.level();
        MeteorCraterPlan proposed = MeteorCraterPlan.create(ctx.origin(), ctx.random());
        BoundingBox bounds = proposed.bounds(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        // The legacy resource ID remains available, but cannot partly modify a
        // normal feature region. Natural large craters use structure pieces.
        for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
            for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                BlockPos check = new BlockPos(chunkX * 16, ctx.origin().getY(), chunkZ * 16);
                if (!level.ensureCanWrite(check)
                        || level instanceof ServerLevel server && !server.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        BlockPos center = new BlockPos(ctx.origin().getX(),
                level.getHeight(Heightmap.Types.WORLD_SURFACE, ctx.origin().getX(), ctx.origin().getZ()) - 1,
                ctx.origin().getZ());
        MeteorCraterPlan plan = new MeteorCraterPlan(center, proposed.radius(), proposed.depth(),
                proposed.rimHeight(), proposed.ejectaRange(), proposed.seed());
        placeChunk(level, plan, bounds);
        if (level instanceof ServerLevel serverLevel) {
            MeteorSiteServices.recordGeneratedSite(serverLevel, center, plan.radius());
        }
        return true;
    }

    /** Applies only the supplied chunk slice of a persisted structure plan. */
    public void placeChunk(WorldGenLevel level, MeteorCraterPlan plan, BoundingBox clip) {
        MeteorPlacementContext world = new MeteorPlacementContext(level, plan, clip);
        BlockPos center = plan.center();
        SimplexNoise noise = new SimplexNoise(plan.seed());
        long chunkSalt = ((long) clip.minX() << 32) ^ (clip.minZ() & 0xFFFFFFFFL);
        excavateBowl(world, center, plan.radius(), plan.depth(), noise);
        buildRim(world, center, plan.radius(), plan.rimHeight(), noise,
                RandomSource.create(plan.seed() ^ chunkSalt ^ 1L));
        resurfaceBowlFloor(world, center, plan.radius(), RandomSource.create(plan.seed() ^ chunkSalt ^ 2L));
        placeMeteorFragment(world, center, plan.radius(), plan.depth(), noise,
                RandomSource.create(plan.seed() ^ chunkSalt ^ 3L));
        placeLavaPools(world, center, plan.radius(), RandomSource.create(plan.seed() ^ 4L), noise);
        placeEjecta(world, center, plan.radius(), plan.ejectaRange(), RandomSource.create(plan.seed() ^ 5L), noise);
        ageSite(world, center, plan.radius(), plan.rimHeight(), noise,
                RandomSource.create(plan.seed() ^ chunkSalt ^ 6L));
        carveUndergroundCave(world, center, plan.radius(), plan.depth());
    }

    // =========================================================
    //  PASS 1 — Excavate the bowl
    // =========================================================
    private void excavateBowl(MeteorPlacementContext world, BlockPos center,
                               int craterRadius, int bowlDepth, SimplexNoise noise) {
        long r2 = (long) craterRadius * craterRadius;

        for (int x = world.minimumX(craterRadius); x <= world.maximumX(craterRadius); x++) {
            for (int z = world.minimumZ(craterRadius); z <= world.maximumZ(craterRadius); z++) {
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
    private void buildRim(MeteorPlacementContext world, BlockPos center,
                          int craterRadius, int rimHeight,
                          SimplexNoise noise, RandomSource rng) {
        int rimWidth = rimHeight + 6;

        int range = Math.min(world.plan.footprintRadius(), craterRadius + rimWidth);
        for (int x = world.minimumX(range); x <= world.maximumX(range); x++) {
            for (int z = world.minimumZ(range); z <= world.maximumZ(range); z++) {
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
    private void resurfaceBowlFloor(MeteorPlacementContext world, BlockPos center,
                                    int craterRadius, RandomSource rng) {
        long r2 = (long) craterRadius * craterRadius;
        for (int x = world.minimumX(craterRadius); x <= world.maximumX(craterRadius); x++) {
            for (int z = world.minimumZ(craterRadius); z <= world.maximumZ(craterRadius); z++) {
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
    private void placeMeteorFragment(MeteorPlacementContext world, BlockPos center,
                                     int craterRadius, int bowlDepth,
                                     SimplexNoise noise, RandomSource rng) {
        // Every slice samples the same geometry before any local palette draws.
        RandomSource geometry = RandomSource.create(world.plan.seed() ^ 3L);
        int spireHeight    = 5 + geometry.nextInt(11);
        int spireBaseWidth = 6 + geometry.nextInt(5);
        int rootDepth = 4 + geometry.nextInt(5);
        int surfY = center.getY() - bowlDepth - 2;

        // Above-ground spire
        for (int dy = 0; dy <= spireHeight; dy++) {
            float progress = (float) dy / spireHeight;
            double radius  = spireBaseWidth * (1.0 - progress * 0.85);
            int ir = (int) Math.ceil(radius);

            for (int x = world.minimumX(ir); x <= world.maximumX(ir); x++) {
                for (int z = world.minimumZ(ir); z <= world.maximumZ(ir); z++) {
                    double nv  = noise.fractal(x * 0.3 + dy * 0.15, z * 0.3 + dy * 0.15, 2, 0.5);
                    double effR = radius * (0.7 + nv * 0.4);
                    if ((double) x * x + (double) z * z > effR * effR) continue;
                    BlockPos bp = new BlockPos(center.getX() + x, surfY + dy, center.getZ() + z);
                    world.setBlock(bp, pickMeteorBlock(progress, rng), Block.UPDATE_CLIENTS);
                }
            }
        }

        // Buried root
        for (int dy = 1; dy <= rootDepth; dy++) {
            int r = spireBaseWidth - dy;
            if (r <= 0) break;
            for (int x = world.minimumX(r); x <= world.maximumX(r); x++) {
                for (int z = world.minimumZ(r); z <= world.maximumZ(r); z++) {
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
        float anomalyChance = progress > 0.65F ? 0.025F : 0.055F;
        if (rng.nextFloat() < anomalyChance) {
            return AnomalyBlocks.ANOMALY_ORE.get().defaultBlockState();
        }

        int r = rng.nextInt(10);
        if (progress > 0.7f) return switch (r) { case 0, 1 -> OBSIDIAN; case 2 -> COBBLED_DEEP; default -> DEEPSLATE; };
        else                 return switch (r) { case 0, 1 -> BLACKSTONE; case 2 -> OBSIDIAN; case 3 -> COBBLED_DEEP; default -> DEEPSLATE; };
    }

    // =========================================================
    //  PASS 5 — Lava pools
    // =========================================================
    private void placeLavaPools(MeteorPlacementContext world, BlockPos center,
                                int craterRadius, RandomSource rng, SimplexNoise noise) {
        int poolCount = 2 + rng.nextInt(4);

        for (int p = 0; p < poolCount; p++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist  = rng.nextDouble() * craterRadius * 0.6;
            int px = (int)(Math.cos(angle) * dist);
            int pz = (int)(Math.sin(angle) * dist);
            int poolR = 2 + rng.nextInt(5);

            for (int x = -poolR; x <= poolR; x++) {
                for (int z = -poolR; z <= poolR; z++) {
                    double nv  = noise.noise(px * 0.1 + x * 0.2, pz * 0.1 + z * 0.2);
                    double eff = poolR * (0.8 + nv * 0.3);
                    if ((double) x * x + (double) z * z > eff * eff) continue;
                    int worldX = center.getX() + px + x;
                    int worldZ = center.getZ() + pz + z;
                    if (!world.containsColumn(worldX, worldZ)) continue;
                    int py = getSurfaceY(world, worldX, worldZ);

                    world.setBlock(new BlockPos(center.getX() + px + x, py - 1, center.getZ() + pz + z), MAGMA, Block.UPDATE_CLIENTS);
                    world.setBlock(new BlockPos(center.getX() + px + x, py,     center.getZ() + pz + z), LAVA,  Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // =========================================================
    //  PASS 6 — Ejecta debris field
    // =========================================================
    private void placeEjecta(MeteorPlacementContext world, BlockPos center,
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
            int boulderSize = 1 + rng.nextInt(4);
            for (int b = 0; b < boulderSize; b++) {
                int bx = worldX + rng.nextInt(3) - 1;
                int bz = worldZ + rng.nextInt(3) - 1;
                BlockState debris = rng.nextInt(3) == 0 ? COBBLE : STONE;
                if (!world.containsColumn(bx, bz)) continue;
                int by = getSurfaceY(world, bx, bz) + 1;
                world.setBlock(new BlockPos(bx, by, bz), debris, Block.UPDATE_CLIENTS);
            }
        }
    }

    // =========================================================
    //  PASS 7 — Age the site (50 years of regrowth)
    // =========================================================
    private void ageSite(MeteorPlacementContext world, BlockPos center,
                         int craterRadius, int rimHeight,
                         SimplexNoise noise, RandomSource rng) {
        int totalRange = Math.min(world.plan.footprintRadius(), craterRadius + rimHeight + 8);

        for (int x = world.minimumX(totalRange); x <= world.maximumX(totalRange); x++) {
            for (int z = world.minimumZ(totalRange); z <= world.maximumZ(totalRange); z++) {
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
    private void carveUndergroundCave(MeteorPlacementContext world, BlockPos center,
                                       int craterRadius, int bowlDepth) {
        int caveRadius  = craterRadius / 3;
        int surfY       = center.getY() - bowlDepth - 2;
        int caveDepth   = bowlDepth + 8 + caveRadius;
        int caveCenterY = surfY - caveDepth;
        int r2          = caveRadius * caveRadius;

        // Flattened ellipsoid
        for (int x = world.minimumX(caveRadius); x <= world.maximumX(caveRadius); x++) {
            for (int y = -(caveRadius / 2); y <= caveRadius / 2; y++) {
                for (int z = world.minimumZ(caveRadius); z <= world.maximumZ(caveRadius); z++) {
                    double check = (double) x * x + (double) y * y * 4.0 + (double) z * z;
                    if (check > r2) continue;
                    world.setBlock(new BlockPos(center.getX() + x, caveCenterY + y, center.getZ() + z), AIR, Block.UPDATE_CLIENTS);
                }
            }
        }

        // 1–3 crack tunnels to the surface
        int crackCount = 1 + RandomSource.create(world.plan.seed() ^ 7L).nextInt(2);
        for (int c = 0; c < crackCount; c++) {
            double angle = c * (Math.PI * 2.0 / crackCount) + Math.PI / 6;
            int crackX = (int)(Math.cos(angle) * caveRadius * 0.5);
            int crackZ = (int)(Math.sin(angle) * caveRadius * 0.5);
            for (int width = 0; width <= 1; width++) {
                int worldX = center.getX() + crackX + width;
                int worldZ = center.getZ() + crackZ;
                if (!world.containsColumn(worldX, worldZ)) continue;
                int topY = getSurfaceY(world, worldX, worldZ);
                for (int y = caveCenterY + caveRadius / 2; y <= topY; y++) {
                    if (width == 0 || y % 3 == 0) {
                        world.setBlock(new BlockPos(worldX, y, worldZ), AIR, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    // =========================================================
    //  Helpers
    // =========================================================
    private int getSurfaceY(MeteorPlacementContext world, int x, int z) {
        return world.surfaceY(x, z);
    }

    private void setIfReplaceable(MeteorPlacementContext world, BlockPos pos, BlockState state) {
        BlockState existing = world.getBlockState(pos);
        if (existing.isAir()
            || existing.is(Blocks.GRASS_BLOCK)
            || existing.is(Blocks.DIRT)
            || existing.is(Blocks.STONE)) {
            world.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
