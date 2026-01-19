package com.thunder.wildernessodysseyapi.donations.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Opens links encouraging players to donate.
 */
public class DonateCommand {
    /**
     * Registers the {@code /donate} command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("donate").executes(context -> {
            CommandSourceStack source = context.getSource();

            Component yourDonation = Component.literal("\uD83D\uDC96 Support my work")
                    .withStyle(Style.EMPTY
                            .withColor(0xFFAA00)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://your.donation.link")));

            Component mskDonation = Component.literal("\uD83C\uDF97 Donate to Cancer Research at Memorial Sloan Kettering")
                    .withStyle(Style.EMPTY
                            .withColor(0xFF55FF)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://giving.mskcc.org/")));

            Component message = Component.literal("If you'd rather support a cause close to my heart, consider donating to cancer research. I've lost a friend and family to cancer, and every bit helps.");

            source.sendSuccess(() -> yourDonation, false);
            source.sendSuccess(() -> mskDonation, false);
            source.sendSuccess(() -> message, false);

            return 1;
        }));
    }
}
