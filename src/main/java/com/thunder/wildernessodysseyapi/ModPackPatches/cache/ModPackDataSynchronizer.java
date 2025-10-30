package com.thunder.wildernessodysseyapi.ModPackPatches.cache;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Loads external modpack data listed in the optional data manifest and caches the resulting files.
 */
@EventBusSubscriber(modid = MOD_ID)
public final class ModPackDataSynchronizer {
    private static final Gson GSON = new Gson();
    private static final Path MANIFEST_PATH = FMLPaths.GAMEDIR.get()
            .resolve("config")
            .resolve("wilderness_odyssey")
            .resolve("data_manifest.json");
    private static final Map<String, Path> RESOLVED_FILES = new ConcurrentHashMap<>();

    private ModPackDataSynchronizer() {
    }

    /**
     * Returns the cached file for the given identifier if present.
     */
    public static Optional<Path> getCachedFile(String id) {
        return Optional.ofNullable(RESOLVED_FILES.get(id));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ModDataCache.initialize();
        RESOLVED_FILES.clear();
        if (!Files.exists(MANIFEST_PATH)) {
            LOGGER.info("[ModPackDataSync] No data manifest found at {}", MANIFEST_PATH);
            return;
        }

        try (Reader reader = Files.newBufferedReader(MANIFEST_PATH)) {
            ResourceManifest manifest = GSON.fromJson(reader, ResourceManifest.class);
            if (manifest == null || manifest.resources().isEmpty()) {
                LOGGER.info("[ModPackDataSync] Data manifest was empty");
                return;
            }
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            for (ResourceEntry entry : manifest.resources()) {
                processEntry(client, entry);
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.error("[ModPackDataSync] Failed to read data manifest {}: {}", MANIFEST_PATH, e.getMessage());
            if (ModDataCacheConfig.isVerboseLogging()) {
                LOGGER.debug("[ModPackDataSync] Manifest parsing exception", e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RESOLVED_FILES.clear();
    }

    private static void processEntry(HttpClient client, ResourceEntry entry) {
        if (entry == null || entry.id() == null || entry.uri() == null) {
            return;
        }
        String id = entry.id();
        String checksum = entry.checksum() == null ? "" : entry.checksum();
        URI uri;
        try {
            uri = URI.create(entry.uri());
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("[ModPackDataSync] Invalid URI for {}: {}", id, entry.uri());
            return;
        }

        Optional<Path> cached = ModDataCache.getOrDownload("manifest:" + id, checksum, () -> openStream(client, uri));
        cached.ifPresent(path -> RESOLVED_FILES.put(id, path));
    }

    private static InputStream openStream(HttpClient client, URI uri) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme()) || uri.getScheme() == null) {
            Path local = Path.of(uri.getPath());
            return Files.newInputStream(local);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new IOException("Unexpected HTTP status " + response.statusCode() + " for " + uri);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + uri, e);
        }
    }

    private record ResourceManifest(List<ResourceEntry> resources) {
        ResourceManifest {
            resources = resources == null ? Collections.emptyList() : List.copyOf(resources);
        }
    }

    private record ResourceEntry(String id, String uri, String checksum) {
    }
}
