package com.thunder.wildernessodysseyapi.environment.glacial.network;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonManager;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Publishes glacial presentation state only on changes or a sparse refresh interval. */
public final class GlacialSeasonSyncService {

    private static final long REFRESH_TICKS = 200L;
    private static final Map<ServerLevel, SentState> SENT = new ConcurrentHashMap<>();

    private GlacialSeasonSyncService() {
    }

    /** Sends the current season to players in this dimension when needed. */
    public static void tickLevel(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }
        BlockPos samplePosition = level.players().getFirst().blockPosition();
        GlacialSeasonSnapshot snapshot = GlacialSeasonManager.sample(level, samplePosition);
        long gameTime = level.getGameTime();
        SentState sent = SENT.get(level);
        if (sent != null && sent.signature() == snapshot.visualSignature()
                && gameTime - sent.sentAtTick() < REFRESH_TICKS) {
            return;
        }
        publish(level, snapshot);
    }

    /** Forces an immediate publication after an operator override changes. */
    public static void publishNow(ServerLevel level) {
        BlockPos samplePosition = level.players().isEmpty()
                ? new BlockPos(0, level.getSeaLevel(), 0)
                : level.players().getFirst().blockPosition();
        publish(level, GlacialSeasonManager.sample(level, samplePosition));
    }

    private static void publish(ServerLevel level, GlacialSeasonSnapshot snapshot) {
        GlacialSeasonSyncPayload payload = GlacialSeasonSyncPayload.from(
                level.dimension().location(), level.getGameTime(), snapshot);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        SENT.put(level, new SentState(level.getGameTime(), snapshot.visualSignature()));
    }

    /** Clears one dimension's last-send marker. */
    public static void clearLevel(ServerLevel level) {
        SENT.remove(level);
    }

    /** Clears all last-send markers. */
    public static void clearAll() {
        SENT.clear();
    }

    private record SentState(long sentAtTick, int signature) {
    }
}
