package com.thunder.wildernessodysseyapi.WorldGen.processor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Ensures air blocks from the bunker template are placed so the interior stays clear.
 */
public class BunkerPlacementProcessor extends StructureProcessor {
    public static final MapCodec<BunkerPlacementProcessor> CODEC = MapCodec.unit(BunkerPlacementProcessor::new);

    @Override
    protected StructureProcessorType<?> getType() {
        return ModProcessors.BUNKER_PLACEMENT.get();
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level,
                                                             BlockPos pos,
                                                             BlockPos pivot,
                                                             StructureTemplate.StructureBlockInfo raw,
                                                             StructureTemplate.StructureBlockInfo placed,
                                                             StructurePlaceSettings settings) {
        if (placed.state().isAir()) {
            BlockState existing = level.getBlockState(pos);
            if (!shouldClear(existing)) {
                return null;
            }
        }
        return placed;
    }

    private static boolean shouldClear(BlockState existing) {
        if (existing.isAir()) {
            return true;
        }
        if (!existing.getFluidState().isEmpty()) {
            return true;
        }
        return existing.is(BlockTags.DIRT);
    }
}
