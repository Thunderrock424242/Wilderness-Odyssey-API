package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StructurePlacementDebugger;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StructurePlacementDebugger.PlacementAttempt;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Debug command that exposes the recent structure placement attempts recorded by the mod.
 */
public final class StructurePlacementDebugCommand {
    private StructurePlacementDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("structuredebug")
                        .executes(ctx -> execute(ctx, 10))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "limit"))))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, int limit) {
        List<PlacementAttempt> attempts = StructurePlacementDebugger.recent(limit);
        if (attempts.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(ChatFormatting.RED + "No recorded structure placements yet."), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(ChatFormatting.GOLD + "Recent structure placements (newest first):"), false);
        attempts.forEach(attempt -> ctx.getSource().sendSuccess(() -> Component.literal(formatAttempt(attempt)), false));
        return 1;
    }

    private static String formatAttempt(PlacementAttempt attempt) {
        String statusColor = attempt.success() ? ChatFormatting.GREEN.toString() : ChatFormatting.RED.toString();
        String location = "[%s] %s,%s,%s".formatted(
                attempt.dimension(), attempt.origin().getX(), attempt.origin().getY(), attempt.origin().getZ());
        return "%s%s %s %s %s".formatted(
                statusColor,
                attempt.success() ? "✔" : "✖",
                ChatFormatting.GRAY + attempt.id().toString(),
                ChatFormatting.AQUA + location,
                ChatFormatting.YELLOW + attempt.statusDetail()
        );
    }
}
