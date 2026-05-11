package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TemporalRiftManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TemporalRift");

    private TemporalRiftManager() {
    }

    public static void tick(MinecraftServer server) {
        if (!TemporalRiftConfig.ENABLE_RIFT_SYSTEM.get()) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        TemporalRiftSavedData data = TemporalRiftSavedData.get(server);
        long gameTime = overworld.getGameTime();
        long currentDay = gameTime / 24000L;

        if (data.getNextRiftDay() == 0L) {
            long firstRiftDay = currentDay + TemporalRiftConfig.RIFT_INTERVAL_DAYS.get();
            data.setNextRiftDay(firstRiftDay);
            LOGGER.info("[TemporalRift] System initialized. First rift scheduled for day {}.", firstRiftDay);
        } else if (data.isRiftOpen()) {
            if (gameTime >= data.getRiftCloseGameTime()) {
                closeRift(server, overworld, data);
            } else if (data.getRiftPosition() != null) {
                RiftEffectHelper.tickOpenRift(overworld, data.getRiftPosition());
            }
        } else if (currentDay >= data.getNextRiftDay()) {
            openRift(server, overworld, data, gameTime);
        }

        TemporalTransferManager.tick(server);
    }

    public static void forceOpenRift(MinecraftServer server, BlockPos pos) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        TemporalRiftSavedData data = TemporalRiftSavedData.get(server);
        if (data.isRiftOpen()) {
            closeRift(server, overworld, data);
        }

        long gameTime = overworld.getGameTime();
        BlockPos riftPos = prepareRiftSite(overworld, pos);
        overworld.setBlock(riftPos, TemporalRiftBlocks.RIFT_CORE.get().defaultBlockState(), 3);
        RiftEffectHelper.playOpeningEffects(overworld, riftPos);
        data.setRiftOpen(true);
        data.setRiftPosition(riftPos);
        data.setRiftCloseGameTime(gameTime + TemporalRiftConfig.RIFT_OPEN_DURATION_TICKS.get());
        LOGGER.info("[TemporalRift] Rift force-opened at {}.", riftPos);
    }

    public static void forceCloseRift(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        TemporalRiftSavedData data = TemporalRiftSavedData.get(server);
        if (data.isRiftOpen()) {
            closeRift(server, overworld, data);
        }
    }

    private static void openRift(MinecraftServer server, ServerLevel overworld, TemporalRiftSavedData data, long currentGameTime) {
        int radius = TemporalRiftConfig.RIFT_SPAWN_RADIUS.get();
        int duration = TemporalRiftConfig.RIFT_OPEN_DURATION_TICKS.get();
        BlockPos surfacePos = RiftSpawnHelper.findRiftSpawnPosition(overworld, radius);
        BlockPos riftPos = prepareRiftSite(overworld, surfacePos);

        overworld.setBlock(riftPos, TemporalRiftBlocks.RIFT_CORE.get().defaultBlockState(), 3);
        RiftEffectHelper.playOpeningEffects(overworld, riftPos);
        data.setRiftOpen(true);
        data.setRiftPosition(riftPos);
        data.setRiftCloseGameTime(currentGameTime + duration);

        LOGGER.info("[TemporalRift] Rift opened at {}. Closes at game tick {}.", riftPos, currentGameTime + duration);
        if (TemporalRiftConfig.CHAT_BROADCASTS.get()) {
            broadcast(server, Component.literal("[Temporal Rift] A tear in time has opened near "
                    + riftPos.getX() + ", " + riftPos.getY() + ", " + riftPos.getZ()
                    + ". Seek it before it closes."));
        }
    }

    private static void closeRift(MinecraftServer server, ServerLevel overworld, TemporalRiftSavedData data) {
        BlockPos riftPos = data.getRiftPosition();
        if (riftPos != null && overworld.getBlockState(riftPos).is(TemporalRiftBlocks.RIFT_CORE.get())) {
            overworld.removeBlock(riftPos, false);
            RiftEffectHelper.playClosingEffects(overworld, riftPos);
            LOGGER.info("[TemporalRift] Rift core removed at {}.", riftPos);
        }

        data.setRiftOpen(false);
        data.setRiftPosition(null);

        long currentDay = overworld.getGameTime() / 24000L;
        long nextRiftDay = currentDay + TemporalRiftConfig.RIFT_INTERVAL_DAYS.get();
        data.setNextRiftDay(nextRiftDay);
        LOGGER.info("[TemporalRift] Rift closed. Next rift on day {}.", nextRiftDay);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY)) {
                player.sendSystemMessage(Component.literal("[Temporal Rift] The temporal rift has collapsed. You are stranded beyond your own time."));
                LOGGER.warn("[TemporalRift] {} is now stranded in The Before.", player.getName().getString());
            }
        }

        if (TemporalRiftConfig.CHAT_BROADCASTS.get()) {
            broadcast(server, Component.literal("[Temporal Rift] The temporal rift has sealed. Those left behind are lost to time."));
        }
    }

    private static void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    private static BlockPos prepareRiftSite(ServerLevel overworld, BlockPos surfacePos) {
        if (!TemporalRiftConfig.ENABLE_RIFT_SINKHOLE.get()) {
            return surfacePos;
        }

        return RiftTerrainHelper.createSinkhole(
                overworld,
                surfacePos,
                TemporalRiftConfig.RIFT_SINKHOLE_RADIUS.get(),
                TemporalRiftConfig.RIFT_SINKHOLE_DEPTH.get()
        );
    }
}
