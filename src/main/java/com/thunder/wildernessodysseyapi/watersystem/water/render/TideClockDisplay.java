package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Attaches synchronized tide information to Minecraft's vanilla clock.
 *
 * <p>The clock item, its model predicate, and its timekeeping behavior remain
 * vanilla-owned. This client subscriber only appends tooltip lines and draws
 * one compact contextual line while a clock is held in either hand or targeted
 * in an item frame.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class TideClockDisplay {

    private static final int BACKGROUND_COLOR = 0x88000000;
    private static final int HORIZONTAL_PADDING = 4;
    private static final int VERTICAL_PADDING = 2;
    private static final int HOTBAR_CLEARANCE = 49;

    private TideClockDisplay() {
    }

    /** Adds live tide detail when a player hovers a vanilla clock. */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null
                || !event.getItemStack().is(Items.CLOCK)
                || !WaterRenderingConfig.SHOW_CLOCK_TIDE_TOOLTIP.get()) {
            return;
        }

        Level level = player.level();
        TideClockDisplayModel.TideReadout readout = readout(level);
        event.getToolTip().add(Component.translatable(
                "tooltip.wildernessodysseyapi.clock_tide",
                readout.tideName(),
                readout.offsetBlocks()
        ).withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(Component.translatable(
                "tooltip.wildernessodysseyapi.clock_tide_trend",
                Component.translatable(readout.trendTranslationKey())
        ).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable(
                "tooltip.wildernessodysseyapi.clock_moon",
                Component.translatable(readout.moonTranslationKey())
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Draws one tide line only while the player is holding or looking at a clock. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null
                || level == null
                || minecraft.options.hideGui
                || !WaterRenderingConfig.SHOW_CONTEXTUAL_CLOCK_TIDE_DISPLAY.get()
                || !TideClockDisplayModel.shouldShowContextualDisplay(
                        player.getMainHandItem().is(Items.CLOCK),
                        player.getOffhandItem().is(Items.CLOCK),
                        isLookingAtFramedClock(minecraft))) {
            return;
        }

        TideClockDisplayModel.TideReadout readout = readout(level);
        Component text = Component.translatable(
                "hud.wildernessodysseyapi.clock_tide",
                readout.tideName(),
                readout.offsetBlocks(),
                Component.translatable(readout.moonTranslationKey())
        );
        drawAboveHotbar(event.getGuiGraphics(), minecraft, text);
    }

    private static boolean isLookingAtFramedClock(Minecraft minecraft) {
        return minecraft.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof ItemFrame itemFrame
                && itemFrame.getItem().is(Items.CLOCK);
    }

    private static TideClockDisplayModel.TideReadout readout(Level level) {
        return TideClockDisplayModel.create(
                TideSystem.sample(level),
                TideSystem.getTideName(level)
        );
    }

    private static void drawAboveHotbar(
            GuiGraphics graphics,
            Minecraft minecraft,
            Component text
    ) {
        int textWidth = minecraft.font.width(text);
        int x = (minecraft.getWindow().getGuiScaledWidth() - textWidth) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - HOTBAR_CLEARANCE;
        graphics.fill(
                x - HORIZONTAL_PADDING,
                y - VERTICAL_PADDING,
                x + textWidth + HORIZONTAL_PADDING,
                y + minecraft.font.lineHeight + VERTICAL_PADDING,
                BACKGROUND_COLOR
        );
        graphics.drawString(minecraft.font, text, x, y, 0xFFEAF7FF, true);
    }
}
