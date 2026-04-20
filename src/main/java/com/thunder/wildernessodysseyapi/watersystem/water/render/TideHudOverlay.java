package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * TideHudOverlay
 *
 * Draws a small tide indicator in the bottom-left corner of the screen
 * when the player is in or near an ocean biome.
 *
 * Shows:
 *   - Current tide name (Spring High Tide, Neap Ebbing, etc.)
 *   - A small bar showing tide level from low to high
 *   - Moon phase name
 *
 * Can be toggled with a config option (defaults on).
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class TideHudOverlay {

    private static boolean hudEnabled = false;

    private static final int BAR_WIDTH  = 92;
    private static final int BAR_HEIGHT = 6;

    private static final String[] MOON_NAMES = {
        "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
        "New Moon",  "Waxing Crescent", "First Quarter", "Waxing Gibbous"
    };

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!hudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.options.hideGui) return;

        // Only show when near/in water
        if (!mc.player.isInWater() && !isNearOcean(mc)) return;

        Level level  = mc.level;
        GuiGraphics g = event.getGuiGraphics();
        int screenH  = mc.getWindow().getGuiScaledHeight();
        int x = 8, y = screenH - 54;

        // Tide name
        String moonName = MOON_NAMES[level.getMoonPhase()];
        String tideName = TideSystem.getTideName(level);
        float tideLevel = TideSystem.getTideNormalised(level); // 0–1
        float tideRate = TideSystem.getTideRate(level);
        float tideOffset = TideSystem.getTideOffset(level);

        String trend = tideRate > 0.001f ? "↑" : tideRate < -0.001f ? "↓" : "•";
        String detail = String.format("§7%s %s  §8(%.2f)", trend, tideName, tideOffset);

        g.fill(x - 4, y - 4, x + BAR_WIDTH + 6, y + 34, 0x66000000);
        g.drawString(mc.font, "§b" + moonName, x, y, 0xFFFFFFFF, false);
        g.drawString(mc.font, detail, x, y + 11, 0xFFFFFFFF, false);

        int barY = y + 24;
        g.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, 0xAA111111);

        int markerX = x + Mth.clamp(Math.round(tideLevel * BAR_WIDTH), 0, BAR_WIDTH);
        int barCol = tideLevel > 0.66f ? 0xFF4B8BFF
                   : tideLevel < 0.33f ? 0xFF9A6A33
                   : 0xFF2D75C8;
        g.fill(x, barY, markerX, barY + BAR_HEIGHT, barCol);
        g.fill(markerX - 1, barY - 2, markerX + 1, barY + BAR_HEIGHT + 2, 0xFFEAF2FF);

        // Midpoint marker
        int centerX = x + BAR_WIDTH / 2;
        g.fill(centerX - 1, barY - 2, centerX + 1, barY + BAR_HEIGHT + 2, 0x88FFFFFF);
    }

    private static boolean isNearOcean(Minecraft mc) {
        // Quick check: is the player within vertical range of sea level?
        return mc.player != null && Math.abs(mc.player.getY() - 62) < 8;
    }

    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    public static void setHudEnabled(boolean enabled) {
        hudEnabled = enabled;
    }

    public static boolean toggleHudEnabled() {
        hudEnabled = !hudEnabled;
        return hudEnabled;
    }
}
