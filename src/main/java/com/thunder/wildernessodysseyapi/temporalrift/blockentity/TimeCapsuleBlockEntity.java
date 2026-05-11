package com.thunder.wildernessodysseyapi.temporalrift.blockentity;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TimeCapsuleBlockEntity extends BlockEntity {
    private boolean sealed;
    private String ownerName = "";
    private long sealedOnDay;
    private CompoundTag storedData = new CompoundTag();

    public TimeCapsuleBlockEntity(BlockPos pos, BlockState state) {
        super(TemporalRiftBlockEntities.TIME_CAPSULE.get(), pos, state);
    }

    public void seal(ServerPlayer player, ItemStack payload) {
        sealed = true;
        ownerName = player.getName().getString();
        if (level != null) {
            sealedOnDay = level.getGameTime() / 24000L;
        }

        if (!payload.isEmpty()) {
            storedData.put("heldItem", payload.save(player.registryAccess()));
        }
        setChanged();
    }

    public boolean isSealed() {
        return sealed;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getSealedOnDay() {
        return sealedOnDay;
    }

    public CompoundTag getStoredData() {
        return storedData.copy();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("sealed", sealed);
        tag.putString("ownerName", ownerName);
        tag.putLong("sealedOnDay", sealedOnDay);
        tag.put("storedData", storedData.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sealed = tag.getBoolean("sealed");
        ownerName = tag.getString("ownerName");
        sealedOnDay = tag.getLong("sealedOnDay");
        storedData = tag.contains("storedData") ? tag.getCompound("storedData") : new CompoundTag();
    }
}
