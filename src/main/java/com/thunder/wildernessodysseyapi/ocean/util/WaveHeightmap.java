package com.thunder.wildernessodysseyapi.ocean.util;

import net.minecraft.core.BlockPos;

import java.util.HashMap;

/**
 * The type Wave heightmap.
 */
public class WaveHeightmap {
    private static final WaveHeightmap INSTANCE = new WaveHeightmap();
    private final HashMap<BlockPos, Double> heightMap = new HashMap<>();

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static WaveHeightmap getInstance() {
        return INSTANCE;
    }

    /**
     * Sets height.
     *
     * @param pos    the pos
     * @param height the height
     */
    public void setHeight(BlockPos pos, double height) {
        heightMap.put(pos, height);
    }

    /**
     * Gets height.
     *
     * @param pos the pos
     * @return the height
     */
    public double getHeight(BlockPos pos) {
        return heightMap.getOrDefault(pos, 0.0);
    }

    /**
     * Gets all positions.
     *
     * @return the all positions
     */
    public Iterable<BlockPos> getAllPositions() {
        return heightMap.keySet();
    }
}