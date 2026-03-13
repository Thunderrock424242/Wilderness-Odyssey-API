package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;

public final class LocateLastDeathAliasCommand {
    private LocateLastDeathAliasCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> locateNode = dispatcher.getRoot().getChild("locate");
        if (locateNode == null || locateNode.getChild("lastdeath") != null) {
            return;
        }

        CommandNode<CommandSourceStack> legacyLastDeathNode = locateNode.getChild("last");
        if (legacyLastDeathNode != null) {
            legacyLastDeathNode = legacyLastDeathNode.getChild("death");
        }

        if (legacyLastDeathNode == null) {
            return;
        }

        locateNode.addChild(LiteralArgumentBuilder.<CommandSourceStack>literal("lastdeath")
                .redirect(legacyLastDeathNode)
                .build());
    }
}
