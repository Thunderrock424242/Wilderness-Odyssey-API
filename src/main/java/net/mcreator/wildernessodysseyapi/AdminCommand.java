package net.mcreator.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.level.GameType;

import java.io.*;
import java.util.*;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class AdminCommand {

    private static boolean adminModeEnabled = false;
    private static final String WARNINGS_FILE_PATH = "warnings.dat";
    private static HashMap<UUID, List<String>> warnings = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        loadWarnings();

        LiteralArgumentBuilder<CommandSourceStack> adminBuilder = Commands.literal("admin")
                .requires(source -> source.hasPermission(2)) // Requires op level 2
                .then(Commands.literal("seeplayers")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> toggleAdminMode(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("warn")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .then(Commands.argument("message", StringArgumentType.string())
                                        .executes(context -> warnPlayer(context, StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "message"))))));

        dispatcher.register(adminBuilder);
    }

    private static int toggleAdminMode(CommandSourceStack source, boolean enabled) {
        adminModeEnabled = enabled;
        if (adminModeEnabled) {
            try {
                ServerPlayer player = source.getPlayerOrException();
                player.setGameMode(GameType.SPECTATOR);
                ///EntityOutlineRenderer.addPlayerWithGlowingEffect(player.getUUID()); // Add player to see glowing effect
                showPlayers(source);
                source.sendSuccess(() -> Component.literal("Admin mode enabled: All players are now visible, and you are in spectate mode."), false);
            } catch (CommandSyntaxException e) {
                source.sendFailure(Component.literal("Error: " + e.getMessage()));
            }
        } else {
            try {
                ServerPlayer player = source.getPlayerOrException();
                player.setGameMode(GameType.SURVIVAL); // Reset the player to survival or their original game mode
                ///EntityOutlineRenderer.removePlayerWithGlowingEffect(player.getUUID()); // Remove player from glowing effect
                hidePlayers(source);
                source.sendSuccess(() -> Component.literal("Admin mode disabled: Players visibility reset."), false);
            } catch (CommandSyntaxException e) {
                source.sendFailure(Component.literal("Error: " + e.getMessage()));
            }
        }
        return 1;
    }

    private static int warnPlayer(CommandContext<CommandSourceStack> context, String playerName, String message) {
        CommandSourceStack source = context.getSource();
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (targetPlayer != null) {
            UUID playerUUID = targetPlayer.getUUID();
            warnings.putIfAbsent(playerUUID, new ArrayList<>());
            warnings.get(playerUUID).add(message);
            saveWarnings();

            targetPlayer.sendSystemMessage(Component.literal("Warning: " + message));
            source.sendSuccess(() -> Component.literal("Warning sent to " + playerName), false);

            // Display all previous warnings to the admin
            List<String> playerWarnings = warnings.get(playerUUID);
            for (String warn : playerWarnings) {
                source.sendSuccess(() -> Component.literal("Previous Warning: " + warn), false);
            }
        } else {
            source.sendFailure(Component.literal("Player " + playerName + " not found."));
        }
        return 1;
    }

    private static void showPlayers(CommandSourceStack source) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            makePlayersVisible(player);
        }
    }

    private static void hidePlayers(CommandSourceStack source) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            resetPlayerVisibility(player);
        }
    }

    private static void makePlayersVisible(ServerPlayer player) {
        PlayerTeam team = new PlayerTeam(player.getScoreboard(), "visiblePlayers");
        team.setNameTagVisibility(PlayerTeam.Visibility.ALWAYS);
        team.setSeeFriendlyInvisibles(true); // This is a workaround to make players outline visible

        for (ServerPlayer otherPlayer : Objects.requireNonNull(player.getServer()).getPlayerList().getPlayers()) {
            player.getScoreboard().addPlayerToTeam(otherPlayer.getName().getString(), team);
        }
    }

    private static void resetPlayerVisibility(ServerPlayer player) {
        PlayerTeam team = player.getScoreboard().getPlayerTeam(player.getName().getString());
        if (team != null) {
            player.getScoreboard().removePlayerFromTeam(player.getName().getString(), team);
        }
    }

    private static void saveWarnings() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(WARNINGS_FILE_PATH))) {
            out.writeObject(warnings);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadWarnings() {
        File file = new File(WARNINGS_FILE_PATH);
        if (file.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                warnings = (HashMap<UUID, List<String>>) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
