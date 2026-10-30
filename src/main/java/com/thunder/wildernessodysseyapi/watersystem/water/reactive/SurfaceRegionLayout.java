package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import net.minecraft.core.BlockPos;

import java.util.List;

/** One connected, non-overlapping geometric footprint; not a volume snapshot. */
record SurfaceRegionLayout(int ceilingY, List<SurfaceColumn> columns) {

    SurfaceRegionLayout { columns = List.copyOf(columns); }

    List<BlockPos> cells() { return columns.stream().flatMap(column -> column.cells().stream()).toList(); }
}
