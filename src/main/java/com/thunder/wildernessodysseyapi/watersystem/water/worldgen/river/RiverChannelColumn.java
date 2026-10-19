package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

/** A planned local column; topY may include falling water above the downstream free surface. */
public record RiverChannelColumn(int x, int z, int waterY, int floorY, int topY,
                                 float directionX, float directionZ, float speed, Kind kind) {
    public RiverChannelColumn {
        if (x < 0 || x >= 32 || z < 0 || z >= 32 || floorY >= waterY || topY < waterY
                || !Float.isFinite(directionX) || !Float.isFinite(directionZ)
                || !Float.isFinite(speed) || speed < 0 || speed > 8 || kind == null) {
            throw new IllegalArgumentException("Invalid generated river column");
        }
    }

    public enum Kind { STREAM, LAKE, WATERFALL }
}
