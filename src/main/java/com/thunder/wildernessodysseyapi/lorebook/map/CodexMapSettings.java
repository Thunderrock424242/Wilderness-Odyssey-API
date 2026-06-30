package com.thunder.wildernessodysseyapi.lorebook.map;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Shared map profile used by the server sync payload and the client fallback
 * config.
 *
 * <p>The HTTP tile URL is data instead of code so a server owner can point the
 * Field Codex at their public BlueMap web root without shipping a custom jar.</p>
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
public record CodexMapSettings(
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
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_ID_LENGTH = 128;

    /** Writes this settings snapshot to a play-channel buffer. */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeUtf(baseUrl == null ? "" : baseUrl, MAX_URL_LENGTH);
        buffer.writeUtf(mapId == null ? "" : mapId, MAX_ID_LENGTH);
        buffer.writeUtf(tileUrlTemplate == null ? "" : tileUrlTemplate, MAX_URL_LENGTH);
        buffer.writeVarInt(tileZoom);
        buffer.writeVarInt(tilePixelSize);
        buffer.writeVarInt(blocksPerTile);
        buffer.writeVarInt(tileRadius);
        buffer.writeVarInt(cacheTiles);
        buffer.writeVarInt(requestTimeoutMs);
    }

    /** Reads a settings snapshot from a play-channel buffer. */
    public static CodexMapSettings decode(FriendlyByteBuf buffer) {
        return new CodexMapSettings(
                buffer.readBoolean(),
                buffer.readUtf(MAX_URL_LENGTH),
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_URL_LENGTH),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

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
