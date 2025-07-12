package com.thunder.wildernessodysseyapi.donations;

import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber
public class DonationReminder {
    private static boolean hasShownReminder = false;
    private static int ticksElapsed = 0;
    private static final int REMINDER_TICKS = 20 * 60 * 10; // 10 minutes


    public static void onClientJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        hasShownReminder = false;
        ticksElapsed = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;
        if (DonationReminderConfig.disableReminder.get()) return;
        if (hasShownReminder) return;

        ticksElapsed++;
        if (ticksElapsed >= REMINDER_TICKS) {
            hasShownReminder = true;
            showReminder();
        }
    }

    private static void showReminder() {
        Minecraft mc = Minecraft.getInstance();

        Component msg = Component.literal("💜 Consider supporting cancer research or this mod.")
                .withStyle(Style.EMPTY.withColor(0xFFD700));

        Component mskLink = Component.literal("[Donate to MSKCC]")
                .withStyle(Style.EMPTY
                        .withColor(0x55FF55)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://giving.mskcc.org/")));

        Component optOutHint = Component.literal("If you’d like to stop receiving these reminders, ")
                .append(Component.literal("click here to opt out.")
                        .withStyle(Style.EMPTY
                                .withColor(0xFF5555)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/donation_optout"))));

        mc.player.sendSystemMessage(msg);
        mc.player.sendSystemMessage(mskLink);
        mc.player.sendSystemMessage(optOutHint);
    }
}