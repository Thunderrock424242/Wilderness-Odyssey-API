package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.server.level.ServerLevel;

/** Moves accepted parcels from a packed donor surface into the receiver's lowest capacity. */
final class CanonicalColumnFluxBridge {

    private CanonicalColumnFluxBridge() { }

    static int transfer(ServerLevel level, SurfaceColumn donor, SurfaceColumn receiver,
                        int units, float vx, float vz) {
        if (units <= 0) return 0;
        int moved = 0;
        int from = donor.height() - 1;
        int to = 0;
        while (from >= 0 && to < receiver.height() && moved < units) {
            var sourcePos = donor.bottom().above(from);
            var targetPos = receiver.bottom().above(to);
            var source = CanonicalWater.get(level, sourcePos);
            var target = CanonicalWater.get(level, targetPos);
            if (source.volumeUnits() == 0) { from--; continue; }
            if (target.volumeUnits() == WaterVolumeChunk.UNITS_PER_BLOCK) { to++; continue; }
            int requested = Math.min(units - moved, Math.min(source.volumeUnits(),
                    WaterVolumeChunk.UNITS_PER_BLOCK - target.volumeUnits()));
            int accepted = CanonicalFluxBridge.transfer(level, sourcePos, targetPos, requested, vx, vz);
            if (accepted == 0) break;
            moved += accepted;
        }
        return moved;
    }
}
