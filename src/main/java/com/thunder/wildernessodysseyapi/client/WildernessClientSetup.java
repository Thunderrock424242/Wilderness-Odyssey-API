package com.thunder.wildernessodysseyapi.client;

import net.neoforged.bus.api.IEventBus;

public final class WildernessClientSetup {
    private WildernessClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ChipSetRendererRegistration::onClientSetup);
        modEventBus.addListener(CloakLayerRegistration::onAddLayers);
        modEventBus.addListener(NeuralFrameLayerRegistration::onRegisterLayerDefinitions);
        modEventBus.addListener(NeuralFrameLayerRegistration::onAddLayers);
    }
}
