package com.thunder.wildernessodysseyapi.WorldGen.processor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Prevents the bunker template from clearing non-dirt blocks when placing air inside the structure.
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
        BlockState state = placed.state();
        if (!state.isAir()) {
            return placed;
        }

        BlockState existing = level.getBlockState(placed.pos());
        if (existing.isAir() || existing.is(Blocks.DIRT)) {
            return placed;
        }

        return null;
    }
}
