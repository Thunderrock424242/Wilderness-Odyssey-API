package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureWaterMarkerAdapterTest {

    @Test
    void explicitStructureWaterMarkersDefaultOn() {
        assertTrue(WaterSimulationConfig.ENABLE_STRUCTURE_WATER_MARKERS.getDefault());
    }

    @Test
    void recognizesOnlyExplicitDataModeMarker() {
        CompoundTag tag = new CompoundTag();
        tag.putString("metadata", StructureWaterMarkerAdapter.WATER_MARKER);
        StructureTemplate.StructureBlockInfo marker = new StructureTemplate.StructureBlockInfo(
                BlockPos.ZERO,
                Blocks.STRUCTURE_BLOCK.defaultBlockState()
                        .setValue(StructureBlock.MODE, StructureMode.DATA),
                tag
        );
        StructureTemplate.StructureBlockInfo saveBlock = new StructureTemplate.StructureBlockInfo(
                BlockPos.ZERO,
                Blocks.STRUCTURE_BLOCK.defaultBlockState()
                        .setValue(StructureBlock.MODE, StructureMode.SAVE),
                tag
        );

        assertTrue(StructureWaterMarkerAdapter.isWaterMarker(marker));
        assertFalse(StructureWaterMarkerAdapter.isWaterMarker(saveBlock));
    }

    @Test
    void leavesMarkerFreeProcessedListUnallocated() {
        List<StructureTemplate.StructureBlockInfo> processed = List.of(
                new StructureTemplate.StructureBlockInfo(
                        BlockPos.ZERO,
                        Blocks.STONE.defaultBlockState(),
                        null
                )
        );

        assertSame(processed, StructureWaterMarkerAdapter.convertMarkers(processed));
    }
}
