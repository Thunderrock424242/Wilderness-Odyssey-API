package com.thunder.wildernessodysseyapi.Cloak;


import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.AddLayers event) {

        EntityRenderer<?> anyRenderer = (EntityRenderer<?>) event.getRenderer(EntityType.PLAYER);

        if (anyRenderer instanceof PlayerRenderer) {
            PlayerRenderer playerRenderer = (PlayerRenderer) anyRenderer;
            playerRenderer.addLayer(new CloakLayer(playerRenderer));
        }
    }
}
