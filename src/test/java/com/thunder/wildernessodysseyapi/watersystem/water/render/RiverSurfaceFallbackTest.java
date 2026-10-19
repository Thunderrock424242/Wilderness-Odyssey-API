package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiverSurfaceFallbackTest {
    @Test
    void verticalFallingWaterKeepsTheNativeFluidGeometry() {
        var generated = new GeneratedWaterChunk();
        generated.recordCell(new BlockPos(2, 140, 3), GeneratedWaterChunk.Cell.of(8, true, GeneratedWaterChunk.BodyType.RIVER));
        var snapshot = new ClientWaterChunkSnapshot(0, 0, generated.snapshot(), 0, new int[0]);
        assertTrue(snapshot.column(2, 3).falling());
        assertFalse(WaterChunkMeshCache.usesCustomSurface(snapshot.column(2, 3)),
                "A falling curtain must not become a horizontal wave sheet");
    }
}
