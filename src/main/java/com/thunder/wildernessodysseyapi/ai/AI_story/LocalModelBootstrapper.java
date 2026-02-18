package com.thunder.wildernessodysseyapi.ai.AI_story;

import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Downloads and verifies a local model artifact for sidecar runtimes.
 */
public final class LocalModelBootstrapper {

    private LocalModelBootstrapper() {
    }

    public static Path ensureModelFile(String downloadUrl, String expectedSha256, String fileName, Path modelsDir) {
        if (downloadUrl == null || downloadUrl.isBlank() || fileName == null || fileName.isBlank()) {
            return null;
        }
        try {
            Files.createDirectories(modelsDir);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to create model directory {}.", modelsDir, e);
            return null;
        }

        Path modelPath = modelsDir.resolve(fileName.trim());
        String normalizedHash = normalizeSha256(expectedSha256);

        if (Files.exists(modelPath) && isHashValid(modelPath, normalizedHash)) {
            return modelPath;
        }

        Path tmpPath = modelPath.resolveSibling(modelPath.getFileName() + ".download");
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl.trim())).GET().build();
        try {
            HttpResponse<InputStream> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                ModConstants.LOGGER.warn("Failed to download model file from {} with status {}.", downloadUrl, response.statusCode());
                return null;
            }
            try (InputStream in = response.body()) {
                Files.copy(in, tmpPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!isHashValid(tmpPath, normalizedHash)) {
                Files.deleteIfExists(tmpPath);
                ModConstants.LOGGER.warn("Downloaded model checksum mismatch for {}.", modelPath);
                return null;
            }
            Files.move(tmpPath, modelPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            ModConstants.LOGGER.info("Downloaded local model artifact to {}.", modelPath);
            return modelPath;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {
                // best effort cleanup
            }
            ModConstants.LOGGER.warn("Failed downloading local model artifact from {}.", downloadUrl, e);
            return null;
        }
    }

    private static String normalizeSha256(String hash) {
        if (hash == null) {
            return "";
        }
        return hash.trim().toLowerCase();
    }

    private static boolean isHashValid(Path file, String expectedSha256) {
        if (!Files.exists(file)) {
            return false;
        }
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return true;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest()).toLowerCase();
            return actual.equals(expectedSha256);
        } catch (IOException | NoSuchAlgorithmException e) {
            ModConstants.LOGGER.warn("Failed to verify model checksum for {}.", file, e);
            return false;
        }
    }
}
