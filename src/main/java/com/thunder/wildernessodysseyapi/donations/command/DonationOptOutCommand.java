package com.thunder.wildernessodysseyapi.donations.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class DonationOptOutCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("donation_optout").executes(ctx -> {
            DonationReminderConfig.INSTANCE.disableReminder.set(true);
            DonationReminderConfig.INSTANCE.save();
            ctx.getSource().sendSuccess(() -> Component.literal("✅ You will no longer receive donation reminders."), false);
            return 1;
        }));
    }
}