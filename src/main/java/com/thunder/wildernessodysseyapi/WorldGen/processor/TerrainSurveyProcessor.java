package com.thunder.wildernessodysseyapi.WorldGen.processor;

import com.mojang.serialization.MapCodec;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.TerrainReplacerBlock;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.WorldGen.structure.TerrainReplacerEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
        if (!StructureConfig.ENABLE_TERRAIN_REPLACER.get()) {
            return placed;
        }

        if (state.is(TerrainReplacerBlock.TERRAIN_REPLACER.get())) {
            BoundingBox bounds = settings.getBoundingBox();
            if (isInteriorToBounds(bounds, placed.pos())) {
                return new StructureTemplate.StructureBlockInfo(placed.pos(), Blocks.AIR.defaultBlockState(), placed.nbt());
            }
            var material = TerrainReplacerEngine.sampleSurfaceMaterialOutsideBounds(level, placed.pos(), bounds);
            BlockState sampled = TerrainReplacerEngine.chooseReplacement(material, placed.pos().getY());
            return new StructureTemplate.StructureBlockInfo(placed.pos(), sampled, placed.nbt());
        }

        return placed;
    }

    private boolean isInteriorToBounds(BoundingBox bounds, BlockPos worldPos) {
        if (bounds == null) {
            return false;
        }
        return worldPos.getX() > bounds.minX()
                && worldPos.getX() < bounds.maxX()
                && worldPos.getY() > bounds.minY()
                && worldPos.getY() < bounds.maxY()
                && worldPos.getZ() > bounds.minZ()
                && worldPos.getZ() < bounds.maxZ();
    }
}
