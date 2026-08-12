package com.thunder.wildernessodysseyapi.developmentstudio.debug.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** NeoForge render bridge for independently registered Studio overlays. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class StudioDebugRenderEvents {
    private StudioDebugRenderEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!StudioDebugRendererRegistry.hasEnabledRenderers()) {
            return;
        }
        StudioDebugRendererRegistry.render(Minecraft.getInstance(), event);
    }
}
