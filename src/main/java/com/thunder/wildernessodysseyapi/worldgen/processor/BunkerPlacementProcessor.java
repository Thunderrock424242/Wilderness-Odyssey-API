package com.thunder.wildernessodysseyapi.worldgen.processor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

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
    public StructureTemplate.StructureBlockInfo process(LevelReader level,
                                                        BlockPos pos,
                                                        BlockPos pivot,
                                                        StructureTemplate.StructureBlockInfo raw,
                                                        StructureTemplate.StructureBlockInfo placed,
                                                        StructurePlaceSettings settings,
                                                        @Nullable StructureTemplate template) {
        if (placed.state().isAir()) {
            BoundingBox bounds = settings.getBoundingBox();
            if (bounds == null || isBoundary(pos, bounds)) {
                return null;
            }
        }

        // A template can retain block-entity NBT after a missing or replaced
        // modded block resolves to ordinary terrain. Strip that stale payload
        // before LevelChunk tries to instantiate a DUMMY block entity for air
        // or stone during placement and later chunk saves.
        if (shouldStripBlockEntityData(placed.nbt() != null, placed.state().hasBlockEntity())) {
            return new StructureTemplate.StructureBlockInfo(placed.pos(), placed.state(), null);
        }
        return placed;
    }

    static boolean shouldStripBlockEntityData(boolean hasBlockEntityData, boolean blockSupportsBlockEntity) {
        return hasBlockEntityData && !blockSupportsBlockEntity;
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
