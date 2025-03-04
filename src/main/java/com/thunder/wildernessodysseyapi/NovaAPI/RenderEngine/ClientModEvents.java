package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine;


import com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.Threading.ModdedRenderInterceptor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
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