package com.thunder.wildernessodysseyapi.temporalrift.echo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EchoChunkDataTest {
    private static final ResourceLocation REMNANT = ResourceLocation.parse("minecraft:weathered_copper");

    @Test
    void remnantEvidenceIsBoundedAndSurvivesChunkSave() {
        var data = new EchoChunkData();
        var dirty = new AtomicInteger();
        data.setDirtyListener(dirty::incrementAndGet);
        for (int i = 0; i < 40; i++) data.record(new BlockPos(0, 64 + i, 0), REMNANT);
        assertEquals(EchoChunkData.MAX_MARKERS, data.markers().size());
        assertEquals(40, dirty.get());
        var restored = new EchoChunkData();
        restored.deserializeNBT(null, data.serializeNBT(null));
        assertEquals(data.markers(), restored.markers());
        assertFalse(restored.playerModified());
    }

    @Test
    void playerEditProtectionSurvivesReloadAndRemovesEvidenceAtEditedBlock() {
        var data = new EchoChunkData();
        var edited = new BlockPos(5, 64, 5);
        data.record(edited, REMNANT);
        data.markPlayerEdited(edited);
        var restored = new EchoChunkData();
        restored.deserializeNBT(null, data.serializeNBT(null));
        assertTrue(restored.playerModified());
        assertTrue(restored.markers().isEmpty());
    }

    @Test
    void newEditsProtectOnlyTheirPositionsAndSurviveSaveReload() {
        var data = new EchoChunkData();
        var edited = new BlockPos(5, 64, 5);
        var unrelated = new BlockPos(7, 64, 5);
        data.markPlayerEdited(edited);
        assertTrue(data.protects(edited));
        assertFalse(data.protects(unrelated));
        var restored = new EchoChunkData();
        restored.deserializeNBT(null, data.serializeNBT(null));
        assertTrue(restored.protects(edited));
        assertFalse(restored.protects(unrelated));
    }

    @Test
    void legacyChunkProtectionIsNeverDowngradedOnReload() {
        var oldTag = new net.minecraft.nbt.CompoundTag();
        oldTag.putBoolean("player_modified", true);
        var data = new EchoChunkData();
        data.deserializeNBT(null, oldTag);
        assertTrue(data.protects(new BlockPos(0, 64, 0)));
        assertTrue(data.protects(new BlockPos(15, 100, 15)));
        var restored = new EchoChunkData();
        restored.deserializeNBT(null, data.serializeNBT(null));
        assertTrue(restored.protects(new BlockPos(15, 100, 15)));
    }

    @Test
    void overflowingSparseProtectionConservativelyProtectsWholeChunk() {
        var data = new EchoChunkData();
        for (int i = 0; i <= EchoChunkData.MAX_PROTECTED_POSITIONS; i++) {
            data.markPlayerEdited(new BlockPos(i, 64, 0));
        }
        assertTrue(data.protects(new BlockPos(10, 64, 10)));
        var restored = new EchoChunkData();
        restored.deserializeNBT(null, data.serializeNBT(null));
        assertTrue(restored.protects(new BlockPos(10, 64, 10)));
    }

    @Test
    void fractureLedgerCoalescesChunksAndEvictsOnlyOldestSitesAtCapacity() {
        var data = new EchoFractureSavedData();
        data.record(new BlockPos(0, 64, 0));
        data.record(new BlockPos(1, 60, 1));
        assertEquals(1, data.sites().size());
        for (int i = 1; i < 200; i++) data.record(new BlockPos(i * 16, 64, 0));
        assertEquals(EchoFractureSavedData.MAX_SITES, data.sites().size());
        assertFalse(data.sites().contains(new BlockPos(0, 64, 0)));
        assertTrue(data.sites().contains(new BlockPos(199 * 16, 64, 0)));
    }
}
