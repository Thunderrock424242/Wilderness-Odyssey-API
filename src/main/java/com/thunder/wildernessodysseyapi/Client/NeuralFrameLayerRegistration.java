package com.thunder.wildernessodysseyapi.Client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NeuralFrameLayerRegistration {
    private NeuralFrameLayerRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NeuralFrameModel.LAYER_LOCATION, NeuralFrameModel::createLayer);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            addLayer(event, skin);
        }
    }

    private static void addLayer(EntityRenderersEvent.AddLayers event, PlayerSkin.Model skin) {
        PlayerRenderer renderer = event.getSkin(skin);
        if (renderer != null) {
            renderer.addLayer(new NeuralFrameRenderLayer(renderer));
        }
    }
}
