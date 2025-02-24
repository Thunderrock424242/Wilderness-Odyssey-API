package com.thunder.wildernessodysseyapi.RenderEngine;


import com.thunder.wildernessodysseyapi.RenderEngine.Threading.ModdedRenderInterceptor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public class ClientModEvents {

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelLastEvent event) {
        ModdedRenderInterceptor.executeModRender(() -> {
            // TODO: Run modded world rendering logic
        });
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        ModdedRenderInterceptor.executeModRender(() -> {
            event.getMatrixStack().pushPose();
            // TODO: Run modded UI rendering logic
            event.getMatrixStack().popPose();
        });
    }
}