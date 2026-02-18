package com.thunder.wildernessodysseyapi.ModPackPatches.worldupgrade;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * World-level persistent state for the upgrade queue.
 */
public class WorldUpgradeSavedData extends SavedData {
    private static final String DATA_NAME = ModConstants.MOD_ID + "_world_upgrade";
    private static final String TARGET_VERSION_KEY = "target_version";
    private static final String RUNNING_KEY = "running";
    private static final String PROCESSED_KEY = "processed_chunks";
    private static final String MIGRATED_KEY = "migrated_chunks";
    private static final String FAILED_KEY = "failed_chunks";
    private static final String LAST_PACK_VERSION_KEY = "last_pack_version";

    private int targetVersion = WorldUpgradeManager.TARGET_VERSION;
    private boolean running;
    private long processedChunks;
    private long migratedChunks;
    private long failedChunks;
    private String lastPackVersion = "";

    public WorldUpgradeSavedData() {
    }

    public WorldUpgradeSavedData(CompoundTag tag, HolderLookup.Provider registries) {
        this.targetVersion = tag.getInt(TARGET_VERSION_KEY);
        this.running = tag.getBoolean(RUNNING_KEY);
        this.processedChunks = tag.getLong(PROCESSED_KEY);
        this.migratedChunks = tag.getLong(MIGRATED_KEY);
        this.failedChunks = tag.getLong(FAILED_KEY);
        this.lastPackVersion = tag.getString(LAST_PACK_VERSION_KEY);
        if (this.targetVersion <= 0) {
            this.targetVersion = WorldUpgradeManager.TARGET_VERSION;
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

    public boolean shouldRunForPackVersion(String currentVersion) {
        return currentVersion != null && !currentVersion.equals(lastPackVersion);
    }

    public void markPackVersionProcessed(String currentVersion) {
        if (currentVersion == null) {
            return;
        }
        if (!currentVersion.equals(this.lastPackVersion)) {
            this.lastPackVersion = currentVersion;
            setDirty();
        }
    }

    public void resetCounters() {
        processedChunks = 0L;
        migratedChunks = 0L;
        failedChunks = 0L;
        setDirty();
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
        tag.putInt(TARGET_VERSION_KEY, targetVersion);
        tag.putBoolean(RUNNING_KEY, running);
        tag.putLong(PROCESSED_KEY, processedChunks);
        tag.putLong(MIGRATED_KEY, migratedChunks);
        tag.putLong(FAILED_KEY, failedChunks);
        tag.putString(LAST_PACK_VERSION_KEY, lastPackVersion);
        return tag;
    }
}
