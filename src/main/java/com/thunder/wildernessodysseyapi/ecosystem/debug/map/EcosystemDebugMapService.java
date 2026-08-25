package com.thunder.wildernessodysseyapi.ecosystem.debug.map;

import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeSavedData;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemCellKey;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemRegionSnapshot;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Builds an on-demand developer map from existing ecosystem owner snapshots.
 *
 * <p>The service samples a fixed 17 by 17 cell window. It reads the bounded
 * distant-wildlife ledger and pure LOD classification only; it does not inspect
 * live entities, access chunks, or retain a second map model.</p>
 */
public final class EcosystemDebugMapService {
    public static final int MAP_RADIUS_CELLS = 8;
    private static final long MINIMUM_REFRESH_INTERVAL_TICKS = 10L;
    private static final Map<MinecraftServer, Map<UUID, Long>> LAST_REQUESTS = new WeakHashMap<>();

    private EcosystemDebugMapService() {
    }

    /** Authorizes, rate-limits, snapshots, and opens the map for one player. */
    public static boolean open(ServerPlayer player) {
        if (!mayUse(player)) {
            player.displayClientMessage(Component.literal(
                    "The animal ecosystem map requires operator permission."
            ), false);
            return false;
        }
        if (!claimRefresh(player)) {
            return false;
        }

        PacketDistributor.sendToPlayer(player, snapshot(player));
        return true;
    }

    /** Releases per-server request throttles during shutdown. */
    public static void shutdown(MinecraftServer server) {
        LAST_REQUESTS.remove(server);
    }

    static EcosystemDebugMapPayload snapshot(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        EcosystemSimulationSettings zoneSettings = EcosystemSimulationSettings.fromConfig();
        EcosystemConfig.DistantWildlifeSettings distantSettings = EcosystemConfig.distantWildlifeSettings();
        EcosystemConfig.PopulationEcologySettings populationSettings = EcosystemConfig.populationEcologySettings();
        int cellSize = zoneSettings.cellSize();
        EcosystemCellKey center = EcosystemCellKey.fromBlock(player.blockPosition(), cellSize);
        int representativeY = player.blockPosition().getY();
        EcosystemSimulationManager manager = EcosystemSimulationManager.get();

        List<EcosystemDebugMapPayload.CellSnapshot> cells = new ArrayList<>(
                (MAP_RADIUS_CELLS * 2 + 1) * (MAP_RADIUS_CELLS * 2 + 1)
        );
        for (int offsetZ = -MAP_RADIUS_CELLS; offsetZ <= MAP_RADIUS_CELLS; offsetZ++) {
            for (int offsetX = -MAP_RADIUS_CELLS; offsetX <= MAP_RADIUS_CELLS; offsetX++) {
                EcosystemCellKey key = new EcosystemCellKey(center.x() + offsetX, center.z() + offsetZ);
                BlockPos samplePosition = key.center(cellSize, representativeY);
                var simulationLevel = manager.previewSimulationLevel(level, samplePosition);
                var region = manager.getRegionSnapshot(level, samplePosition);
                cells.add(cellSnapshot(key, simulationLevel, region.orElse(null)));
            }
        }

        List<EcosystemDebugMapPayload.GroupSnapshot> groups = DistantWildlifeSavedData.get(level).groups()
                .stream()
                .map(group -> groupSnapshot(group, gameTime))
                .filter(group -> insideMap(group, center, cellSize))
                .sorted(Comparator.comparingLong(EcosystemDebugMapPayload.GroupSnapshot::id))
                .toList();

        return new EcosystemDebugMapPayload(
                level.dimension().location(),
                EcosystemDebugMapPayload.DATA_VERSION,
                gameTime,
                player.blockPosition().getX(),
                player.blockPosition().getZ(),
                center.x(),
                center.z(),
                cellSize,
                MAP_RADIUS_CELLS,
                zoneSettings.enabled(),
                distantSettings.enabled(),
                populationSettings.enabled(),
                populationSettings.regionalCarryingCapacity(),
                cells,
                groups
        );
    }

    private static EcosystemDebugMapPayload.CellSnapshot cellSnapshot(
            EcosystemCellKey key,
            com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod simulationLevel,
            EcosystemRegionSnapshot region
    ) {
        if (region == null) {
            return new EcosystemDebugMapPayload.CellSnapshot(
                    key.x(), key.z(), simulationLevel,
                    0, 0, List.of(), false, key.x(), key.z(),
                    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0L
            );
        }
        List<EcosystemDebugMapPayload.SpeciesPopulation> species = region.speciesPopulations()
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> new EcosystemDebugMapPayload.SpeciesPopulation(entry.getKey(), entry.getValue()))
                .toList();
        boolean hasMigrationTarget = region.migrationTarget() != null
                && !region.migrationTarget().equals(key);
        return new EcosystemDebugMapPayload.CellSnapshot(
                key.x(),
                key.z(),
                simulationLevel,
                region.groupCount(),
                region.totalPopulation(),
                species,
                hasMigrationTarget,
                region.migrationTarget() == null ? key.x() : region.migrationTarget().x(),
                region.migrationTarget() == null ? key.z() : region.migrationTarget().z(),
                (float) region.foodAvailability(),
                (float) region.waterAvailability(),
                (float) region.foodPressure(),
                (float) region.disturbance(),
                (float) region.weatherImpact(),
                region.lastUpdatedTick()
        );
    }

    private static EcosystemDebugMapPayload.GroupSnapshot groupSnapshot(
            DistantWildlifeGroup group,
            long gameTime
    ) {
        Vec3 position = group.positionAt(gameTime);
        return new EcosystemDebugMapPayload.GroupSnapshot(
                group.id(),
                group.species(),
                group.populationEstimate(),
                (float) group.populationRemainder(),
                position.x,
                position.z,
                (float) group.directionX(),
                (float) group.directionZ(),
                group.form()
        );
    }

    private static boolean insideMap(
            EcosystemDebugMapPayload.GroupSnapshot group,
            EcosystemCellKey center,
            int cellSize
    ) {
        int groupCellX = Math.floorDiv((int) Math.floor(group.blockX()), cellSize);
        int groupCellZ = Math.floorDiv((int) Math.floor(group.blockZ()), cellSize);
        return Math.abs((long) groupCellX - center.x()) <= MAP_RADIUS_CELLS
                && Math.abs((long) groupCellZ - center.z()) <= MAP_RADIUS_CELLS;
    }

    private static boolean mayUse(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return player.createCommandSourceStack().hasPermission(2)
                || server.isSingleplayerOwner(player.getGameProfile());
    }

    private static boolean claimRefresh(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        long gameTime = player.serverLevel().getGameTime();
        Map<UUID, Long> serverRequests = LAST_REQUESTS.computeIfAbsent(server, ignored -> new java.util.HashMap<>());
        Long previous = serverRequests.get(player.getUUID());
        if (previous != null && gameTime >= previous
                && gameTime - previous < MINIMUM_REFRESH_INTERVAL_TICKS) {
            return false;
        }
        serverRequests.put(player.getUUID(), gameTime);
        return true;
    }
}
