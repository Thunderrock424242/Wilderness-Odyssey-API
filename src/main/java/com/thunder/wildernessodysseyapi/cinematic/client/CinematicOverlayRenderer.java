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
            int textAlpha = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
            int panelAlpha = Mth.clamp(Math.round(alpha * 196.0F), 0, 196);
            int panelWidth = Math.min(width - 32, Math.max(220, minecraft.font.width(postMessage) + 24));
            int panelHeight = 29;
            int panelX = (width - panelWidth) / 2;
            int panelY = Math.min(Math.round(height * 0.72F), height - panelHeight - 14);
            int color = (textAlpha << 24) | 0xE4F3F0;
            graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                    (panelAlpha << 24) | 0x091112);
            graphics.fill(panelX, panelY, panelX + 2, panelY + panelHeight,
                    (textAlpha << 24) | 0x65D6CC);
            graphics.hLine(panelX + 2, panelX + panelWidth - 1, panelY,
                    (Mth.clamp(Math.round(alpha * 120.0F), 0, 120) << 24) | 0x567774);
            graphics.drawString(minecraft.font, postMessage, panelX + 11, panelY + 10, color, false);
        }
    }
}
