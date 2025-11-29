package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.AI_perf.PerformanceAction;
import com.thunder.wildernessodysseyapi.AI_perf.PerformanceActionQueue;
import com.thunder.wildernessodysseyapi.AI_perf.PerformanceMitigationController;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AiAdvisorCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aiadvisor")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(AiAdvisorCommand::list))
                .then(Commands.literal("approve")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(AiAdvisorCommand::approve)))
                .then(Commands.literal("reject")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(AiAdvisorCommand::reject))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PerformanceActionQueue queue = PerformanceMitigationController.getActionQueue();
        List<PerformanceAction> actions = queue.getActions();
        if (actions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No AI advisor actions are queued."), false);
            return 1;
        }

        String display = actions.stream()
                .map(action -> action.getId() + " [" + action.getSubsystem() + "] " + action.getStatus()
                        + " -> " + action.getSummary())
                .collect(Collectors.joining("\n"));
        ctx.getSource().sendSuccess(() -> Component.literal(display), false);
        return 1;
    }

    private static int approve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        PerformanceActionQueue queue = PerformanceMitigationController.getActionQueue();
        Optional<PerformanceAction> action = queue.findPending(id);
        if (action.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No pending AI advisor action with id " + id));
            return 0;
        }
        PerformanceMitigationController.approveAndApply(player.serverLevel().getServer(), action.get());
        ctx.getSource().sendSuccess(() -> Component.literal("Approved and applied action " + id), true);
        return 1;
    }

    private static int reject(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        PerformanceActionQueue queue = PerformanceMitigationController.getActionQueue();
        queue.reject(id);
        ctx.getSource().sendSuccess(() -> Component.literal("Rejected action " + id), true);
        return 1;
    }
}
