package com.thunder.wildernessodysseyapi.meteor.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** One placement call's bounded access; no mutable state is retained by the registered feature. */
final class MeteorPlacementContext {

    final WorldGenLevel level;
    final MeteorCraterPlan plan;
    final BoundingBox clip;

    MeteorPlacementContext(WorldGenLevel level, MeteorCraterPlan plan, BoundingBox clip) {
        this.level = level;
        this.plan = plan;
        this.clip = clip;
    }

    int minimumX(int radius) {
        return Math.max(-radius, clip.minX() - plan.center().getX());
    }

    int maximumX(int radius) {
        return Math.min(radius, clip.maxX() - plan.center().getX());
    }

    int minimumZ(int radius) {
        return Math.max(-radius, clip.minZ() - plan.center().getZ());
    }

    int maximumZ(int radius) {
        return Math.min(radius, clip.maxZ() - plan.center().getZ());
    }

    boolean containsColumn(int x, int z) {
        int extent = plan.footprintRadius();
        return x >= clip.minX() && x <= clip.maxX() && z >= clip.minZ() && z <= clip.maxZ()
                && Math.abs((long) x - plan.center().getX()) <= extent
                && Math.abs((long) z - plan.center().getZ()) <= extent;
    }

    int surfaceY(int x, int z) {
        if (!containsColumn(x, z)) {
            throw new IllegalArgumentException("Meteor surface query outside its placement chunk");
        }
        // CARVERS/FEATURES and loaded chunks update the final heightmaps after
        // block writes. The generation-only map would retain the pre-impact roof.
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    BlockState getBlockState(BlockPos position) {
        if (!containsColumn(position.getX(), position.getZ())) {
            throw new IllegalArgumentException("Meteor block query outside its placement chunk");
        }
        return level.getBlockState(position);
    }

    boolean setBlock(BlockPos position, BlockState state, int flags) {
        return containsColumn(position.getX(), position.getZ())
                && clip.isInside(position)
                && position.getY() >= level.getMinBuildHeight() + 1
                && position.getY() < level.getMaxBuildHeight()
                && level.ensureCanWrite(position)
                && level.setBlock(position, state, flags);
    }
}
