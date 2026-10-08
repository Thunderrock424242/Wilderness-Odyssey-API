package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.mojang.serialization.MapCodec;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentDimensionProfile;
import com.thunder.wildernessodysseyapi.temporalrift.worldgen.BeforeChunkGenerator;
import com.thunder.wildernessodysseyapi.temporalrift.worldgen.EchoChunkGenerator;
import com.thunder.wildernessodysseyapi.worldgen.config.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/** Natural large crater placement whose world writes are partitioned by Minecraft's structure pipeline. */
public final class MeteorCraterStructure extends Structure {

    public static final MapCodec<MeteorCraterStructure> CODEC = simpleCodec(MeteorCraterStructure::new);

    public MeteorCraterStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Natural generation supplies a ProtoChunk as its height accessor, so
        // dimension-specific generators must be rejected before creating starts.
        if (context.chunkGenerator() instanceof BeforeChunkGenerator
                || context.chunkGenerator() instanceof EchoChunkGenerator
                || StructureConfig.DEBUG_DISABLE_IMPACT_SITES.get()) {
            return Optional.empty();
        }
        if (context.heightAccessor() instanceof ServerLevel level
                && !EnvironmentDimensionProfile.forDimension(level.dimension()).naturalMeteors()) {
            return Optional.empty();
        }
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        if (y <= context.heightAccessor().getMinBuildHeight() + 40) {
            return Optional.empty();
        }
        MeteorCraterPlan plan = MeteorCraterPlan.create(new BlockPos(x, y, z), context.random());
        return Optional.of(new GenerationStub(plan.center(), pieces -> pieces.addPiece(
                new MeteorCraterPiece(plan, context.heightAccessor().getMinBuildHeight(),
                        context.heightAccessor().getMaxBuildHeight() - 1))));
    }

    @Override
    public StructureType<?> type() {
        return MeteorWorldgenRegistries.METEOR_CRATER.get();
    }
}
