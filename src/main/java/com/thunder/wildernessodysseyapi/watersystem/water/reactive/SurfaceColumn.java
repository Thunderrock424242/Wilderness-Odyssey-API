package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.stream.IntStream;

/** Immutable capacity geometry; every represented unit remains in canonical cells. */
record SurfaceColumn(BlockPos bottom, int height) {

    SurfaceColumn {
        bottom = bottom.immutable();
        if (height < 1 || height > 8) throw new IllegalArgumentException("Unsupported surface column height");
    }

    int topY() { return bottom.getY() + height; }

    int capacity() { return height * WaterVolumeChunk.UNITS_PER_BLOCK; }

    boolean contains(BlockPos position) {
        return position.getX() == bottom.getX() && position.getZ() == bottom.getZ()
                && position.getY() >= bottom.getY() && position.getY() < topY();
    }

    List<BlockPos> cells() { return IntStream.range(0, height).mapToObj(bottom::above).toList(); }

    int volume(ServerLevel level) {
        int total = 0;
        for (int offset = 0; offset < height; offset++) total += CanonicalWater.get(level, bottom.above(offset)).volumeUnits();
        return total;
    }
}
