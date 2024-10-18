package net.mcreator.wildernessodysseyapi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class BanManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String GITHUB_BAN_FILE_URL = "https://raw.githubusercontent.com/Thunderrock424242/Wilderness-Odyssey-API/refs/heads/master/banned-players.txt";
    private static final String LOCAL_BAN_FILE_PATH = "banned-players.txt"; // Local file to store banned players
    private static final Set<String> bannedPlayers = new HashSet<>(); // Set to keep track of banned players

    /**
     * Syncs the ban list from GitHub and updates the local list of banned players.
     */
    public static void syncBanListFromGitHub() {
        try {
            URL url = new URL(GITHUB_BAN_FILE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Read the response
            Scanner scanner = new Scanner(connection.getInputStream());
            Set<String> updatedBannedPlayers = new HashSet<>();
            while (scanner.hasNextLine()) {
                updatedBannedPlayers.add(scanner.nextLine().trim());
            }
            scanner.close();

            // Update the local banned players list
            bannedPlayers.clear();
            bannedPlayers.addAll(updatedBannedPlayers);

            // Save the updated list locally
            writeBanListToFile();

            LOGGER.info("Ban list updated from GitHub. Total banned players: " + bannedPlayers.size());

        } catch (IOException e) {
            LOGGER.error("Failed to update ban list from GitHub", e);
        }
    }

    /**
     * Reads the ban list from a local file and updates the local list of banned players.
     */
    public static void readBanListFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(LOCAL_BAN_FILE_PATH))) {
            String line;
            bannedPlayers.clear();
            while ((line = reader.readLine()) != null) {
                bannedPlayers.add(line.trim());
            }
            LOGGER.info("Ban list loaded from local file. Total banned players: " + bannedPlayers.size());
        } catch (IOException e) {
            LOGGER.error("Failed to read ban list from local file", e);
        }
    }

    /**
     * Writes the current list of banned players to a local file.
     */
    public static void writeBanListToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOCAL_BAN_FILE_PATH))) {
            for (String playerId : bannedPlayers) {
                writer.write(playerId);
                writer.newLine();
            }
            LOGGER.info("Ban list saved to local file.");
        } catch (IOException e) {
            LOGGER.error("Failed to write ban list to local file", e);
        }
    }

    /**
     * Checks if a player is banned.
     *
     * @param playerId The player's unique identifier (e.g., UUID)
     * @return True if the player is banned, false otherwise
     */
    public static boolean isPlayerBanned(String playerId) {
        return bannedPlayers.contains(playerId);
    }

    /**
     * Adds a player to the local banned list.
     * This does not upload to GitHub; it's only for local use.
     *
     * @param playerId The player's unique identifier (e.g., UUID)
     */
    public static void addBannedPlayer(String playerId) {
        bannedPlayers.add(playerId);
        writeBanListToFile(); // Update the local file after adding a player
    }

    /**
     * Removes a player from the local banned list.
     * This does not upload to GitHub; it's only for local use.
     *
     * @param playerId The player's unique identifier (e.g., UUID)
     */
    public static void removeBannedPlayer(String playerId) {
        bannedPlayers.remove(playerId);
        writeBanListToFile(); // Update the local file after removing a player
    }

    /**
     * Gets the set of currently banned players.
     *
     * @return A set of banned player IDs
     */
    @Contract(value = " -> new", pure = true)
    public static @NotNull Set<String> getBannedPlayers() {
        return new HashSet<>(bannedPlayers);
    }
}
