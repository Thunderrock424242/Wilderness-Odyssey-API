package com.thunder.wildernessodysseyapi.cinematic.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Applies reusable client presentation camera curves without mutating player rotation. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class CinematicCameraController {
    private CinematicCameraController() {
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        CinematicClientController state = CinematicClientController.get();
        if (!state.isActive()) {
            return;
        }
        ClientCinematicPresentation presentation = state.presentation();
        if (presentation != null) {
            presentation.applyCamera(state, event);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        CinematicClientController state = CinematicClientController.get();
        if (!state.isActive()) {
            return;
        }
        ClientCinematicPresentation presentation = state.presentation();
        if (presentation != null) {
            presentation.applyFov(state, event);
        }
    }
}
