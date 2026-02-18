package com.thunder.wildernessodysseyapi.item.cloak;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.curios.CuriosIntegration;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.item.chip.ChipSetItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class CloakChipUnequipHandler {
    private CloakChipUnequipHandler() {
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        if (!player.getItemInHand(event.getHand()).isEmpty()) {
            return;
        }

        boolean removed = CuriosIntegration.unequipMatching(
                player,
                ChipSetItem.CHIP_SET_SLOT,
                stack -> stack.is(ModItems.CLOAK_CHIP.get())
        );

        if (removed && CloakState.isCloaked(player)) {
            CloakState.setCloaked(player, false);
            CloakState.clearCloak(player);
        }
    }
}
