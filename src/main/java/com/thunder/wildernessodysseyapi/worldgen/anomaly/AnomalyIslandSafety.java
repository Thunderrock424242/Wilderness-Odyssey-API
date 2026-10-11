package com.thunder.wildernessodysseyapi.worldgen.anomaly;

/** Pure, bounded preflight used before the Minecraft template API receives permission to write. */
public final class AnomalyIslandSafety {
    public static final int MAX_WIDTH = 12;
    public static final int MAX_HEIGHT = 16;
    public static final int AIR_MARGIN = 1;
    public static final int MIN_GROUND_GAP = 8;
    private static final int BUILD_MARGIN = 2;

    public enum Rejection {
        NONE, TEMPLATE_UNKNOWN, TEMPLATE_MISSING, TEMPLATE_CONTENT, INVALID_BOUNDS,
        CHUNK_BOUNDARY, BUILD_HEIGHT, CHUNK_UNAVAILABLE, STRUCTURE_PRESENT,
        GROUND_CLEARANCE, BLOCK_ENTITY, OBSTRUCTED, WRITE_UNAVAILABLE, PLACE_FAILED
    }

    /** Inclusive bounds, deliberately independent of Minecraft initialization for policy tests. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) { }

    /** Read-only world boundary; the implementation must never load an unavailable chunk. */
    public interface Site {
        boolean chunkAvailable(int chunkX, int chunkZ);
        boolean structureProtected(int chunkX, int chunkZ);
        int groundHeight(int x, int z);
        boolean blockEntityAt(int x, int y, int z);
        boolean airAt(int x, int y, int z);
        boolean canWriteAt(int x, int y, int z);
    }

    private AnomalyIslandSafety() { }

    /** Validates serialized template coordinates and palette references before world inspection. */
    public static boolean templateBlockFits(int sizeX, int sizeY, int sizeZ, int paletteSize,
                                            int x, int y, int z, int state) {
        return sizeX > 0 && sizeX <= MAX_WIDTH && sizeY > 0 && sizeY <= MAX_HEIGHT
                && sizeZ > 0 && sizeZ <= MAX_WIDTH && paletteSize > 0
                && x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ
                && state >= 0 && state < paletteSize;
    }

    /** Checks all clearance cells and returns the first rejection before any placement is possible. */
    public static Rejection evaluate(Bounds bounds, int minBuildY, int maxBuildY, Site site) {
        long width = (long) bounds.maxX - bounds.minX + 1;
        long depth = (long) bounds.maxZ - bounds.minZ + 1;
        long height = (long) bounds.maxY - bounds.minY + 1;
        if (width <= 0 || depth <= 0 || height <= 0
                || width > MAX_WIDTH || depth > MAX_WIDTH || height > MAX_HEIGHT
                || bounds.minX == Integer.MIN_VALUE || bounds.minZ == Integer.MIN_VALUE
                || bounds.maxX >= Integer.MAX_VALUE - AIR_MARGIN
                || bounds.maxZ >= Integer.MAX_VALUE - AIR_MARGIN) {
            return Rejection.INVALID_BOUNDS;
        }
        int chunkX = Math.floorDiv(bounds.minX - AIR_MARGIN, 16);
        int chunkZ = Math.floorDiv(bounds.minZ - AIR_MARGIN, 16);
        if (chunkX != Math.floorDiv(bounds.maxX + AIR_MARGIN, 16)
                || chunkZ != Math.floorDiv(bounds.maxZ + AIR_MARGIN, 16)) {
            return Rejection.CHUNK_BOUNDARY;
        }
        if ((long) bounds.minY - AIR_MARGIN < (long) minBuildY + BUILD_MARGIN
                || (long) bounds.maxY + AIR_MARGIN >= (long) maxBuildY - BUILD_MARGIN) {
            return Rejection.BUILD_HEIGHT;
        }
        if (!site.chunkAvailable(chunkX, chunkZ)) {
            return Rejection.CHUNK_UNAVAILABLE;
        }
        if (site.structureProtected(chunkX, chunkZ)) {
            return Rejection.STRUCTURE_PRESENT;
        }
        for (int z = bounds.minZ - AIR_MARGIN; z <= bounds.maxZ + AIR_MARGIN; z++) {
            for (int x = bounds.minX - AIR_MARGIN; x <= bounds.maxX + AIR_MARGIN; x++) {
                if ((long) bounds.minY - site.groundHeight(x, z) < MIN_GROUND_GAP) {
                    return Rejection.GROUND_CLEARANCE;
                }
                for (int y = bounds.minY - AIR_MARGIN; y <= bounds.maxY + AIR_MARGIN; y++) {
                    if (!site.canWriteAt(x, y, z)) {
                        return Rejection.WRITE_UNAVAILABLE;
                    }
                    if (site.blockEntityAt(x, y, z)) {
                        return Rejection.BLOCK_ENTITY;
                    }
                    if (!site.airAt(x, y, z)) {
                        return Rejection.OBSTRUCTED;
                    }
                }
            }
        }
        return Rejection.NONE;
    }
}
