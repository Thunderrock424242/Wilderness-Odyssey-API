package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wilderness.water.tide.TideSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
@EventBusSubscriber(modid = "wilderness", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class TideHudOverlay {

    private static final boolean SHOW_HUD = true; // TODO: wire to config

    private static final int BAR_WIDTH  = 60;
    private static final int BAR_HEIGHT = 4;

    private static final String[] MOON_NAMES = {
        "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
        "New Moon",  "Waxing Crescent", "First Quarter", "Waxing Gibbous"
    };

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!SHOW_HUD) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.options.hideGui) return;

        // Only show when near/in water
        if (!mc.player.isInWater() && !isNearOcean(mc)) return;

        Level level  = mc.level;
        GuiGraphics g = event.getGuiGraphics();
        int screenH  = mc.getWindow().getGuiScaledHeight();
        int x = 8, y = screenH - 40;

        // Tide name
        String tideName  = TideSystem.getTideName(level);
        String moonName  = MOON_NAMES[level.getMoonPhase()];
        float  tideLevel = TideSystem.getTideNormalised(level); // 0–1

        g.drawString(mc.font, "§b" + moonName,  x, y,      0xFFFFFFFF, false);
        g.drawString(mc.font, "§7" + tideName,  x, y + 10, 0xFFFFFFFF, false);

        // Tide bar background
        g.fill(x, y + 22, x + BAR_WIDTH, y + 22 + BAR_HEIGHT, 0x88000000);

        // Tide bar fill
        int fillW = (int)(tideLevel * BAR_WIDTH);
        int barCol = tideLevel > 0.65f ? 0xFF4488FF   // high tide — blue
                   : tideLevel < 0.35f ? 0xFF886622   // low tide — brown
                   : 0xFF2266AA;                        // mid tide
        if (fillW > 0) {
            g.fill(x, y + 22, x + fillW, y + 22 + BAR_HEIGHT, barCol);
        }

        // Tick marks at low/mid/high
        g.fill(x + BAR_WIDTH/2 - 1, y + 21, x + BAR_WIDTH/2 + 1, y + 28, 0x88FFFFFF);
    }

    private static boolean isNearOcean(Minecraft mc) {
        // Quick check: is the player within vertical range of sea level?
        return mc.player != null && Math.abs(mc.player.getY() - 62) < 8;
    }
}
