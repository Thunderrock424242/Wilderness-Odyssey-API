package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WaterMutationSafety;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Exact accepted-credit bridge for the initial ordinary-water surface regions. */
final class CanonicalFluxBridge {

    private CanonicalFluxBridge() { }

    static int transfer(ServerLevel level, BlockPos donor, BlockPos receiver, int units, float vx, float vz) {
        if (!level.getServer().isSameThread() || units <= 0 || donor.equals(receiver)
                || !WaterMutationSafety.isReady(level, donor, WaterMutationSafety.CALLBACK_RADIUS)
                || !WaterMutationSafety.isReady(level, receiver, WaterMutationSafety.CALLBACK_RADIUS)) return 0;
        var source = CanonicalWater.getTracked(level, donor);
        var target = CanonicalWater.getTracked(level, receiver);
        if (!ordinary(source) || !ordinary(target) || source == null || source.volumeUnits() <= 0) return 0;
        int accepted = CanonicalWater.addTransferredVolume(level, donor, receiver, source,
                Math.min(units, source.volumeUnits()), vx, 0, vz);
        if (accepted <= 0) return 0;
        // Same server-thread credit/debit contract as finite voxel flow. A
        // failed destination receipt never changes the donor or its temperature.
        int remaining = source.volumeUnits() - accepted;
        CanonicalWater.set(level, donor, remaining == 0 ? WaterVolumeChunk.WaterCell.EMPTY
                : source.withFlowState(remaining, vx, 0, vz)
                        .withAddedFlags(WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED)
                        .withoutFlags(WaterVolumeChunk.FLAG_SLEEPING), true);
        return accepted;
    }

    static boolean ordinary(WaterVolumeChunk.WaterCell cell) {
        return cell == null || (!cell.imported() && !cell.hostedWater() && !cell.temporaryFlood()
                && !cell.displacementReservoir() && !cell.projectionPending());
    }
}
