package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.AI.AI_perf.PerformanceAction;
import com.thunder.wildernessodysseyapi.AI.AI_perf.PerformanceActionQueue;
import com.thunder.wildernessodysseyapi.AI.AI_perf.PerformanceMitigationController;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.ChatFormatting;
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
                .map(AiAdvisorCommand::formatActionLine)
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
            ctx.getSource().sendFailure(Component.literal(ChatFormatting.RED + "No pending AI advisor action with id " + id + ChatFormatting.RESET));
            return 0;
        }
        PerformanceMitigationController.approveAndApply(player.serverLevel().getServer(), action.get());
        ModConstants.LOGGER.info("AI advisor approval: {} approved {} ({})", ctx.getSource().getTextName(),
                action.get().getId(), action.get().getSubsystem());
        ctx.getSource().sendSuccess(() -> Component.literal(ChatFormatting.GREEN + "Approved and applied action "
                + ChatFormatting.BOLD + id + ChatFormatting.RESET + ChatFormatting.DARK_GRAY + " ["
                + ChatFormatting.GOLD + action.get().getSubsystem() + ChatFormatting.DARK_GRAY + "]"), true);
        return 1;
    }

    private static int reject(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        PerformanceActionQueue queue = PerformanceMitigationController.getActionQueue();
        queue.reject(id);
        ModConstants.LOGGER.info("AI advisor rejection: {} rejected {}", ctx.getSource().getTextName(), id);
        ctx.getSource().sendSuccess(() -> Component.literal(ChatFormatting.RED + "Rejected action "
                + ChatFormatting.BOLD + id + ChatFormatting.RESET), true);
        return 1;
    }

    private static String formatActionLine(PerformanceAction action) {
        String statusColor = switch (action.getStatus()) {
            case PENDING -> ChatFormatting.YELLOW.toString();
            case APPROVED -> ChatFormatting.GREEN.toString();
            case APPLIED -> ChatFormatting.DARK_GREEN.toString();
            case REJECTED -> ChatFormatting.RED.toString();
            case EXPIRED -> ChatFormatting.DARK_RED.toString();
        };

        String idColor = action.isRollback() ? ChatFormatting.LIGHT_PURPLE.toString() : ChatFormatting.AQUA.toString();
        String idLabel = ChatFormatting.BOLD + "ID: " + idColor + action.getId();
        if (action.isRollback()) {
            idLabel += ChatFormatting.DARK_GRAY + " (undo " + ChatFormatting.GRAY
                    + action.getRollbackOfId().orElse("?") + ChatFormatting.DARK_GRAY + ")";
        }

        String statusLabel = statusColor + action.getStatus().name();
        String subsystemLabel = ChatFormatting.GOLD + action.getSubsystem();
        String summaryLabel = ChatFormatting.WHITE + action.getSummary();

        return ChatFormatting.DARK_GRAY + " • " + ChatFormatting.RESET + idLabel + ChatFormatting.RESET
                + ChatFormatting.DARK_GRAY + " [" + subsystemLabel + ChatFormatting.DARK_GRAY + "] "
                + statusLabel + ChatFormatting.RESET + ChatFormatting.DARK_GRAY + " -> "
                + summaryLabel + ChatFormatting.RESET;
    }
}
