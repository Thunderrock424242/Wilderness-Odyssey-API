package com.thunder.wildernessodysseyapi.cinematic.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Renders resolution-independent cinematic overlays after the ordinary HUD pass. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class CinematicOverlayRenderer {
    private CinematicOverlayRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        CinematicClientController state = CinematicClientController.get();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        GuiGraphics graphics = event.getGuiGraphics();

        if (state.isActive()) {
            ClientCinematicPresentation presentation = state.presentation();
            if (presentation != null) {
                presentation.renderOverlay(state, graphics, width, height, partialTick);
            }
            return;
        }

        Component postMessage = state.postMessage();
        float alpha = state.postMessageAlpha(partialTick);
        if (postMessage != null && alpha > 0.0F) {
            int color = (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | 0xE6F3F7;
            graphics.drawCenteredString(
                    minecraft.font,
                    postMessage,
                    width / 2,
                    Math.round(height * 0.72F),
                    color
            );
        }
    }
}
