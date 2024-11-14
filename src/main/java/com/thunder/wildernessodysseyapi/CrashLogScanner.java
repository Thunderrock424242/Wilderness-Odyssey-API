package com.thunder.wildernessodysseyapi;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class CrashLogScanner {
    private static final Set<String> knownModPackages = new HashSet<>();

    public CrashLogScanner() {
        // Add known mod packages for analysis
        knownModPackages.add("com.mymod");
        knownModPackages.add("net.modpackage");
    }

    // Server-side log analysis
    @SubscribeEvent
    public void onServerStopping(@NotNull ServerStoppingEvent event) {
        Path logsDirectory = event.getServer().getFile("logs").toPath();  // Get the logs directory as a Path object
        Path logFilePath = logsDirectory.resolve("latest.log");   // Resolve the path for the latest.log file
        analyzeLogs(logFilePath);
    }

    // Client-side log analysis
    @OnlyIn(Dist.CLIENT)
    public void onClientStopping() {
        File logFile = new File(Minecraft.getInstance().gameDirectory, "logs/latest.log");
        analyzeLogs(logFile.toPath());
    }

    private void analyzeLogs(Path logFilePath) {
        if (Files.exists(logFilePath)) {
            try (BufferedReader reader = Files.newBufferedReader(logFilePath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    for (String modPackage : knownModPackages) {
                        if (line.contains(modPackage)) {
                            WildernessOdysseyAPIMainModClass.LOGGER.error("Found possible mod involvement: " + line);
                        }
                    }
                }
            } catch (IOException e) {
                WildernessOdysseyAPIMainModClass.LOGGER.error("Failed to analyze crash report", e);
            }
        }
    }
}
