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
    private static final short WATER_FINALIZED_FLAG = 1;

    private int visitCount;
    private short stateFlags;
    private int upgradeVersion;
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
     * Marks the chunk's generated plain water as finalized into Wilderness
     * authority.
     */
    public void markWaterFinalized() {
        setFlag(WATER_FINALIZED_FLAG, true);
    }

    /**
     * Clears the water-finalized marker so repair or future migrations can
     * force the chunk back through the bounded seeding queue.
     */
    public void clearWaterFinalized() {
        setFlag(WATER_FINALIZED_FLAG, false);
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
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        visitCount = nbt.getInt(VISITS_TAG);
        stateFlags = nbt.getShort(FLAGS_TAG);
        upgradeVersion = nbt.getInt(UPGRADE_VERSION_TAG);
        dirty = false;
    }
}
