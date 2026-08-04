package com.thunder.wildernessodysseyapi.watersystem.water.api;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;

/**
 * Read-only four-by-four drainage-cell sample for gameplay and rendering.
 *
 * <p>The sample refines the chunk-scale watershed current without becoming a
 * separate water-volume authority.</p>
 */
public record WatershedLocalFlow(
        long basinId,
        int cell,
        DrainageDirection direction,
        int contributingCells,
        boolean confluence,
        float currentX,
        float currentZ
) {

    /** Shared dry result for disabled, unloaded, or unsynchronized chunks. */
    public static final WatershedLocalFlow NONE = new WatershedLocalFlow(
            0L, 0, DrainageDirection.SINK, 0, false, 0.0f, 0.0f
    );

    public WatershedLocalFlow {
        cell = Math.max(0, Math.min(15, cell));
        direction = direction == null ? DrainageDirection.SINK : direction;
        contributingCells = Math.max(0, Math.min(15, contributingCells));
        currentX = Float.isFinite(currentX) ? currentX : 0.0f;
        currentZ = Float.isFinite(currentZ) ? currentZ : 0.0f;
    }

    /** Returns the local current magnitude without allocating a vector. */
    public float currentStrength() {
        return (float) Math.hypot(currentX, currentZ);
    }
}
