package com.thunder.wildernessodysseyapi.worldupgrade;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUpgradeSavedDataTest {

    @Test
    void legacyEarlyCommitIsReopenedAsPendingWork() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("target_version", 1);
        legacy.putBoolean("running", false);
        legacy.putString("last_pack_version", "4.2.0");

        WorldUpgradeSavedData restored = new WorldUpgradeSavedData(legacy, null);

        assertTrue(restored.isRunning());
        assertEquals("", restored.getLastPackVersion());
        assertEquals("4.2.0", restored.getPendingPackVersion());
    }

    @Test
    void formatTwoCompletedUpgradeRemainsCompletedWhenMetadataFormatAdvances() {
        CompoundTag completed = new CompoundTag();
        completed.putInt("data_format", 2);
        completed.putInt("target_version", 1);
        completed.putBoolean("running", false);
        completed.putString("last_pack_version", "4.2.0");

        WorldUpgradeSavedData restored = new WorldUpgradeSavedData(completed, null);

        assertFalse(restored.isRunning());
        assertEquals("4.2.0", restored.getLastPackVersion());
        assertEquals("", restored.getPendingPackVersion());
    }

    @Test
    void storedTargetNeverMovesBehindTheCurrentMigrationChain() {
        CompoundTag stored = new CompoundTag();
        stored.putInt("data_format", 2);
        stored.putInt("target_version", 0);

        WorldUpgradeSavedData restored = new WorldUpgradeSavedData(stored, null);
        CompoundTag encoded = restored.save(new CompoundTag(), null);

        assertEquals(WorldUpgradeManager.TARGET_VERSION, restored.getTargetVersion());
        assertEquals(WorldUpgradeManager.TARGET_VERSION, encoded.getInt("target_version"));
    }

    @Test
    void packVersionCommitsOnlyThroughExplicitCompletion() {
        WorldUpgradeSavedData state = new WorldUpgradeSavedData();

        state.beginPackUpgrade("4.3.0");

        assertEquals("", state.getLastPackVersion());
        assertEquals("4.3.0", state.getPendingPackVersion());
        assertTrue(state.completePendingPackUpgrade());
        assertEquals("4.3.0", state.getLastPackVersion());
        assertEquals("", state.getPendingPackVersion());
        assertFalse(state.completePendingPackUpgrade());
    }

    @Test
    void retryAcknowledgementClearsOnlyTheFailureCounter() {
        WorldUpgradeSavedData state = new WorldUpgradeSavedData();
        state.beginPackUpgrade("4.3.0");
        state.onChunkProcessed(false, true);

        state.resetFailedChunks();

        assertEquals(1L, state.getProcessedChunks());
        assertEquals(0L, state.getMigratedChunks());
        assertEquals(0L, state.getFailedChunks());
        assertEquals("4.3.0", state.getPendingPackVersion());
    }

    @Test
    void completionRequiresAnActiveEmptySuccessfulRollout() {
        assertTrue(WorldUpgradeManager.canComplete(true, 0, 0L, true));
        assertFalse(WorldUpgradeManager.canComplete(false, 0, 0L, true));
        assertFalse(WorldUpgradeManager.canComplete(true, 1, 0L, true));
        assertFalse(WorldUpgradeManager.canComplete(true, 0, 1L, true));
        assertFalse(WorldUpgradeManager.canComplete(true, 0, 0L, false));
    }
}
