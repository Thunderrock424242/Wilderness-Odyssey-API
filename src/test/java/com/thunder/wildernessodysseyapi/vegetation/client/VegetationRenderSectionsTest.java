package com.thunder.wildernessodysseyapi.vegetation.client;

import com.thunder.wildernessodysseyapi.vegetation.VegetationChunkFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VegetationRenderSectionsTest {
    @Test
    void invalidatesTheTintedGroundBelowAnOpaqueRoofAndSkipsEmptySections() {
        var chunk = VegetationChunkFixture.create();
        chunk.setBlockState(new BlockPos(8, 0, 8), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 80, 8), Blocks.STONE.defaultBlockState(), false);
        List<Integer> invalidated = new ArrayList<>();

        ReactiveVegetationClientEvents.forEachOccupiedSection(chunk, invalidated::add);

        assertEquals(List.of(0, 5), invalidated);
    }
}
