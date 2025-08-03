package com.thunder.wildernessodysseyapi.donations.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.client.WorldVersionChecker;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client command allowing players to disable donation reminders.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID)
public class DonationOptOutCommand {

    /** Registers the {@code /donation_optout} client command. */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("donation_optout").executes(ctx -> {
            DonationReminderConfig.disableReminder.set(true);
            DonationReminderConfig.optOutWorldVersion.set(WorldVersionChecker.MOD_DEFAULT_WORLD_VERSION);
            DonationReminderConfig.save();
            ctx.getSource().sendSuccess(() -> Component.literal("\u2705 You will no longer receive donation reminders."), false);
            return 1;
        }));
    }
}
