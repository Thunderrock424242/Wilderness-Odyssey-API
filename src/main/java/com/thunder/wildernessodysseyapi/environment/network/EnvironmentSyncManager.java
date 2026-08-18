package com.thunder.wildernessodysseyapi.environment.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;
import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceService;
import com.thunder.wildernessodysseyapi.environment.event.WorldDisturbanceType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Staggers compact environment summaries and detects player-relevant hazard transitions. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class EnvironmentSyncManager {

    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final Map<UUID, PlayerState> PLAYERS = new HashMap<>();

    private EnvironmentSyncManager() {
    }

    /** Sends each player one server-authored summary per second on a staggered phase. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long gameTime = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                int phase = Math.floorMod(player.getUUID().hashCode(), SYNC_INTERVAL_TICKS);
                if (Math.floorMod(gameTime, SYNC_INTERVAL_TICKS) != phase) {
                    continue;
                }
                sync(player);
            }
        }
    }

    /** Releases the constant-size player transition baseline on logout. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYERS.remove(event.getEntity().getUUID());
    }

    /** Clears all transient synchronization state after dimensions have saved. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PLAYERS.clear();
    }

    private static void sync(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RegionalEnvironmentSnapshot snapshot = EnvironmentServices.query()
                .sample(level, player.blockPosition());
        PlayerState previous = PLAYERS.get(player.getUUID());
        boolean sameDimension = previous != null && previous.dimension().equals(level.dimension());

        if (snapshot.watershed().flooding() && (!sameDimension || !previous.flooding())) {
            WorldDisturbanceService.publish(
                    level,
                    player.blockPosition(),
                    WorldDisturbanceType.FLOOD,
                    64,
                    null,
                    false
            );
        }
        boolean severeDrought = snapshot.vegetation().droughtLevel() >= 0.75;
        if (severeDrought && (!sameDimension || !previous.severeDrought())) {
            WorldDisturbanceService.publish(
                    level,
                    player.blockPosition(),
                    WorldDisturbanceType.DROUGHT,
                    96,
                    null,
                    false
            );
        }
        boolean irradiated = snapshot.meteorSite().radiation() >= 0.20;
        if (irradiated && (!sameDimension || !previous.irradiated())) {
            WorldDisturbanceService.publish(
                    level,
                    player.blockPosition(),
                    WorldDisturbanceType.RADIATION,
                    Math.max(32, snapshot.meteorSite().craterRadius() * 3),
                    null,
                    false
            );
        }

        PLAYERS.put(player.getUUID(), new PlayerState(
                level.dimension(), snapshot.watershed().flooding(), severeDrought, irradiated));
        PacketDistributor.sendToPlayer(player, EnvironmentSyncPayload.from(player, snapshot));
    }

    private record PlayerState(
            ResourceKey<Level> dimension,
            boolean flooding,
            boolean severeDrought,
            boolean irradiated
    ) {
    }
}
