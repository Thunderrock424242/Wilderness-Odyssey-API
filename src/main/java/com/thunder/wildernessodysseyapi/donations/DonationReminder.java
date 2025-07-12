package com.thunder.wildernessodysseyapi.donations;

import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class DonationReminder {
    private static boolean pendingReminder = false;
    private static int tickCountdown = 0;
    private static final int DELAY_TICKS = 20 * 180; // 3 minutes

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (DonationReminderConfig.disableReminder.get()) return;

        tickCountdown = DELAY_TICKS;
        pendingReminder = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!pendingReminder || Minecraft.getInstance().player == null) return;

        if (--tickCountdown <= 0) {
            pendingReminder = false;
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