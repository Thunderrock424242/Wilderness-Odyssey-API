package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatOptIn;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Simple opt-in/out command dedicated to the global chat feature.
 */
public final class GlobalChatOptToggleCommand {

    private GlobalChatOptToggleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("globalchatoptin")
                .executes(ctx -> setOptIn(ctx, true))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setOptIn(ctx, BoolArgumentType.getBool(ctx, "enabled")))));

        dispatcher.register(Commands.literal("globalchatoptout")
                .executes(ctx -> setOptIn(ctx, false)));
    }

    private static int setOptIn(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Only players can change their global chat opt-in status."));
            return 0;
        }
        GlobalChatOptIn.setOptIn(player, enabled);
        ctx.getSource().sendSuccess(() -> Component.literal("Global chat opt-in set to " + (enabled ? "enabled" : "disabled")), false);
        return 1;
    }
}
