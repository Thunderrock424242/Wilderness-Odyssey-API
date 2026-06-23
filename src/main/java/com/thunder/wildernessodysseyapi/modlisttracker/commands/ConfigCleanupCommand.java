package com.thunder.wildernessodysseyapi.modlisttracker.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.modlisttracker.ConfigCleanupService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * Registers the administrator-only config cleanup preview and confirmed deletion commands.
 */
public final class ConfigCleanupCommand {

    private static final int MAX_CHAT_CANDIDATES = 10;

    private ConfigCleanupCommand() {
    }

    /**
     * Registers {@code /configcleanup scan} and {@code /configcleanup delete confirm}.
     *
     * <p>The confirmation literal makes destructive cleanup intentional while leaving the root command
     * safe to run as a preview from either an operator account or the dedicated-server console.</p>
     *
     * @param dispatcher active server command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("configcleanup")
                .requires(source -> source.hasPermission(2))
                .executes(context -> runCleanup(context.getSource(), false))
                .then(Commands.literal("scan")
                        .executes(context -> runCleanup(context.getSource(), false)))
                .then(Commands.literal("delete")
                        .executes(context -> requestConfirmation(context.getSource()))
                        .then(Commands.literal("confirm")
                                .executes(context -> runCleanup(context.getSource(), true)))));
    }

    private static int requestConfirmation(CommandSourceStack source) {
        source.sendFailure(Component.literal(
                "This permanently deletes confidently matched stale configs. Run /configcleanup delete confirm to continue."));
        return 0;
    }

    private static int runCleanup(CommandSourceStack source, boolean delete) {
        Path configDir = source.getServer().getFile("config");
        Path reportPath = configDir.resolve("wildernessodysseyapi/config-cleanup-report.json");
        ConfigCleanupService.CleanupResult result = delete
                ? ConfigCleanupService.clean(configDir)
                : ConfigCleanupService.scan(configDir);
        ConfigCleanupService.writeReport(reportPath, result);

        if (delete) {
            source.sendSuccess(() -> Component.literal("[ConfigCleanup] Deleted "
                    + result.deletedConfigs().size() + " stale config file(s); "
                    + result.failedDeletions().size() + " deletion(s) failed."), true);
        } else {
            source.sendSuccess(() -> Component.literal("[ConfigCleanup] Preview found "
                    + result.staleConfigCandidates().size() + " stale config candidate(s). No files were changed."), false);
        }

        source.sendSuccess(() -> Component.literal("[ConfigCleanup] Active=" + result.activeConfigs().size()
                + ", ambiguous=" + result.ambiguousConfigs().size()
                + ", unresolved=" + result.unresolvedConfigs().size() + "."), false);
        sendCandidatePreview(source, result);
        source.sendSuccess(() -> Component.literal("[ConfigCleanup] Report: " + reportPath), false);

        if (!delete && !result.staleConfigCandidates().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "[ConfigCleanup] Review the report, then run /configcleanup delete confirm to delete these files."), false);
        }
        return result.failedDeletions().isEmpty() ? Command.SINGLE_SUCCESS : 0;
    }

    private static void sendCandidatePreview(CommandSourceStack source, ConfigCleanupService.CleanupResult result) {
        result.staleConfigCandidates().entrySet().stream()
                .limit(MAX_CHAT_CANDIDATES)
                .forEach(entry -> source.sendSuccess(() -> Component.literal(
                        "[ConfigCleanup] " + entry.getValue() + " -> " + entry.getKey()), false));

        int hidden = result.staleConfigCandidates().size() - MAX_CHAT_CANDIDATES;
        if (hidden > 0) {
            source.sendSuccess(() -> Component.literal(
                    "[ConfigCleanup] " + hidden + " additional candidate(s) are listed in the report."), false);
        }
    }
}
