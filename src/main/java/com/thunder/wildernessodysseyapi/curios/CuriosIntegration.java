package com.thunder.wildernessodysseyapi.curios;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Optional;
import java.util.function.Predicate;

public final class CuriosIntegration {
    private CuriosIntegration() {
    }

    public static boolean isEquipped(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(item))
                .orElse(false);
    }

    public static boolean equipIfMissing(Player player, Item item, String slot) {
        Optional<ICuriosItemHandler> handlerOptional = CuriosApi.getCuriosInventory(player);
        if (handlerOptional.isEmpty()) {
            return false;
        }

        ICuriosItemHandler handler = handlerOptional.get();
        if (handler.isEquipped(item)) {
            return false;
        }

        Optional<ICurioStacksHandler> stacksHandlerOptional = handler.getStacksHandler(slot);
        if (stacksHandlerOptional.isEmpty()) {
            return false;
        }

        ICurioStacksHandler stacksHandler = stacksHandlerOptional.get();
        int slots = stacksHandler.getSlots();
        int emptySlot = -1;
        for (int i = 0; i < slots; i++) {
            ItemStack existing = stacksHandler.getStacks().getStackInSlot(i);
            if (existing.isEmpty()) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot == -1) {
            return false;
        }

        ItemStack sourceStack = ItemStack.EMPTY;
        int sourceIndex = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                sourceStack = stack;
                sourceIndex = i;
                break;
            }
        }

        if (sourceStack.isEmpty() || sourceIndex == -1) {
            return false;
        }

        ItemStack equippedStack = sourceStack.split(1);
        if (sourceStack.isEmpty()) {
            player.getInventory().setItem(sourceIndex, ItemStack.EMPTY);
        }

        stacksHandler.getStacks().setStackInSlot(emptySlot, equippedStack);
        return true;
    }

    public static boolean equipFromHand(Player player, InteractionHand hand, String slot) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.isEmpty()) {
            return false;
        }

        Optional<ICuriosItemHandler> handlerOptional = CuriosApi.getCuriosInventory(player);
        if (handlerOptional.isEmpty()) {
            return false;
        }

        ICuriosItemHandler handler = handlerOptional.get();
        if (handler.isEquipped(heldStack.getItem())) {
            return false;
        }

        Optional<ICurioStacksHandler> stacksHandlerOptional = handler.getStacksHandler(slot);
        if (stacksHandlerOptional.isEmpty()) {
            return false;
        }

        ICurioStacksHandler stacksHandler = stacksHandlerOptional.get();
        int slots = stacksHandler.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack existing = stacksHandler.getStacks().getStackInSlot(i);
            if (!existing.isEmpty()) {
                continue;
            }

            ItemStack equippedStack = heldStack.split(1);
            if (heldStack.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }

            stacksHandler.getStacks().setStackInSlot(i, equippedStack);
            return true;
        }

        return false;
    }

    public static boolean unequipMatching(Player player, String slot, Predicate<ItemStack> matcher) {
        Optional<ICuriosItemHandler> handlerOptional = CuriosApi.getCuriosInventory(player);
        if (handlerOptional.isEmpty()) {
            return false;
        }

        Optional<ICurioStacksHandler> stacksHandlerOptional = handlerOptional.get().getStacksHandler(slot);
        if (stacksHandlerOptional.isEmpty()) {
            return false;
        }

        ICurioStacksHandler stacksHandler = stacksHandlerOptional.get();
        int slots = stacksHandler.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack existing = stacksHandler.getStacks().getStackInSlot(i);
            if (existing.isEmpty() || !matcher.test(existing)) {
                continue;
            }

            ItemStack removed = existing.copy();
            removed.setCount(1);
            existing.shrink(1);
            if (existing.isEmpty()) {
                stacksHandler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
            }

            if (!player.addItem(removed)) {
                player.drop(removed, false);
            }
            return true;
        }

        return false;
    }
}
