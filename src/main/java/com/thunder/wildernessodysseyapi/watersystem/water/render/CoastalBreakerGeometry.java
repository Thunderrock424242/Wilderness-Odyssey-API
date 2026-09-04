package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSegment;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveModel;

/** Pure cross-section for a rolling crest, bounded to the cached water strip. */
final class CoastalBreakerGeometry {

    static final int QUADS_PER_CREST = 3;

    private CoastalBreakerGeometry() {
    }

    /** Interpolates the crest instead of snapping it between whole water cells. */
    static Shape sample(CoastalSegment.ShorelinePoint point, CoastalWaveModel.Sample wave) {
        var cells = point.nearshoreCells();
        var first = cells.getFirst();
        var last = cells.getLast();
        float distance = Math.max(first.distanceFromShoreBlocks(), Math.min(
                last.distanceFromShoreBlocks(), wave.crestDistanceFromShoreBlocks()));
        float surfaceY = first.waterSurfaceY();
        for (int index = 1; index < cells.size(); index++) {
            var previous = cells.get(index - 1);
            var next = cells.get(index);
            if (distance <= next.distanceFromShoreBlocks()) {
                float span = next.distanceFromShoreBlocks() - previous.distanceFromShoreBlocks();
                float fraction = span > 0.0f
                        ? (distance - previous.distanceFromShoreBlocks()) / span : 0.0f;
                surfaceY = previous.waterSurfaceY()
                        + (next.waterSurfaceY() - previous.waterSurfaceY()) * fraction;
                break;
            }
        }
        float height = switch (wave.stage()) {
            case INCOMING, SHOALING -> wave.waveHeight();
            case BREAKING -> wave.breakerLift();
            case RUN_UP, RETREAT -> 0.0f;
        };
        // Offsets are positive landward. Both feet stay on sampled water, even
        // beside a cliff or when the seaward trace ends at an unloaded chunk.
        float back = Math.min(1.2f + height * 0.85f,
                last.distanceFromShoreBlocks() - distance + 0.45f);
        float front = Math.min(0.65f + height * 0.30f,
                distance - first.distanceFromShoreBlocks() + 0.45f);
        return new Shape(distance, surfaceY, height, -back, front * 0.35f,
                height * 0.90f, front);
    }

    /** The three faces join back foot, crest, curled lip, and landward foot. */
    record Shape(
            float distanceFromShore,
            float surfaceY,
            float crestHeight,
            float backOffset,
            float lipOffset,
            float lipHeight,
            float frontOffset
    ) {
    }
}
