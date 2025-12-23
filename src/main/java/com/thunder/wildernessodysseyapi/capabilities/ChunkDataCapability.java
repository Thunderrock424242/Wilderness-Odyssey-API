package com.thunder.wildernessodysseyapi.capabilities;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Simple per-chunk data container for Wilderness Odyssey state.
 * Uses primitive fields to keep storage shallow and efficient.
 */
public class ChunkDataCapability implements INBTSerializable<CompoundTag> {

    private static final String VISITS_TAG = "Visits";
    private static final String FLAGS_TAG = "Flags";

    private int visitCount;
    private short stateFlags;
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

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VISITS_TAG, visitCount);
        tag.putShort(FLAGS_TAG, stateFlags);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        visitCount = nbt.getInt(VISITS_TAG);
        stateFlags = nbt.getShort(FLAGS_TAG);
        dirty = false;
    }
}
