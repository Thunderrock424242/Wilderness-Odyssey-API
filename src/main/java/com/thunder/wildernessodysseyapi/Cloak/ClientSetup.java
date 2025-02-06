package com.thunder.wildernessodysseyapi.Cloak;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.AddLayers event) {
        EntityRenderer<Player> renderer = event.getSkin(PlayerSkin.Model.valueOf("default"));
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(new CloakLayer(livingRenderer));
        }
    }
}
