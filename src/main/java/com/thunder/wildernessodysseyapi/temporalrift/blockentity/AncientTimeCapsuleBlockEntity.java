package com.thunder.wildernessodysseyapi.temporalrift.blockentity;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AncientTimeCapsuleBlockEntity extends BlockEntity {
    private String ownerName = "";
    private long originalSealDay;
    private CompoundTag transferredData = new CompoundTag();
    private boolean collected;

    public AncientTimeCapsuleBlockEntity(BlockPos pos, BlockState state) {
        super(TemporalRiftBlockEntities.ANCIENT_TIME_CAPSULE.get(), pos, state);
    }

    public void setTransferData(String ownerName, long sealDay, CompoundTag data) {
        this.ownerName = ownerName;
        this.originalSealDay = sealDay;
        this.transferredData = data.copy();
        setChanged();
    }

    public void openForPlayer(ServerPlayer player) {
        if (collected) {
            player.sendSystemMessage(Component.literal("This ancient capsule has already been claimed."));
            return;
        }

        player.sendSystemMessage(Component.literal("[Temporal Echo] Sealed on day " + originalSealDay + " by " + ownerName + "."));
        if (transferredData.contains("heldItem")) {
            ItemStack stack = ItemStack.parseOptional(player.registryAccess(), transferredData.getCompound("heldItem"));
            if (!stack.isEmpty() && !player.addItem(stack)) {
                player.drop(stack, false);
            }
        }

        collected = true;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ownerName", ownerName);
        tag.putLong("originalSealDay", originalSealDay);
        tag.put("transferredData", transferredData.copy());
        tag.putBoolean("collected", collected);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerName = tag.getString("ownerName");
        originalSealDay = tag.getLong("originalSealDay");
        transferredData = tag.contains("transferredData") ? tag.getCompound("transferredData") : new CompoundTag();
        collected = tag.getBoolean("collected");
    }
}
