package com.thunder.wildernessodysseyapi.donations.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Presents the project's verified charitable donation link.
 */
public class DonateCommand {
    /**
     * Registers the {@code /donate} command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("donate").executes(context -> {
            CommandSourceStack source = context.getSource();

            Component mskDonation = Component.literal("\uD83C\uDF97 Donate to Cancer Research at Memorial Sloan Kettering")
                    .withStyle(Style.EMPTY
                            .withColor(0xFF55FF)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://giving.mskcc.org/")));

            Component message = Component.literal("If you would like to support a cause close to my heart, consider donating to cancer research. I've lost a friend and family to cancer, and every bit helps.");

            source.sendSuccess(() -> mskDonation, false);
            source.sendSuccess(() -> message, false);

            return 1;
        }));
    }
}
