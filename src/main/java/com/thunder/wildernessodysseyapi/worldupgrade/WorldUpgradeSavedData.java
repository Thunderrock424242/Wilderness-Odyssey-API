package com.thunder.wildernessodysseyapi.worldupgrade;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * World-level persistent state for the upgrade queue.
 */
public class WorldUpgradeSavedData extends SavedData {
    private static final int DATA_FORMAT_VERSION = 2;
    private static final String DATA_NAME = ModConstants.MOD_ID + "_world_upgrade";
    private static final String DATA_FORMAT_KEY = "data_format";
    private static final String TARGET_VERSION_KEY = "target_version";
    private static final String RUNNING_KEY = "running";
    private static final String PROCESSED_KEY = "processed_chunks";
    private static final String MIGRATED_KEY = "migrated_chunks";
    private static final String FAILED_KEY = "failed_chunks";
    private static final String LAST_PACK_VERSION_KEY = "last_pack_version";
    private static final String PENDING_PACK_VERSION_KEY = "pending_pack_version";

    private int targetVersion = WorldUpgradeManager.TARGET_VERSION;
    private boolean running;
    private long processedChunks;
    private long migratedChunks;
    private long failedChunks;
    private String lastPackVersion = "";
    private String pendingPackVersion = "";

    public WorldUpgradeSavedData() {
    }

    public WorldUpgradeSavedData(CompoundTag tag, HolderLookup.Provider registries) {
        int dataFormat = tag.contains(DATA_FORMAT_KEY, Tag.TAG_INT)
                ? tag.getInt(DATA_FORMAT_KEY)
                : 1;
        int storedTargetVersion = tag.getInt(TARGET_VERSION_KEY);
        this.targetVersion = Math.max(storedTargetVersion, WorldUpgradeManager.TARGET_VERSION);
        this.running = tag.getBoolean(RUNNING_KEY);
        this.processedChunks = tag.getLong(PROCESSED_KEY);
        this.migratedChunks = tag.getLong(MIGRATED_KEY);
        this.failedChunks = tag.getLong(FAILED_KEY);
        this.lastPackVersion = tag.getString(LAST_PACK_VERSION_KEY);
        this.pendingPackVersion = tag.getString(PENDING_PACK_VERSION_KEY);
        if (targetVersion != storedTargetVersion) {
            setDirty();
        }

        // Version 1 wrote the pack version before any queued chunk succeeded.
        // Reopen that value as pending so interrupted legacy upgrades resume
        // instead of being treated as complete.
        if (dataFormat < DATA_FORMAT_VERSION
                && pendingPackVersion.isBlank()
                && !lastPackVersion.isBlank()) {
            pendingPackVersion = lastPackVersion;
            lastPackVersion = "";
            running = true;
            setDirty();
        }
    }

    public static WorldUpgradeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldUpgradeSavedData::new, WorldUpgradeSavedData::new),
                DATA_NAME
        );
    }

    public int getTargetVersion() {
        return targetVersion;
    }

    /**
     * Advances the stored target when a newer migration chain is shipped.
     *
     * @param targetVersion current code-defined migration target
     */
    public void advanceTargetVersion(int targetVersion) {
        int advanced = Math.max(this.targetVersion, targetVersion);
        if (advanced != this.targetVersion) {
            this.targetVersion = advanced;
            setDirty();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        if (this.running != running) {
            this.running = running;
            setDirty();
        }
    }

    public long getProcessedChunks() {
        return processedChunks;
    }

    public long getMigratedChunks() {
        return migratedChunks;
    }

    public long getFailedChunks() {
        return failedChunks;
    }

    public String getLastPackVersion() {
        return lastPackVersion;
    }

    /**
     * Returns the pack version whose migrations are still in progress.
     */
    public String getPendingPackVersion() {
        return pendingPackVersion;
    }

    public boolean hasPendingPackVersion() {
        return !pendingPackVersion.isBlank();
    }

    public boolean shouldRunForPackVersion(String currentVersion) {
        return currentVersion != null
                && !currentVersion.isBlank()
                && !currentVersion.equals(lastPackVersion)
                && !currentVersion.equals(pendingPackVersion);
    }

    /**
     * Starts a new durable rollout without claiming it has completed.
     *
     * @param currentVersion pack version being migrated
     */
    public void beginPackUpgrade(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return;
        }
        pendingPackVersion = currentVersion;
        running = true;
        resetCounters();
        setDirty();
    }

    /**
     * Commits the pending pack version only after the operator has observed an
     * empty successful queue.
     *
     * @return {@code true} when a pending rollout was committed
     */
    public boolean completePendingPackUpgrade() {
        if (pendingPackVersion.isBlank()) {
            return false;
        }
        lastPackVersion = pendingPackVersion;
        pendingPackVersion = "";
        setDirty();
        return true;
    }

    public void resetCounters() {
        processedChunks = 0L;
        migratedChunks = 0L;
        failedChunks = 0L;
        setDirty();
    }

    /** Clears unresolved failures after an operator requests a retry pass. */
    public void resetFailedChunks() {
        if (failedChunks != 0L) {
            failedChunks = 0L;
            setDirty();
        }
    }

    public void onChunkProcessed(boolean migrated, boolean failed) {
        this.processedChunks++;
        if (migrated) {
            this.migratedChunks++;
        }
        if (failed) {
            this.failedChunks++;
        }
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putInt(DATA_FORMAT_KEY, DATA_FORMAT_VERSION);
        tag.putInt(TARGET_VERSION_KEY, targetVersion);
        tag.putBoolean(RUNNING_KEY, running);
        tag.putLong(PROCESSED_KEY, processedChunks);
        tag.putLong(MIGRATED_KEY, migratedChunks);
        tag.putLong(FAILED_KEY, failedChunks);
        tag.putString(LAST_PACK_VERSION_KEY, lastPackVersion);
        tag.putString(PENDING_PACK_VERSION_KEY, pendingPackVersion);
        return tag;
    }
}
