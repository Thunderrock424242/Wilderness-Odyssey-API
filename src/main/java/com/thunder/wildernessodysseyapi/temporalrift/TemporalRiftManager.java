package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class TemporalRiftManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TemporalRift");
    private static final long BEFORE_RIFT_SHRINK_TICKS = 240L;
    private static final long TRANSIENT_RETURN_RIFT_TICKS = 100L;
    private static final int BEFORE_SKY_RIFT_CLOUD_Y = 192;
    private static final int BEFORE_GROUND_RIFT_MIN_DISTANCE = 640;

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
                captureSinkholeEntries(server, overworld, data.getRiftPosition());
                if (data.getBeforeSkyRiftPosition() == null || data.getBeforeGroundRiftPosition() == null) {
                    placeBeforeRifts(server, data, data.getRiftPosition());
                }
                keepBeforeGroundRiftAwayFromArrival(server, data);
                updateBeforeRiftClosingVisuals(server, data, data.getRiftCloseGameTime() - gameTime);
            }
        } else if (currentDay >= data.getNextRiftDay()) {
            openRift(server, overworld, data, gameTime);
        }

        TemporalTransferManager.tick(server);
        TemporalEchoManager.tick(server);
        tickTransientReturnRift(overworld, data, gameTime);
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
        setRiftVisualMode(overworld, riftPos, RiftCoreBlockEntity.VisualMode.OVERWORLD_SINKHOLE);
        RiftEffectHelper.playOpeningEffects(overworld, riftPos);
        data.setRiftOpen(true);
        data.setRiftPosition(riftPos);
        data.setRiftCloseGameTime(gameTime + TemporalRiftConfig.RIFT_OPEN_DURATION_TICKS.get());
        placeBeforeRifts(server, data, riftPos);
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
        setRiftVisualMode(overworld, riftPos, RiftCoreBlockEntity.VisualMode.OVERWORLD_SINKHOLE);
        RiftEffectHelper.playOpeningEffects(overworld, riftPos);
        data.setRiftOpen(true);
        data.setRiftPosition(riftPos);
        data.setRiftCloseGameTime(currentGameTime + duration);
        placeBeforeRifts(server, data, riftPos);

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
        removeBeforeRifts(server, data);

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

    private static void placeBeforeRifts(MinecraftServer server, TemporalRiftSavedData data, BlockPos overworldRiftPos) {
        ServerLevel beforeLevel = server.getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
        if (beforeLevel == null) {
            LOGGER.warn("[TemporalRift] The Before is not loaded, so paired return rifts could not be placed.");
            return;
        }

        BlockPos skyRiftPos = findSkyRiftPosition(beforeLevel, overworldRiftPos);
        beforeLevel.setBlock(skyRiftPos, TemporalRiftBlocks.RIFT_CORE.get().defaultBlockState(), 3);
        setRiftVisualMode(beforeLevel, skyRiftPos, RiftCoreBlockEntity.VisualMode.BEFORE_SKY_TEAR);
        setBeforeRiftScale(beforeLevel, skyRiftPos, 1.0F);
        data.setBeforeSkyRiftPosition(skyRiftPos);

        int mountainSearchRadius = Math.max(1024, TemporalRiftConfig.RIFT_SPAWN_RADIUS.get() * 8);
        BlockPos groundRiftPos = RiftSpawnHelper.findHighestMountainRiftPosition(
                beforeLevel,
                skyRiftPos,
                mountainSearchRadius,
                BEFORE_GROUND_RIFT_MIN_DISTANCE
        );
        beforeLevel.setBlock(groundRiftPos, TemporalRiftBlocks.RIFT_CORE.get().defaultBlockState(), 3);
        setRiftVisualMode(beforeLevel, groundRiftPos, RiftCoreBlockEntity.VisualMode.BEFORE_GROUND_RETURN);
        setBeforeRiftScale(beforeLevel, groundRiftPos, 1.0F);
        data.setBeforeGroundRiftPosition(groundRiftPos);

        LOGGER.info("[TemporalRift] The Before return rifts opened at sky={} and ground={}.", skyRiftPos, groundRiftPos);
    }

    private static void removeBeforeRifts(MinecraftServer server, TemporalRiftSavedData data) {
        ServerLevel beforeLevel = server.getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
        if (beforeLevel != null) {
            removeBeforeRiftBlock(beforeLevel, data.getBeforeSkyRiftPosition());
            removeBeforeRiftBlock(beforeLevel, data.getBeforeGroundRiftPosition());
        }

        data.setBeforeSkyRiftPosition(null);
        data.setBeforeGroundRiftPosition(null);
    }

    private static void removeBeforeRiftBlock(ServerLevel beforeLevel, BlockPos pos) {
        if (pos != null && beforeLevel.getBlockState(pos).is(TemporalRiftBlocks.RIFT_CORE.get())) {
            beforeLevel.removeBlock(pos, false);
            RiftEffectHelper.playClosingEffects(beforeLevel, pos);
        }
    }

    private static void keepBeforeGroundRiftAwayFromArrival(MinecraftServer server, TemporalRiftSavedData data) {
        BlockPos skyRiftPos = data.getBeforeSkyRiftPosition();
        BlockPos groundRiftPos = data.getBeforeGroundRiftPosition();
        if (skyRiftPos == null || groundRiftPos == null) {
            return;
        }

        long dx = groundRiftPos.getX() - skyRiftPos.getX();
        long dz = groundRiftPos.getZ() - skyRiftPos.getZ();
        if (dx * dx + dz * dz >= (long) BEFORE_GROUND_RIFT_MIN_DISTANCE * BEFORE_GROUND_RIFT_MIN_DISTANCE) {
            return;
        }

        ServerLevel beforeLevel = server.getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
        if (beforeLevel == null) {
            return;
        }

        removeBeforeRiftBlock(beforeLevel, groundRiftPos);
        int mountainSearchRadius = Math.max(1024, TemporalRiftConfig.RIFT_SPAWN_RADIUS.get() * 8);
        BlockPos relocatedGroundRift = RiftSpawnHelper.findHighestMountainRiftPosition(
                beforeLevel,
                skyRiftPos,
                mountainSearchRadius,
                BEFORE_GROUND_RIFT_MIN_DISTANCE
        );
        beforeLevel.setBlock(relocatedGroundRift, TemporalRiftBlocks.RIFT_CORE.get().defaultBlockState(), 3);
        setRiftVisualMode(beforeLevel, relocatedGroundRift, RiftCoreBlockEntity.VisualMode.BEFORE_GROUND_RETURN);
        setBeforeRiftScale(beforeLevel, relocatedGroundRift, 1.0F);
        data.setBeforeGroundRiftPosition(relocatedGroundRift);
        LOGGER.info("[TemporalRift] Relocated The Before ground return rift from {} to {} to keep the arrival area clear.",
                groundRiftPos, relocatedGroundRift);
    }

    private static BlockPos findSkyRiftPosition(ServerLevel beforeLevel, BlockPos overworldRiftPos) {
        int skyY = Math.max(
                beforeLevel.getMinBuildHeight() + 16,
                Math.min(BEFORE_SKY_RIFT_CLOUD_Y, beforeLevel.getMaxBuildHeight() - 16)
        );
        return new BlockPos(overworldRiftPos.getX(), skyY, overworldRiftPos.getZ());
    }

    private static void updateBeforeRiftClosingVisuals(MinecraftServer server, TemporalRiftSavedData data, long remainingTicks) {
        ServerLevel beforeLevel = server.getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
        if (beforeLevel == null) {
            return;
        }

        float scale = remainingTicks >= BEFORE_RIFT_SHRINK_TICKS
                ? 1.0F
                : Math.max(0.08F, remainingTicks / (float) BEFORE_RIFT_SHRINK_TICKS);
        setBeforeRiftScale(beforeLevel, data.getBeforeSkyRiftPosition(), scale);
        setBeforeRiftScale(beforeLevel, data.getBeforeGroundRiftPosition(), scale);
    }

    private static void setBeforeRiftScale(ServerLevel beforeLevel, BlockPos pos, float scale) {
        if (pos != null && beforeLevel.getBlockEntity(pos) instanceof RiftCoreBlockEntity rift) {
            rift.setRenderScale(scale);
        }
    }

    private static void setRiftVisualMode(ServerLevel level, BlockPos pos, RiftCoreBlockEntity.VisualMode visualMode) {
        if (level.getBlockEntity(pos) instanceof RiftCoreBlockEntity rift) {
            rift.setVisualMode(visualMode);
        }
    }

    public static void returnPlayerThroughTransientRift(ServerPlayer player, ServerLevel overworld) {
        BlockPos exitRiftPos = openTransientReturnRift(overworld);
        TemporalRiftTeleporter.teleportToOverworld(player, overworld, exitRiftPos);
    }

    private static BlockPos openTransientReturnRift(ServerLevel overworld) {
        MinecraftServer server = overworld.getServer();
        TemporalRiftSavedData data = TemporalRiftSavedData.get(server);
        closeTransientReturnRift(overworld, data);

        BlockPos randomSurface = RiftSpawnHelper.findRiftSpawnPosition(overworld, TemporalRiftConfig.RIFT_SPAWN_RADIUS.get());
        BlockPos riftPos = RiftTerrainHelper.createReturnScar(overworld, randomSurface);
        overworld.setBlock(riftPos, TemporalRiftBlocks.RIFT_CORE.get().defaultBlockState(), 3);
        setRiftVisualMode(overworld, riftPos, RiftCoreBlockEntity.VisualMode.TRANSIENT_RETURN);
        RiftEffectHelper.playOpeningEffects(overworld, riftPos);
        data.setTransientReturnRiftPosition(riftPos);
        data.setTransientReturnRiftCloseGameTime(overworld.getGameTime() + TRANSIENT_RETURN_RIFT_TICKS);
        LOGGER.info("[TemporalRift] Transient return rift opened at {} and will seal in {} ticks.", riftPos, TRANSIENT_RETURN_RIFT_TICKS);
        return riftPos;
    }

    private static void tickTransientReturnRift(ServerLevel overworld, TemporalRiftSavedData data, long gameTime) {
        BlockPos transientRift = data.getTransientReturnRiftPosition();
        if (transientRift == null || gameTime < data.getTransientReturnRiftCloseGameTime()) {
            return;
        }

        closeTransientReturnRift(overworld, data);
    }

    private static void closeTransientReturnRift(ServerLevel overworld, TemporalRiftSavedData data) {
        BlockPos transientRift = data.getTransientReturnRiftPosition();
        if (transientRift != null && overworld.getBlockState(transientRift).is(TemporalRiftBlocks.RIFT_CORE.get())) {
            overworld.removeBlock(transientRift, false);
            RiftEffectHelper.playClosingEffects(overworld, transientRift);
        }
        data.setTransientReturnRiftPosition(null);
        data.setTransientReturnRiftCloseGameTime(0L);
    }

    private static void captureSinkholeEntries(MinecraftServer server, ServerLevel overworld, BlockPos riftPos) {
        ServerLevel beforeLevel = server.getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
        if (beforeLevel == null) {
            return;
        }

        Vec3 center = Vec3.atCenterOf(riftPos);
        double captureRadius = Math.max(7.0D, TemporalRiftConfig.RIFT_SINKHOLE_RADIUS.get() * 0.26D);
        double captureY = riftPos.getY() + 2.35D;
        AABB captureBox = new AABB(
                center.x - captureRadius,
                riftPos.getY() - 3.0D,
                center.z - captureRadius,
                center.x + captureRadius,
                captureY,
                center.z + captureRadius
        );
        List<ServerPlayer> playersToCapture = new ArrayList<>(overworld.getEntitiesOfClass(
                ServerPlayer.class,
                captureBox,
                player -> !player.isSpectator() && isInsideSinkholeCapture(player, center, captureRadius, captureY)
        ));
        List<Entity> entitiesToCapture = new ArrayList<>(overworld.getEntitiesOfClass(
                Entity.class,
                captureBox,
                entity -> !(entity instanceof ServerPlayer)
                        && entity.isAlive()
                        && isInsideSinkholeCapture(entity, center, captureRadius, captureY)
        ));

        if (!playersToCapture.isEmpty() || !entitiesToCapture.isEmpty()) {
            RiftEffectHelper.playTransitEarthquake(overworld, riftPos);
        }
        for (ServerPlayer player : playersToCapture) {
            TemporalRiftTeleporter.teleportToPastDimension(player, beforeLevel);
        }
        for (Entity entity : entitiesToCapture) {
            TemporalRiftTeleporter.teleportEntityToPastDimension(entity, beforeLevel);
        }
    }

    private static boolean isInsideSinkholeCapture(Entity entity, Vec3 center, double captureRadius, double captureY) {
        double dx = entity.getX() - center.x;
        double dz = entity.getZ() - center.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        return horizontalDistance <= captureRadius && entity.getY() <= captureY;
    }
}
