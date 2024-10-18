package net.mcreator.wildernessodysseyapi.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.mcreator.wildernessodysseyapi.BanManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;

public class BanCommand {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String GITHUB_LOG_PATH = "logs/ban-log.txt"; // Path for local ban log before uploading to GitHub

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ban")
                .requires(source -> source.hasPermission(2)) // Requires admin-level permissions
                .then(Commands.argument("playerName", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String playerName = StringArgumentType.getString(context, "playerName");
                                    String reason = StringArgumentType.getString(context, "reason");

                                    // Ban the player and log the information
                                    banPlayer(playerName, reason);
                                    context.getSource().sendSuccess((Supplier<Component>) Component.literal("Player " + playerName + " has been banned for: " + reason), true);
                                    return Command.SINGLE_SUCCESS;
                                }))));
    }

    private static void banPlayer(String playerName, String reason) {
        BanManager.addBannedPlayer(playerName);

        String logEntry = "Player: " + playerName + " | Action: Banned | Reason: " + reason + "\n";

        // Log to local file
        logToLocalFile(logEntry);

        // Upload the log to GitHub
        uploadLogToGitHub(logEntry);
    }

    private static void logToLocalFile(String logEntry) {
        try {
            Files.write(Paths.get(GITHUB_LOG_PATH), logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to log ban to local file", e);
        }
    }

    private static void uploadLogToGitHub(String logEntry) {
        // GitHub integration logic goes here.
        LOGGER.info("Uploading ban log to GitHub: {}", logEntry);
        // Implementation for uploading the log entry to the GitHub repository.
    }
}
