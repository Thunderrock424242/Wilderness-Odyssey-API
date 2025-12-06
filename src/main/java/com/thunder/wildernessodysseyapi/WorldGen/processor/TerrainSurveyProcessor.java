package com.thunder.wildernessodysseyapi.WorldGen.processor;

import com.mojang.serialization.MapCodec;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.TerrainReplacerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Processor that replaces placeholder blocks with sampled terrain and strips wool survey markers
 * after the structure has been placed by vanilla worldgen.
 */
public class TerrainSurveyProcessor extends StructureProcessor {
    /** Codec used by datapacks. */
    public static final MapCodec<TerrainSurveyProcessor> CODEC = MapCodec.unit(TerrainSurveyProcessor::new);

    @Override
    protected StructureProcessorType<?> getType() {
        return ModProcessors.TERRAIN_SURVEY.get();
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level,
                                                             BlockPos pos,
                                                             BlockPos pivot,
                                                             StructureTemplate.StructureBlockInfo raw,
                                                             StructureTemplate.StructureBlockInfo placed,
                                                             StructurePlaceSettings settings) {
        BlockState state = placed.state();

        // Remove wool survey pillars once the structure has been aligned to the terrain heightmap.
        if (state.is(Blocks.BLUE_WOOL)) {
            return new StructureTemplate.StructureBlockInfo(placed.pos(), Blocks.AIR.defaultBlockState(), placed.nbt());
        }

        // Replace terrain markers with the local surface block to blend the footprint into the biome.
        if (state.is(TerrainReplacerBlock.TERRAIN_REPLACER.get())) {
            BlockPos target = placed.pos();
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, target.getX(), target.getZ()) - 1;
            BlockPos surfacePos = new BlockPos(target.getX(), surfaceY, target.getZ());
            BlockState sampled = level.getBlockState(surfacePos);

            if (sampled.isAir() && surfaceY > level.getMinBuildHeight()) {
                sampled = level.getBlockState(surfacePos.below());
            }

            if (sampled.isAir()) {
                sampled = Blocks.DIRT.defaultBlockState();
            }

            return new StructureTemplate.StructureBlockInfo(target, sampled, placed.nbt());
        }

        return placed;
    }
}
