package com.thunder.wildernessodysseyapi.ai.voice.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Draws near-synchronized speech text without adding a duplicate chat message. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AetherVoiceSubtitleOverlay {
    private AetherVoiceSubtitleOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        AetherVoiceClient.SubtitleSnapshot subtitle = AetherVoiceClient.subtitle();
        if (subtitle.text() == null || subtitle.alpha() <= 0.0F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int textWidth = minecraft.font.width(subtitle.text());
        int x = Math.max(8, (width - textWidth) / 2);
        int y = Math.round(height * 0.79F);
        int alpha = Mth.clamp(Math.round(subtitle.alpha() * 220.0F), 0, 220);
        graphics.fill(x - 5, y - 4, Math.min(width - 8, x + textWidth + 5), y + minecraft.font.lineHeight + 4,
                alpha << 24);
        graphics.drawString(minecraft.font, subtitle.text(), x, y, (alpha << 24) | 0xEAF4F8, true);
    }
}
