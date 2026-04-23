package com.thunder.wildernessodysseyapi.item.cloak;

import com.thunder.wildernessodysseyapi.core.ModConstants;
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

        if (!CloakItem.isHoldingCloak(player)) {
            if (CloakState.isCloaked(player)) {
                CloakState.setCloaked(player, false);
                CloakState.clearCloak(player);
            }
            CloakState.setHoldingBreath(player, false);
            return;
        }

        boolean holdingBreath = player.isShiftKeyDown();
        boolean wasHoldingBreath = CloakState.isHoldingBreath(player);
        int maxBreath = CloakState.getCurrentMaxBreath(player);

        if (holdingBreath && !wasHoldingBreath) {
            CloakState.incrementBreathPenalty(player);
            maxBreath = CloakState.getCurrentMaxBreath(player);
            if (player.getAirSupply() > maxBreath) {
                player.setAirSupply(maxBreath);
            }
        }

        CloakState.setHoldingBreath(player, holdingBreath);

        if (!holdingBreath) {
            if (CloakState.isCloaked(player)) {
                CloakState.setCloaked(player, false);
                CloakState.clearCloak(player);
            }
            if (!player.isUnderWater() && player.getAirSupply() < maxBreath) {
                player.setAirSupply(Math.min(maxBreath, player.getAirSupply() + 2));
            }
            return;
        }

        if (player.getAirSupply() <= 0) {
            CloakState.setCloaked(player, false);
            CloakState.clearCloak(player);
            return;
        }

        if (!CloakState.isCloaked(player)) {
            CloakState.setCloaked(player, true);
            CloakState.applyCloak(player);
        } else {
            CloakState.refreshIfNeeded(player);
        }

        int cloakAirDrainPerTick = player.isUnderWater() ? 2 : 6;
        player.setAirSupply(Math.max(0, player.getAirSupply() - cloakAirDrainPerTick));
    }
}
