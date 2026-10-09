package com.thunder.wildernessodysseyapi.diagnostics.performance;

/** Broad diagnostic areas; a candidate must also identify its narrower patch scope. */
public enum PerformanceArea {
    SAVE, CHUNK_IO, CHUNK_LOADING, WORLDGEN, LIGHTING, TICK_OPTIMIZATION,
    MEMORY, RESOURCE_LOADING, RENDERING, WO_BACKGROUND_WORK
}
