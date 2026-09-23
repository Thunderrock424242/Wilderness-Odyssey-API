package com.thunder.aether.server.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BundleInstallerTest {
    @TempDir Path temporary;

    private BundleManifest manifest(String path, byte[] content) throws Exception {
        return new BundleManifest(1, BundleManifest.currentPlatform(), "0.17.7", path,
                "llama3.1:8b", "aether-custom:8b", List.of(new BundleManifest.Asset(
                path, content.length, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)))));
    }

    @Test void extractsVerifiedBytesAndReusesInstalledFilesWithoutReadingBundleAgain() throws Exception {
        byte[] bytes = "native test fixture".getBytes();
        var manifest = manifest("runtime/ollama", bytes);
        BundleInstaller.install(temporary, manifest, path -> new ByteArrayInputStream(bytes));
        assertArrayEquals(bytes, Files.readAllBytes(temporary.resolve("runtime/ollama")));
        BundleInstaller.install(temporary, manifest, path -> { throw new IOException("should reuse valid asset"); });
    }

    @Test void reclaimsOnlyOwnedAbandonedExtractionFilesBeforeRetry() throws Exception {
        byte[] bytes = "complete".getBytes();
        Files.createDirectories(temporary.resolve("runtime"));
        Path abandoned = temporary.resolve("runtime/.aether-extract-123456.part");
        Path unrelated = temporary.resolve("runtime/.aether-extract-user.part");
        Path outsideAssetDirectory = temporary.resolve(".aether-extract-999.part");
        Files.writeString(abandoned, "incomplete");
        Files.writeString(unrelated, "operator file");
        Files.writeString(outsideAssetDirectory, "unrelated file");
        BundleInstaller.install(temporary, manifest("runtime/ollama", bytes),
                path -> new ByteArrayInputStream(bytes));
        assertFalse(Files.exists(abandoned));
        assertEquals("operator file", Files.readString(unrelated));
        assertEquals("unrelated file", Files.readString(outsideAssetDirectory));
    }
    @Test void rejectsWrongHashBeforePublishingInstalledFile() throws Exception {
        var manifest = manifest("runtime/ollama", "expected".getBytes());
        assertThrows(IOException.class, () -> BundleInstaller.install(temporary, manifest,
                path -> new ByteArrayInputStream("tampered".getBytes())));
        assertFalse(Files.exists(temporary.resolve("runtime/ollama")));
    }

    @Test void repairsAnInterruptedOrDamagedManagedAsset() throws Exception {
        byte[] bytes = "correct bytes".getBytes();
        Files.createDirectories(temporary.resolve("runtime"));
        Files.writeString(temporary.resolve("runtime/ollama"), "damaged");
        BundleInstaller.install(temporary, manifest("runtime/ollama", bytes),
                path -> new ByteArrayInputStream(bytes));
        assertArrayEquals(bytes, Files.readAllBytes(temporary.resolve("runtime/ollama")));
    }

    @Test void rejectsTraversalAndConfigurationOverwriteBeforeInstallation() {
        for (String path : List.of("../escape", "/runtime/escape", "runtime/../escape",
                "runtime/a:b", "runtime/\\escape", "runtime/CON", "models/nul.txt", "runtime/com1.dll", "aether-server.yml")) {
            assertThrows(IllegalArgumentException.class, () -> manifest(path, new byte[]{1}));
        }
    }

    @Test void rejectsFilesThatConflictAsDirectories() {
        String hash = "0".repeat(64);
        assertThrows(IllegalArgumentException.class, () -> new BundleManifest(1,
                BundleManifest.currentPlatform(), "0.17.7", "runtime/ollama",
                "llama3.1:8b", "aether-custom:8b", List.of(
                new BundleManifest.Asset("runtime/ollama", 1, hash),
                new BundleManifest.Asset("runtime/ollama/child", 1, hash))));
    }

    @Test void applicationDirectoryCannotBeOwnedByTwoLaunchers() throws Exception {
        try (DataDirectory first = DataDirectory.open(temporary)) {
            assertThrows(IOException.class, () -> DataDirectory.open(temporary));
        }
        try (DataDirectory second = DataDirectory.open(temporary)) {
            assertEquals(temporary.toAbsolutePath(), second.path());
        }
    }

    @Test void firstSetupNeedsNoCredentialAndPreservesOperatorChangesOnRestart() throws Exception {
        var bundle = manifest("runtime/ollama", new byte[]{1});
        Path config = SetupConfig.ensure(temporary, bundle, Map.of("SERVER_PORT", "25565"));
        var initial = com.thunder.aether.server.config.ServerConfig.load(config, Map.of());
        assertEquals(25565, initial.port());
        assertEquals("aether-custom:8b", initial.model());
        assertFalse(Files.readString(config).contains("api_keys"));
        String edited = Files.readString(config).replace("25565", "24444");
        Files.writeString(config, edited);
        SetupConfig.ensure(temporary, bundle, Map.of("SERVER_PORT", "25566"));
        assertEquals(edited, Files.readString(config));
    }

    @Test void invalidHostingPortCannotCreateConfiguration() throws Exception {
        var bundle = manifest("runtime/ollama", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> SetupConfig.ensure(
                temporary, bundle, Map.of("SERVER_PORT", "0")));
        assertFalse(Files.exists(temporary.resolve("aether-server.yml")));
    }
}
