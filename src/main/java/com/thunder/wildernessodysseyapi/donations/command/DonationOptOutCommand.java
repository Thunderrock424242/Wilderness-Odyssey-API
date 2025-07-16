package com.thunder.wildernessodysseyapi.donations.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command allowing players to disable donation reminders.
 */
public class DonationOptOutCommand {
    /**
     * Registers the {@code /donation_optout} command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("donation_optout").executes(ctx -> {
            DonationReminderConfig.INSTANCE.disableReminder.set(true);
            DonationReminderConfig.INSTANCE.save();
            ctx.getSource().sendSuccess(() -> Component.literal("\u2705 You will no longer receive donation reminders."), false);
            return 1;
        }));
    }
}
