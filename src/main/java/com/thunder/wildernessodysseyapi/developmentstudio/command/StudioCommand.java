package com.thunder.wildernessodysseyapi.developmentstudio.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioServerService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Registers the player-facing {@code /wilderness studio} entry point. */
public final class StudioCommand {
    private StudioCommand() {
    }

    /** Adds Studio beneath a focused Wilderness command root. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wilderness")
                .then(Commands.literal("studio")
                        .executes(context -> {
                            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                context.getSource().sendFailure(Component.translatable(
                                        "message.wildernessodysseyapi.studio.players_only"));
                                return 0;
                            }
                            return StudioServerService.open(player) ? 1 : 0;
                        })));
    }
}
