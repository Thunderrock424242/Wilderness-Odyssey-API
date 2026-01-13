package com.thunder.wildernessodysseyapi.WorldGen.processor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Prevents air blocks from the bunker template from replacing existing terrain.
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
            BoundingBox bounds = settings.getBoundingBox();
            if (bounds == null || isBoundary(pos, bounds)) {
                return null;
            }
        }
        return placed;
    }

    private boolean isBoundary(BlockPos pos, BoundingBox bounds) {
        return pos.getX() == bounds.minX()
                || pos.getX() == bounds.maxX()
                || pos.getY() == bounds.minY()
                || pos.getY() == bounds.maxY()
                || pos.getZ() == bounds.minZ()
                || pos.getZ() == bounds.maxZ();
    }
}
