package com.thunder.wildernessodysseyapi.ocean.util;

import net.minecraft.core.BlockPos;

import java.util.HashMap;

public class WaveHeightmap {
    private static final WaveHeightmap INSTANCE = new WaveHeightmap();
    private final HashMap<BlockPos, Double> heightMap = new HashMap<>();

    public static WaveHeightmap getInstance() {
        return INSTANCE;
    }

    public void setHeight(BlockPos pos, double height) {
        heightMap.put(pos, height);
    }

    public double getHeight(BlockPos pos) {
        return heightMap.getOrDefault(pos, 0.0);
    }

    public Iterable<BlockPos> getAllPositions() {
        return heightMap.keySet();
    }
}