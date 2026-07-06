package com.thunder.wildernessodysseyapi.capabilities;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Simple per-chunk data container for Wilderness Odyssey state.
 * Uses primitive fields to keep storage shallow and efficient.
 */
public class ChunkDataCapability implements INBTSerializable<CompoundTag> {

    private static final String VISITS_TAG = "Visits";
    private static final String FLAGS_TAG = "Flags";
    private static final String UPGRADE_VERSION_TAG = "UpgradeVersion";
    private static final String WATER_SYSTEM_VERSION_TAG = "WaterSystemVersion";
    private static final short WATER_FINALIZED_FLAG = 1;

    private int visitCount;
    private short stateFlags;
    private int upgradeVersion;
    private int waterSystemVersion;
    private boolean dirty;
    private Runnable dirtyListener = () -> {};

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {} : dirtyListener;
    }

    private void markDirty() {
        dirty = true;
        dirtyListener.run();
    }

    public int getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(int visitCount) {
        if (this.visitCount != visitCount) {
            this.visitCount = visitCount;
            markDirty();
        }
    }

    public void incrementVisits() {
        visitCount++;
        markDirty();
    }

    public short getStateFlags() {
        return stateFlags;
    }

    public void setStateFlags(short stateFlags) {
        if (this.stateFlags != stateFlags) {
            this.stateFlags = stateFlags;
            markDirty();
        }
    }

    /**
     * Returns whether this chunk has completed the bounded worldgen-water
     * finalization pass.
     *
     * <p>The water system uses this as its persistent handoff marker: once a
     * chunk has been scanned through all X/Z columns and any accepted plain
     * {@code minecraft:water} conversions have been completed, normal chunk
     * load/watch hooks can skip it instead of rescanning forever.</p>
     */
    public boolean isWaterFinalized() {
        return hasFlag(WATER_FINALIZED_FLAG);
    }

    /**
     * Returns whether this chunk was finalized by the current water-system
     * conversion version.
     */
    public boolean isWaterFinalized(int currentWaterSystemVersion) {
        return isWaterFinalized() && waterSystemVersion >= currentWaterSystemVersion;
    }

    /**
     * Marks the chunk's generated plain water as finalized into Wilderness
     * authority.
     */
    public void markWaterFinalized() {
        setFlag(WATER_FINALIZED_FLAG, true);
    }

    /**
     * Marks the chunk finalized and stores the water-system conversion version
     * that produced the canonical data.
     */
    public void markWaterFinalized(int currentWaterSystemVersion) {
        setWaterSystemVersion(currentWaterSystemVersion);
        markWaterFinalized();
    }

    /**
     * Clears the water-finalized marker so repair or future migrations can
     * force the chunk back through the bounded seeding queue.
     */
    public void clearWaterFinalized() {
        setFlag(WATER_FINALIZED_FLAG, false);
        setWaterSystemVersion(0);
    }

    /** Returns the water-system conversion version stored on this chunk. */
    public int getWaterSystemVersion() {
        return waterSystemVersion;
    }

    /** Updates the stored water-system conversion version. */
    public void setWaterSystemVersion(int waterSystemVersion) {
        if (this.waterSystemVersion != waterSystemVersion) {
            this.waterSystemVersion = waterSystemVersion;
            markDirty();
        }
    }

    public int getUpgradeVersion() {
        return upgradeVersion;
    }

    public void setUpgradeVersion(int upgradeVersion) {
        if (this.upgradeVersion != upgradeVersion) {
            this.upgradeVersion = upgradeVersion;
            markDirty();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    private boolean hasFlag(short flag) {
        return (stateFlags & flag) != 0;
    }

    private void setFlag(short flag, boolean enabled) {
        short nextFlags = enabled
                ? (short) (stateFlags | flag)
                : (short) (stateFlags & ~flag);
        setStateFlags(nextFlags);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VISITS_TAG, visitCount);
        tag.putShort(FLAGS_TAG, stateFlags);
        tag.putInt(UPGRADE_VERSION_TAG, upgradeVersion);
        tag.putInt(WATER_SYSTEM_VERSION_TAG, waterSystemVersion);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        visitCount = nbt.getInt(VISITS_TAG);
        stateFlags = nbt.getShort(FLAGS_TAG);
        upgradeVersion = nbt.getInt(UPGRADE_VERSION_TAG);
        waterSystemVersion = nbt.getInt(WATER_SYSTEM_VERSION_TAG);
        dirty = false;
    }
}
