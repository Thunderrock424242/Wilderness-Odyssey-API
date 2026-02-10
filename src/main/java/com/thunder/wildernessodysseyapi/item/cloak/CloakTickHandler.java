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

        boolean holdingCloak = CloakItem.isHoldingCloak(player);
        boolean hasCompass = CloakItem.hasCompassLink(player);
        boolean hasChip = CloakItem.hasCloakChip(player);

        if (CloakState.isCloaked(player)) {
            if (!holdingCloak || !hasCompass || !hasChip) {
                CloakState.setCloaked(player, false);
                CloakState.clearCloak(player);
                return;
            }

            CloakState.refreshIfNeeded(player);
            return;
        }

        // Cloak activation is explicitly user-triggered from CloakItem#use.
        // The tick handler should only sustain or clear an existing cloak.
    }
}
