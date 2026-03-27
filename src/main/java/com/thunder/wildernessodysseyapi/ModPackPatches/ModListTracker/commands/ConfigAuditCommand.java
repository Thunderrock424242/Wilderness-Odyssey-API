package com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.ConfigLinkAudit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * Command to audit files under /config and map each file to an installed mod when possible.
 */
public final class ConfigAuditCommand {

    private ConfigAuditCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("configaudit")
                .requires(source -> source.hasPermission(2))
                .executes(context -> runAudit(context.getSource())));
    }

    private static int runAudit(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Path configDir = server.getFile("config");
        Path reportPath = configDir.resolve("wildernessodysseyapi/config-audit-report.json");
        Path unresolvedLogPath = server.getFile("logs").toPath().resolve("config-audit-unresolved.log");

        ConfigLinkAudit.AuditResult result = ConfigLinkAudit.run(configDir, reportPath, unresolvedLogPath);

        source.sendSuccess(() -> Component.literal("[ConfigAudit] Complete: "
                + result.totalConfigFiles() + " files, "
                + result.linkedConfigs().size() + " linked, "
                + result.ambiguousConfigs().size() + " ambiguous, "
                + result.unresolvedConfigs().size() + " unresolved."), true);

        source.sendSuccess(() -> Component.literal("[ConfigAudit] Report: " + reportPath), false);
        source.sendSuccess(() -> Component.literal("[ConfigAudit] Unresolved log: " + unresolvedLogPath), false);
        return Command.SINGLE_SUCCESS;
    }
}
