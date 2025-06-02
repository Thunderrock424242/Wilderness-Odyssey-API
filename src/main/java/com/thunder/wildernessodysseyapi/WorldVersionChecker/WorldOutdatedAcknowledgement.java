package com.thunder.wildernessodysseyapi.WorldVersionChecker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class WorldOutdatedAcknowledgement {

    private static final String FILE_NAME = "WORLD_OUTDATED.txt";

    public static void validateOrCrash(Path worldPath, int currentVersion, int loadedVersion) {
        if (loadedVersion >= currentVersion) return; // No warning needed

        Path warnFile = worldPath.resolve(FILE_NAME);
        if (!Files.exists(warnFile)) {
            generateWarningFile(warnFile, currentVersion, loadedVersion);
            crash("World is outdated. Read and accept WORLD_OUTDATED.txt before starting the server.");
        }

        try {
            List<String> lines = Files.readAllLines(warnFile);
            Optional<String> acceptedLine = lines.stream()
                    .map(String::trim)
                    .filter(l -> l.startsWith("accepted="))
                    .findFirst();

            if (acceptedLine.isEmpty() || !acceptedLine.get().equalsIgnoreCase("accepted=true")) {
                crash("WORLD_OUTDATED.txt found but not accepted. Set accepted=true to continue.");
            }
        } catch (IOException e) {
            crash("Failed to read WORLD_OUTDATED.txt: " + e.getMessage());
        }
    }

    private static void generateWarningFile(Path file, int current, int found) {
        String content = """
            # WARNING: This world was created with an older version of Wilderness Odyssey.
            # Minecraft may attempt to update it automatically, but bugs or corruption may occur.
            #
            # Current version: %d
            # Found version: %d
            #
            # Please:
            # 1. BACK UP YOUR WORLD BEFORE CONTINUING.
            # 2. TEST BUGS IN A CLEAN WORLD BEFORE REPORTING.
            #
            # Set the following to true after reading and accepting this warning:
            accepted=false
            """.formatted(current, found);

        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            crash("Failed to write WORLD_OUTDATED.txt: " + e.getMessage());
        }
    }

    private static void crash(String reason) {
        System.err.println("[NovaAPI] " + reason);
        throw new RuntimeException(reason);
    }
}
