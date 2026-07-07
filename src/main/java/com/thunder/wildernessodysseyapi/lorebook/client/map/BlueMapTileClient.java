package com.thunder.wildernessodysseyapi.lorebook.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.lorebook.map.CodexMapSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches BlueMap tiles over HTTP and uploads successful responses as dynamic
 * Minecraft textures.
 *
 * <p>HTTP and image decoding are separated from rendering so the Codex screen
 * can stay responsive while remote tiles arrive or fail independently.</p>
 */
public final class BlueMapTileClient {
    private static final BlueMapTileClient INSTANCE = new BlueMapTileClient();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<BlueMapTileAddress, TileEntry> tiles = new ConcurrentHashMap<>();

    private BlueMapTileClient() {
    }

    /** Returns the shared client-side BlueMap tile cache. */
    public static BlueMapTileClient get() {
        return INSTANCE;
    }

    /** Starts a tile request if the tile is not already cached or in-flight. */
    public TileEntry request(BlueMapTileAddress address, CodexMapSettings config) {
        TileEntry entry = tiles.computeIfAbsent(address, TileEntry::new);
        entry.touch();

        if (!entry.shouldRequest()) {
            return entry;
        }

        URI uri = buildTileUri(address, config);
        if (uri == null) {
            entry.fail("Bad BlueMap URL");
            return entry;
        }

        entry.requesting();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                .header("Accept", "image/png,image/*;q=0.8")
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body().length > 0) {
                        entry.acceptBytes(response.body());
                    } else {
                        entry.fail("HTTP " + response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null
                            ? error.getCause()
                            : error;
                    entry.fail(cause.getClass().getSimpleName());
                    return null;
                });

        return entry;
    }

    /** Uploads a completed response to Minecraft's texture manager on the render thread. */
    public ResourceLocation textureFor(TileEntry entry) {
        ResourceLocation existing = entry.textureLocation();
        if (existing != null) {
            return existing;
        }

        byte[] imageBytes = entry.consumeImageBytes();
        if (imageBytes == null) {
            return null;
        }

        try {
            NativeImage image = NativeImage.read(imageBytes);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    ModConstants.MOD_ID,
                    entry.address().resourcePath()
            );
            Minecraft.getInstance().getTextureManager().register(
                    location,
                    new DynamicTexture(image)
            );
            entry.textureReady(location);
            return location;
        } catch (IOException exception) {
            entry.fail("Image decode failed");
            return null;
        }
    }

    /** Releases older dynamic textures when the cache grows beyond the configured limit. */
    public void trimTo(int maxTiles) {
        int safeMax = Math.max(9, maxTiles);
        if (tiles.size() <= safeMax) {
            return;
        }

        tiles.values().stream()
                .sorted(Comparator.comparingLong(TileEntry::lastAccessMillis))
                .limit(tiles.size() - safeMax)
                .toList()
                .forEach(entry -> {
                    tiles.remove(entry.address());
                    ResourceLocation location = entry.textureLocation();
                    if (location != null) {
                        Minecraft.getInstance().getTextureManager().release(location);
                    }
                });
    }

    /** Clears all cached map textures, usually when the client disconnects. */
    public void clear() {
        tiles.values().forEach(entry -> {
            ResourceLocation location = entry.textureLocation();
            if (location != null) {
                Minecraft.getInstance().getTextureManager().release(location);
            }
        });
        tiles.clear();
    }

    private URI buildTileUri(BlueMapTileAddress address, CodexMapSettings config) {
        String base = config.normalizedBaseUrl();
        String map = config.normalizedMapId();
        String template = config.normalizedTemplate();
        if (base.isBlank() || map.isBlank() || template.isBlank()) {
            return null;
        }

        String url = template
                .replace("{base}", base)
                .replace("{map}", encodePathSegment(map))
                .replace("{zoom}", Integer.toString(address.zoom()))
                .replace("{x}", Integer.toString(address.x()))
                .replace("{z}", Integer.toString(address.z()));

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Mutable request state for one tile address. */
    public static final class TileEntry {
        private static final long ERROR_RETRY_DELAY_MS = 60_000L;

        private final BlueMapTileAddress address;
        private volatile TileState state = TileState.EMPTY;
        private volatile byte[] imageBytes;
        private volatile ResourceLocation textureLocation;
        private volatile String status = "";
        private volatile long lastAccessMillis = System.currentTimeMillis();
        private volatile long lastErrorMillis;

        private TileEntry(BlueMapTileAddress address) {
            this.address = address;
        }

        /** Returns the BlueMap tile address represented by this cache entry. */
        public BlueMapTileAddress address() {
            return address;
        }

        /** Returns the latest request lifecycle state for UI rendering. */
        public TileState state() {
            return state;
        }

        /** Returns the uploaded Minecraft texture, or null until the tile is ready. */
        public ResourceLocation textureLocation() {
            return textureLocation;
        }

        /** Returns a short status or error message for the map overlay. */
        public String status() {
            return status;
        }

        private long lastAccessMillis() {
            return lastAccessMillis;
        }

        private void touch() {
            this.lastAccessMillis = System.currentTimeMillis();
        }

        private boolean shouldRequest() {
            if (state == TileState.EMPTY) {
                return true;
            }
            return state == TileState.ERROR
                    && System.currentTimeMillis() - lastErrorMillis >= ERROR_RETRY_DELAY_MS;
        }

        private void requesting() {
            this.state = TileState.REQUESTING;
            this.status = "Loading";
        }

        private void acceptBytes(byte[] bytes) {
            this.imageBytes = bytes;
            this.state = TileState.READY_TO_UPLOAD;
            this.status = "Ready";
        }

        private byte[] consumeImageBytes() {
            byte[] bytes = this.imageBytes;
            this.imageBytes = null;
            return bytes;
        }

        private void textureReady(ResourceLocation textureLocation) {
            this.textureLocation = textureLocation;
            this.state = TileState.READY;
            this.status = "Ready";
        }

        private void fail(String message) {
            this.imageBytes = null;
            this.state = TileState.ERROR;
            this.status = message;
            this.lastErrorMillis = System.currentTimeMillis();
        }
    }

    /** Request lifecycle states used by the Codex map UI. */
    public enum TileState {
        EMPTY,
        REQUESTING,
        READY_TO_UPLOAD,
        READY,
        ERROR
    }
}
