package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiverGenerationChunkTest {
    @Test
    void preDecorationTerrainSurvivesSaveAndDoesNotAliasItsInputs() {
        int[] terrain = new int[512];
        for (int i = 0; i < 256; i++) {
            terrain[i * 2] = 140;
            terrain[i * 2 + 1] = 0xFFFF;
        }
        var data = RiverGenerationChunk.captured(17, terrain);
        terrain[0] = 1;
        var restored = new RiverGenerationChunk();
        restored.deserializeNBT(null, data.serializeNBT(null));
        assertTrue(restored.hasTerrain(17));
        assertEquals(140, restored.height(0, 0));
        assertTrue(restored.naturalAt(0, 139, 0));
        assertFalse(restored.naturalAt(0, 141, 0));
    }

    @Test
    void completedMetadataDropsCaptureButRetainsExactReachIdentity() {
        int[] terrain = new int[512];
        var data = RiverGenerationChunk.captured(17, terrain);
        data.complete(91, 0, 0, List.of(
                new RiverChannelColumn(3, 4, 140, 138, 140, 1, 0, 1.2f, RiverChannelColumn.Kind.STREAM)));
        var tag = data.serializeNBT(null);
        assertFalse(data.hasTerrain(17));
        assertFalse(tag.contains("terrain"));
        var restored = new RiverGenerationChunk();
        restored.deserializeNBT(null, tag);
        assertTrue(restored.processed());
        assertEquals(91, restored.basinId());
        var flow = restored.reachAt(3, 4);
        assertNotNull(flow);
        assertEquals(140, flow.waterY());
        assertEquals(138, flow.floorY());
        assertEquals(1.2f, flow.speed(), 0.04f);
        assertTrue(flow.directionX() > 0.99f);
        assertNull(restored.reachAt(0, 0));
    }

    @Test
    void unknownOrTruncatedSavedTerrainIsRejected() {
        var malformed = new CompoundTag();
        malformed.putInt("format", 999);
        assertThrows(IllegalArgumentException.class,
                () -> new RiverGenerationChunk().deserializeNBT(null, malformed));
        malformed.putInt("format", 1);
        malformed.putIntArray("terrain", new int[511]);
        assertThrows(IllegalArgumentException.class,
                () -> new RiverGenerationChunk().deserializeNBT(null, malformed));
    }

    @Test
    void readOnlySnapshotSurvivesLaterAttachmentReplacement() {
        var data = RiverGenerationChunk.captured(17, new int[512]);
        data.complete(91, 0, 0, List.of(
                new RiverChannelColumn(3, 4, 140, 138, 140, 1, 0, 1.2f, RiverChannelColumn.Kind.STREAM)));
        var snapshot = data.snapshot();
        data.complete(0, 0, 0, List.of());
        assertEquals(91, snapshot.basinId());
        assertNotNull(snapshot.reachAt(3, 4));
        assertNull(data.reachAt(3, 4));
    }
}
