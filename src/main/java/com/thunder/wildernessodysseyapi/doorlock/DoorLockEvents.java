package com.thunder.wildernessodysseyapi.doorlock;

import com.thunder.ticktoklib.TickTokHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles applying and enforcing door locks.
 */
public class DoorLockEvents {

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock)) return;

        ItemStack stack = event.getItemStack();
        if (!level.isClientSide && event.getEntity() instanceof ServerPlayer player) {
            ServerLevel serverLevel = (ServerLevel) level;
            DoorLockSavedData data = DoorLockSavedData.get(serverLevel);
            long now = level.getGameTime();

            CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
            if (stack.is(Items.STICK) && custom != null) {
                CompoundTag tag = custom.copyTag();
                if (tag.contains("door_lock_duration")) {
                    if (player.isShiftKeyDown()) {
                        if (data.removeLock(pos)) {
                            player.displayClientMessage(Component.literal("Door lock removed"), true);
                        }
                    } else {
                        int duration = tag.getInt("door_lock_duration");
                        data.setLock(pos, duration, now);
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                        float seconds = TickTokHelper.toSeconds(duration);
                        String msg = "Door locked for " + seconds + "s";
                        if (DoorLockSavedData.DEV_MODE) {
                            msg += " (pending)";
                        }
                        player.displayClientMessage(Component.literal(msg), true);
                    }
                    event.setCanceled(true);
                    return;
                }
            }

            if (data.isLocked(pos, now)) {
                long remaining = data.remaining(pos, now);
                float seconds = TickTokHelper.toSeconds((int) remaining);
                player.displayClientMessage(Component.literal("You can access this in " + seconds + "s"), true);
                event.setCanceled(true);
            }
        }
    }
}
