package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.core.ModRegistries;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentDimensionProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Persists immutable crater parameters; each postProcess call touches only its supplied chunk. */
public final class MeteorCraterPiece extends StructurePiece {

    private final MeteorCraterPlan plan;

    public MeteorCraterPiece(MeteorCraterPlan plan, int minimumY, int maximumY) {
        super(MeteorWorldgenRegistries.METEOR_CRATER_PIECE.get(), 0, plan.bounds(minimumY, maximumY));
        this.plan = plan;
    }

    public MeteorCraterPiece(CompoundTag tag) {
        super(MeteorWorldgenRegistries.METEOR_CRATER_PIECE.get(), tag);
        this.plan = new MeteorCraterPlan(new BlockPos(tag.getInt("center_x"), tag.getInt("center_y"),
                tag.getInt("center_z")), tag.getInt("radius"), tag.getInt("depth"),
                tag.getInt("rim_height"), tag.getInt("ejecta_range"), tag.getLong("crater_seed"));
    }

    public MeteorCraterPlan plan() {
        return plan;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("center_x", plan.center().getX());
        tag.putInt("center_y", plan.center().getY());
        tag.putInt("center_z", plan.center().getZ());
        tag.putInt("radius", plan.radius());
        tag.putInt("depth", plan.depth());
        tag.putInt("rim_height", plan.rimHeight());
        tag.putInt("ejecta_range", plan.ejectaRange());
        tag.putLong("crater_seed", plan.seed());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos origin) {
        if (!EnvironmentDimensionProfile.forDimension(level.getLevel().dimension()).naturalMeteors()) {
            return;
        }
        BoundingBox chunkBounds = new BoundingBox(
                Math.max(box.minX(), chunkPos.getMinBlockX()), box.minY(),
                Math.max(box.minZ(), chunkPos.getMinBlockZ()),
                Math.min(box.maxX(), chunkPos.getMaxBlockX()), box.maxY(),
                Math.min(box.maxZ(), chunkPos.getMaxBlockZ()));
        ModRegistries.METEOR_IMPACT_FEATURE.get().placeChunk(level, plan, chunkBounds);
    }
}
