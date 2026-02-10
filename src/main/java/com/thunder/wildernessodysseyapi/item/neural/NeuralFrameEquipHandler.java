package com.thunder.wildernessodysseyapi.item.neural;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.curios.CuriosIntegration;
import com.thunder.wildernessodysseyapi.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class NeuralFrameEquipHandler {
    private NeuralFrameEquipHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!CuriosIntegration.isEquipped(player, ModItems.NEURAL_FRAME.get())) {
                var neuralFrameStack = ModItems.NEURAL_FRAME.get().getDefaultInstance();
                if (!player.getInventory().contains(neuralFrameStack)) {
                    if (!player.addItem(neuralFrameStack)) {
                        player.drop(neuralFrameStack, false);
                    }
                }
                CuriosIntegration.equipIfMissing(player, ModItems.NEURAL_FRAME.get(), "head");
            }
        }
    }
}
