package com.thunder.wildernessodysseyapi.lorebook.map;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-owned defaults for the Field Codex map sync.
 *
 * <p>These values are sent to clients when the Codex opens so a multiplayer
 * server can advertise the correct public BlueMap URL and matching tile scale.</p>
 */
public final class CodexMapServerConfig {
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
    public static final ModConfigSpec.BooleanValue SYNC_WORLD_POIS;
    public static final ModConfigSpec.IntValue MAX_SYNCED_POIS;
    public static final ModConfigSpec.BooleanValue PUBLISH_BLUEMAP_MARKERS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Server-side Field Codex map sync options.")
                .push("field_codex_map");

        ENABLED = builder
                .comment("Tell clients to use the server-provided BlueMap tile settings when the Field Codex opens.")
                .define("enabled", true);
        BASE_URL = builder
                .comment("Public BlueMap web URL clients should use for map tiles.")
                .define("baseUrl", "http://localhost:8100");
        MAP_ID = builder
                .comment("BlueMap map id used by both the Codex tile URL and optional BlueMap marker publishing.")
                .define("mapId", "world");
        TILE_URL_TEMPLATE = builder
                .comment(
                        "Optional PNG tile URL template. Supported placeholders: {base}, {map}, {zoom}, {x}, {z}.",
                        "BlueMap 5 stores native web tiles as .prbm.gz data, not PNGs, so leave this empty unless you expose a PNG tile proxy."
                )
                .define("tileUrlTemplate", "");
        TILE_ZOOM = builder
                .comment("Tile zoom/detail value inserted into {zoom}.")
                .defineInRange("tileZoom", 0, 0, 16);
        TILE_PIXEL_SIZE = builder
                .comment("Pixel width and height of each BlueMap tile image.")
                .defineInRange("tilePixelSize", 512, 64, 2048);
        BLOCKS_PER_TILE = builder
                .comment("World blocks covered by one BlueMap tile at the configured tileZoom.")
                .defineInRange("blocksPerTile", 512, 16, 8192);
        TILE_RADIUS = builder
                .comment("How many tiles clients should request outward from the player's current tile.")
                .defineInRange("tileRadius", 2, 1, 4);
        CACHE_TILES = builder
                .comment("Maximum BlueMap tile textures retained by the client.")
                .defineInRange("cacheTiles", 96, 9, 512);
        REQUEST_TIMEOUT_MS = builder
                .comment("HTTP timeout for one tile request.")
                .defineInRange("requestTimeoutMs", 2500, 250, 15000);
        SYNC_WORLD_POIS = builder
                .comment("Send known Wilderness Odyssey world POIs to the Field Codex map.")
                .define("syncWorldPois", true);
        MAX_SYNCED_POIS = builder
                .comment("Maximum POIs sent to one client when opening the Codex.")
                .defineInRange("maxSyncedPois", 128, 0, 512);
        PUBLISH_BLUEMAP_MARKERS = builder
                .comment("Publish the same Wilderness Odyssey POIs as BlueMap web markers when BlueMap is installed.")
                .define("publishBlueMapMarkers", true);

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private CodexMapServerConfig() {
    }

    /** Returns the server-advertised map settings snapshot. */
    public static CodexMapSettings settings() {
        return new CodexMapSettings(
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
}
