package com.thunder.wildernessodysseyapi.item.cloak;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class CloakTickHandler {
    private CloakTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        if (!CloakState.isCloaked(player)) {
            return;
        }

        if (!CloakItem.isHoldingCloak(player) || !CloakItem.hasCompassLink(player) || !CloakItem.hasCloakChip(player)) {
            CloakState.setCloaked(player, false);
            CloakState.clearCloak(player);
            return;
        }

        CloakState.refreshIfNeeded(player);
    }
}
