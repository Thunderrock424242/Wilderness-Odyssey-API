package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Publishes authoritative SPH bodies to players near their current bounds.
 *
 * <p>Distance filtering is performed on the server so clients do not receive
 * expensive particle payloads for water they cannot render.</p>
 */
public final class SphSnapshotSynchronizer {

    private SphSnapshotSynchronizer() {
    }

    /**
     * Sends the latest state of every active body in one dimension.
     *
     * @param level server dimension whose bodies should be synchronized
     */
    public static void syncLevel(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }
        double trackingDistanceSquared = SPHConstants.NETWORK_TRACKING_DISTANCE
                * SPHConstants.NETWORK_TRACKING_DISTANCE;

        for (SPHSimulator simulator : SPHSimulationManager.get().getActive(level)) {
            if (simulator.isRemoteMirror() || simulator.particleCount() == 0) {
                continue;
            }
            // Settled meshes are static. A one-second refresh still catches
            // newly-arrived players without spending active-fluid bandwidth.
            if (simulator.isSettled() && level.getGameTime() % 20L != 0L) {
                continue;
            }

            SphSimulationSnapshotPayload payload = SphSimulationSnapshotPayload.fromSimulator(simulator);
            for (ServerPlayer player : level.players()) {
                double dx = player.getX() - simulator.getCenterX();
                double dy = player.getY() - simulator.getCenterY();
                double dz = player.getZ() - simulator.getCenterZ();
                if (dx * dx + dy * dy + dz * dz <= trackingDistanceSquared) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }
}
