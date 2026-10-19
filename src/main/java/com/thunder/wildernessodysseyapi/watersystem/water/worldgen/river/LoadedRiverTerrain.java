package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;

/** Server-thread diagnostic adapter; never admits hydrology, creates attachments or requests chunks. */
public final class LoadedRiverTerrain {
    private static final int REGION_CHUNKS = 8;
    private static final int HALO_CHUNKS = 1;
    private static final int SIZE = REGION_CHUNKS + HALO_CHUNKS * 2;

    private LoadedRiverTerrain() { }

    /** Captures one world-aligned eight-chunk region and one-chunk halo (100 probes). */
    public static RiverTerrainSnapshot capture(ServerLevel level, BlockPos position) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Loaded river terrain capture requires the server thread");
        }
        int minimumX = Math.floorDiv(position.getX() >> 4, REGION_CHUNKS) * REGION_CHUNKS - HALO_CHUNKS;
        int minimumZ = Math.floorDiv(position.getZ() >> 4, REGION_CHUNKS) * REGION_CHUNKS - HALO_CHUNKS;
        var layout = new RiverTerrainSnapshot.Layout(minimumX, minimumZ, SIZE, SIZE, 16, SIZE * SIZE);
        var key = new RiverTerrainSnapshot.Key(level.getSeed(), level.dimension().location().toString(), 0);
        return RiverTerrainSnapshot.capture(key, layout, (x, z) -> {
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) return null;
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            if (y < level.getMinBuildHeight()) return null;
            return new RiverTerrainSnapshot.Terrain(y,
                    chunk.getBlockState(new BlockPos(x, y, z)).getFluidState().is(FluidTags.WATER));
        });
    }
}
