package com.thunder.wildernessodysseyapi.lorebook.client.map;

/**
 * Identifies a BlueMap tile and converts player block positions into tile
 * space for the Codex map renderer.
 *
 * <p>The conversion is intentionally driven by config values because BlueMap
 * deployments can expose different tile sizes and detail levels.</p>
 */
public record BlueMapTileAddress(int x, int z, int zoom) {
    /** Returns the tile containing the supplied block-space coordinate. */
    public static BlueMapTileAddress containing(double blockX, double blockZ, int blocksPerTile, int zoom) {
        int safeBlocksPerTile = Math.max(1, blocksPerTile);
        int tileX = (int) Math.floor(blockX / safeBlocksPerTile);
        int tileZ = (int) Math.floor(blockZ / safeBlocksPerTile);
        return new BlueMapTileAddress(tileX, tileZ, zoom);
    }

    /** Returns the source tile pixel x-coordinate for a block-space x value. */
    public double pixelX(double blockX, int blocksPerTile, int tilePixelSize) {
        return localPixel(blockX, this.x, blocksPerTile, tilePixelSize);
    }

    /** Returns the source tile pixel z-coordinate for a block-space z value. */
    public double pixelZ(double blockZ, int blocksPerTile, int tilePixelSize) {
        return localPixel(blockZ, this.z, blocksPerTile, tilePixelSize);
    }

    private static double localPixel(double blockCoordinate, int tileCoordinate, int blocksPerTile, int tilePixelSize) {
        int safeBlocksPerTile = Math.max(1, blocksPerTile);
        int safeTilePixelSize = Math.max(1, tilePixelSize);
        double localBlocks = blockCoordinate - (double) tileCoordinate * safeBlocksPerTile;
        return (localBlocks / safeBlocksPerTile) * safeTilePixelSize;
    }

    public BlueMapTileAddress offset(int dx, int dz) {
        return new BlueMapTileAddress(this.x + dx, this.z + dz, this.zoom);
    }

    /** Returns a stable dynamic texture path for this tile. */
    public String resourcePath() {
        return "dynamic/bluemap/" + this.zoom + "/" + this.x + "_" + this.z;
    }
}
