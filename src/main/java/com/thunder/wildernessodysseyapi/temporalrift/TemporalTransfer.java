package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class TemporalTransfer {
    private final BlockPos sourcePos;
    private final BlockPos targetPos;
    private final long sealDay;
    private final long deliveryDay;
    private final String ownerName;
    private final CompoundTag data;
    private boolean failed;

    public TemporalTransfer(BlockPos sourcePos, BlockPos targetPos, long sealDay, long deliveryDay, String ownerName, CompoundTag data) {
        this.sourcePos = sourcePos.immutable();
        this.targetPos = targetPos.immutable();
        this.sealDay = sealDay;
        this.deliveryDay = deliveryDay;
        this.ownerName = ownerName;
        this.data = data.copy();
    }

    public BlockPos getSourcePos() {
        return sourcePos;
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public long getSealDay() {
        return sealDay;
    }

    public long getDeliveryDay() {
        return deliveryDay;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public CompoundTag getData() {
        return data.copy();
    }

    public boolean isFailed() {
        return failed;
    }

    public void markFailed() {
        failed = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("srcX", sourcePos.getX());
        tag.putInt("srcY", sourcePos.getY());
        tag.putInt("srcZ", sourcePos.getZ());
        tag.putInt("tgtX", targetPos.getX());
        tag.putInt("tgtY", targetPos.getY());
        tag.putInt("tgtZ", targetPos.getZ());
        tag.putLong("sealDay", sealDay);
        tag.putLong("deliveryDay", deliveryDay);
        tag.putString("ownerName", ownerName);
        tag.put("data", data.copy());
        tag.putBoolean("failed", failed);
        return tag;
    }

    public static TemporalTransfer load(CompoundTag tag) {
        BlockPos source = new BlockPos(tag.getInt("srcX"), tag.getInt("srcY"), tag.getInt("srcZ"));
        BlockPos target = new BlockPos(tag.getInt("tgtX"), tag.getInt("tgtY"), tag.getInt("tgtZ"));
        CompoundTag data = tag.contains("data") ? tag.getCompound("data") : new CompoundTag();
        long deliveryDay = tag.getLong("deliveryDay");
        long sealDay = tag.contains("sealDay") ? tag.getLong("sealDay") : deliveryDay;
        TemporalTransfer transfer = new TemporalTransfer(source, target, sealDay, deliveryDay, tag.getString("ownerName"), data);
        if (tag.getBoolean("failed")) {
            transfer.markFailed();
        }
        return transfer;
    }
}
