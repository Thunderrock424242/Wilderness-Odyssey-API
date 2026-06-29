package com.thunder.wildernessodysseyapi.lorebook.map;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side settings for the Field Codex map tab.
 *
 * <p>The map renderer consumes BlueMap's HTTP tile output instead of depending
 * on BlueMap classes directly. Keeping the URL shape configurable lets servers
 * adapt this feature to their BlueMap web-root and storage layout.</p>
 */
public final class CodexMapConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> MAP_ID;
    public static final ModConfigSpec.ConfigValue<String> TILE_URL_TEMPLATE;
    public static final ModConfigSpec.IntValue TILE_ZOOM;
    public static final ModConfigSpec.IntValue TILE_PIXEL_SIZE;
    public static final ModConfigSpec.IntValue BLOCKS_PER_TILE;
    public static final ModConfigSpec.IntValue TILE_RADIUS;
    public static final ModConfigSpec.IntValue CACHE_TILES;
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_MS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Client-side Field Codex map options.")
                .push("field_codex_map");

        ENABLED = builder
                .comment("Enable the live BlueMap-backed map tab in the Wilderness Field Codex.")
                .define("enabled", true);
        BASE_URL = builder
                .comment("Base URL of the BlueMap web server. This is usually http://localhost:8100 for local testing.")
                .define("baseUrl", "http://localhost:8100");
        MAP_ID = builder
                .comment("BlueMap map identifier used in tile URLs. Server owners can confirm this in their BlueMap web data.")
                .define("mapId", "world");
        TILE_URL_TEMPLATE = builder
                .comment(
                        "Tile URL template. Supported placeholders: {base}, {map}, {zoom}, {x}, {z}.",
                        "Adjust this if your BlueMap web-root stores tiles under a different path."
                )
                .define("tileUrlTemplate", "{base}/maps/{map}/tiles/{zoom}/{x}_{z}.png");
        TILE_ZOOM = builder
                .comment("Tile zoom/detail value inserted into {zoom}. The on-screen +/- controls only scale the loaded tiles.")
                .defineInRange("tileZoom", 0, 0, 16);
        TILE_PIXEL_SIZE = builder
                .comment("Pixel width and height of each BlueMap tile image.")
                .defineInRange("tilePixelSize", 512, 64, 2048);
        BLOCKS_PER_TILE = builder
                .comment("World blocks covered by one BlueMap tile at the configured tileZoom.")
                .defineInRange("blocksPerTile", 512, 16, 8192);
        TILE_RADIUS = builder
                .comment("How many tiles to request outward from the player's current tile.")
                .defineInRange("tileRadius", 2, 1, 4);
        CACHE_TILES = builder
                .comment("Maximum BlueMap tile textures retained by the client.")
                .defineInRange("cacheTiles", 96, 9, 512);
        REQUEST_TIMEOUT_MS = builder
                .comment("HTTP timeout for one tile request.")
                .defineInRange("requestTimeoutMs", 2500, 250, 15000);

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private CodexMapConfig() {
    }

    /** Returns a stable snapshot of map settings for one render/request pass. */
    public static Values values() {
        return new Values(
                ENABLED.get(),
                BASE_URL.get(),
                MAP_ID.get(),
                TILE_URL_TEMPLATE.get(),
                TILE_ZOOM.get(),
                TILE_PIXEL_SIZE.get(),
                BLOCKS_PER_TILE.get(),
                TILE_RADIUS.get(),
                CACHE_TILES.get(),
                REQUEST_TIMEOUT_MS.get()
        );
    }

    /**
     * Immutable map settings used by the tile fetcher.
     *
     * @param enabled whether the Codex map should render BlueMap tiles
     * @param baseUrl configured BlueMap HTTP server root
     * @param mapId configured BlueMap map id
     * @param tileUrlTemplate URL template with BlueMap tile placeholders
     * @param tileZoom server tile zoom/detail placeholder value
     * @param tilePixelSize source tile image size in pixels
     * @param blocksPerTile world blocks represented by one source tile
     * @param tileRadius number of neighboring tile rings rendered around the player
     * @param cacheTiles maximum retained dynamic textures
     * @param requestTimeoutMs per-request HTTP timeout
     */
    public record Values(
            boolean enabled,
            String baseUrl,
            String mapId,
            String tileUrlTemplate,
            int tileZoom,
            int tilePixelSize,
            int blocksPerTile,
            int tileRadius,
            int cacheTiles,
            int requestTimeoutMs
    ) {
        /** Returns the configured BlueMap web root without trailing slashes. */
        public String normalizedBaseUrl() {
            if (baseUrl == null) {
                return "";
            }
            String trimmed = baseUrl.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }

        /** Returns the map id trimmed for placeholder insertion. */
        public String normalizedMapId() {
            return mapId == null ? "" : mapId.trim();
        }

        /** Returns the tile template trimmed for URL construction. */
        public String normalizedTemplate() {
            return tileUrlTemplate == null ? "" : tileUrlTemplate.trim();
        }
    }
}
