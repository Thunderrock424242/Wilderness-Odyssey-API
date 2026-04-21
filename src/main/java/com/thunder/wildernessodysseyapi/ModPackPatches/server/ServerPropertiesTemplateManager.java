package com.thunder.wildernessodysseyapi.ModPackPatches.server;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Keeps a distributable server.properties template in sync with the live server file.
 * <p>
 * Workflow:
 * 1. First run creates {@code config/<modid>/server.properties} from bundled defaults.
 * 2. A world-local template is stored at {@code <world>/server.properties.wildernessodyssey}.
 * 3. On start, world template (if present) is synced into live {@code server.properties}
 * with backups. This lets world moves carry server settings with them.
 */
public final class ServerPropertiesTemplateManager {
    private static final String BUNDLED_TEMPLATE = "/defaults/server.properties";
    private static final String WORLD_TEMPLATE_FILE = "server.properties.wildernessodyssey";
    private static final String BUNDLED_HASH_STATE = "server-properties-template.sha256";

    private ServerPropertiesTemplateManager() {
    }

    public static void ensureManagedServerProperties(MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            return;
        }

        Path liveFile = server.getFile("server.properties").toPath();
        Path globalManagedFile = server.getFile("config/" + ModConstants.MOD_ID + "/server.properties").toPath();
        Path hashStateFile = server.getFile("config/" + ModConstants.MOD_ID + "/" + BUNDLED_HASH_STATE).toPath();
        Path worldManagedFile = server.getWorldPath(LevelResource.ROOT).resolve(WORLD_TEMPLATE_FILE);

        try {
            ensureGlobalManagedFileExists(globalManagedFile, liveFile);
            ensureWorldManagedFileExists(worldManagedFile, globalManagedFile, liveFile);
            applyBundledTemplateUpdates(globalManagedFile, worldManagedFile, hashStateFile);

            Path preferredSource = Files.exists(worldManagedFile) ? worldManagedFile : globalManagedFile;
            syncManagedToLive(preferredSource, liveFile);

            if (Files.exists(globalManagedFile) && Files.exists(worldManagedFile)
                    && Files.mismatch(globalManagedFile, worldManagedFile) != -1) {
                Files.copy(worldManagedFile, globalManagedFile, StandardCopyOption.REPLACE_EXISTING);
                ModConstants.LOGGER.info("[ServerProperties] Updated global managed template from world template: {}", globalManagedFile);
            }
        } catch (IOException e) {
            ModConstants.LOGGER.warn("[ServerProperties] Failed to synchronize managed server.properties: {}", e.getMessage());
        }
    }

    private static void applyBundledTemplateUpdates(Path globalManagedFile, Path worldManagedFile, Path hashStateFile) throws IOException {
        byte[] bundledBytes = loadBundledTemplateBytes();
        if (bundledBytes == null || bundledBytes.length == 0) {
            return;
        }

        String newHash = sha256Hex(bundledBytes);
        String existingHash = readHashState(hashStateFile);
        if (newHash.equals(existingHash)) {
            return;
        }

        Files.write(globalManagedFile, bundledBytes);
        Files.write(worldManagedFile, bundledBytes);
        writeHashState(hashStateFile, newHash);
        ModConstants.LOGGER.info("[ServerProperties] Bundled template changed; replaced managed global/world templates.");
    }

    private static byte[] loadBundledTemplateBytes() throws IOException {
        try (InputStream templateStream = ServerPropertiesTemplateManager.class.getResourceAsStream(BUNDLED_TEMPLATE)) {
            if (templateStream == null) {
                return null;
            }
            return templateStream.readAllBytes();
        }
    }

    private static String readHashState(Path hashStateFile) throws IOException {
        if (!Files.exists(hashStateFile)) {
            return "";
        }
        return Files.readString(hashStateFile, StandardCharsets.UTF_8).trim();
    }

    private static void writeHashState(Path hashStateFile, String hash) throws IOException {
        Path parent = hashStateFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(hashStateFile, hash, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void ensureGlobalManagedFileExists(Path managedFile, Path liveFile) throws IOException {
        if (Files.exists(managedFile)) {
            return;
        }
        Path parent = managedFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream templateStream = ServerPropertiesTemplateManager.class.getResourceAsStream(BUNDLED_TEMPLATE)) {
            if (templateStream != null) {
                Files.copy(templateStream, managedFile, StandardCopyOption.REPLACE_EXISTING);
                ModConstants.LOGGER.info("[ServerProperties] Created global managed template from bundled defaults: {}", managedFile);
                return;
            }
        }

        if (Files.exists(liveFile)) {
            Files.copy(liveFile, managedFile, StandardCopyOption.REPLACE_EXISTING);
            ModConstants.LOGGER.info("[ServerProperties] Created global managed template from live server.properties: {}", managedFile);
        }
    }

    private static void ensureWorldManagedFileExists(Path worldManagedFile, Path globalManagedFile, Path liveFile) throws IOException {
        if (Files.exists(worldManagedFile)) {
            return;
        }

        if (Files.exists(globalManagedFile)) {
            Files.copy(globalManagedFile, worldManagedFile, StandardCopyOption.REPLACE_EXISTING);
            ModConstants.LOGGER.info("[ServerProperties] Created world template from global managed template: {}", worldManagedFile);
            return;
        }

        if (Files.exists(liveFile)) {
            Files.copy(liveFile, worldManagedFile, StandardCopyOption.REPLACE_EXISTING);
            ModConstants.LOGGER.info("[ServerProperties] Created world template from live server.properties: {}", worldManagedFile);
        }
    }

    private static void syncManagedToLive(Path managedFile, Path liveFile) throws IOException {
        if (!Files.exists(managedFile)) {
            return;
        }

        if (Files.exists(liveFile) && Files.mismatch(managedFile, liveFile) == -1) {
            return;
        }

        if (Files.exists(liveFile)) {
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
            Path backup = liveFile.resolveSibling("server.properties.backup-" + timestamp);
            Files.copy(liveFile, backup, StandardCopyOption.REPLACE_EXISTING);
            ModConstants.LOGGER.info("[ServerProperties] Backed up existing server.properties to {}", backup);
        }

        Files.copy(managedFile, liveFile, StandardCopyOption.REPLACE_EXISTING);
        ModConstants.LOGGER.info("[ServerProperties] Synced {} into live server.properties (applies on next restart)", managedFile);
    }
}
