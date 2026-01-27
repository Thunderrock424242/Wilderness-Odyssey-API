package com.thunder.wildernessodysseyapi.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Optional;

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
        for (int i = 0; i < slots; i++) {
            ItemStack existing = stacksHandler.getStacks().getStackInSlot(i);
            if (existing.isEmpty()) {
                stacksHandler.getStacks().setStackInSlot(i, new ItemStack(item));
                return true;
            }
        }

        return false;
    }
}
