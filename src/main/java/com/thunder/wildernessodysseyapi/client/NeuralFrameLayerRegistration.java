package com.thunder.wildernessodysseyapi.client;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class NeuralFrameLayerRegistration {
    private NeuralFrameLayerRegistration() {
    }

    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NeuralFrameModel.LAYER_LOCATION, NeuralFrameModel::createLayer);
    }

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
