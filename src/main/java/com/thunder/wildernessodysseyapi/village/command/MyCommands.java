package com.thunder.wildernessodysseyapi.village.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.village.StructureLocationData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class MyCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("locate_mystuff")
                .requires(source -> source.hasPermission(2)) // permission level
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    StructureLocationData data = StructureLocationData.get(level);
                    BlockPos pos = data.getStructurePos();

                    if (pos == null) {
                        context.getSource().sendFailure(Component.literal("Structure has not been placed yet."));
                        return 0;
                    }

                    context.getSource().sendSuccess(() ->
                            Component.literal("Structure is at: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false
                    );

                    // Optional: teleport the player
                    // player.teleportTo(pos.getX(), pos.getY(), pos.getZ());

                    return Command.SINGLE_SUCCESS;
                })
        );
    }
}
