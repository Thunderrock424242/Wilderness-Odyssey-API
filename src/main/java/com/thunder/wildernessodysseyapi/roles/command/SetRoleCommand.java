package com.thunder.wildernessodysseyapi.roles.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.roles.api.PlayerRole;
import com.thunder.wildernessodysseyapi.roles.core.PlayerRoleManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SetRoleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setrole")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("role", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
                                    String roleStr = StringArgumentType.getString(ctx, "role").toUpperCase();

                                    try {
                                        PlayerRole role = PlayerRole.valueOf(roleStr);
                                        PlayerRoleManager.assignRole(player.getUUID(), role);
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("Assigned role " + role + " to " + player.getName().getString()), true);
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendFailure(Component.literal("Invalid role: " + roleStr));
                                    }

                                    return 1;
                                }))));
    }
}