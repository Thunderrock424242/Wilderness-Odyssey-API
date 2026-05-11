package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.blockentity.AncientTimeCapsuleBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.TimeCapsuleBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class TemporalTransferManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TemporalRift");
    private static long lastCheckedDay = -1L;

    private TemporalTransferManager() {
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        if (currentDay == lastCheckedDay) {
            return;
        }
        lastCheckedDay = currentDay;
        processDeliveries(server, overworld, currentDay);
    }

    public static void scheduleCapsule(ServerLevel pastLevel, BlockPos sourcePos) {
        MinecraftServer server = pastLevel.getServer();
        if (!(pastLevel.getBlockEntity(sourcePos) instanceof TimeCapsuleBlockEntity capsule)) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        long deliveryDay = currentDay + TemporalRiftConfig.TIME_CAPSULE_DELAY_DAYS.get();
        BlockPos targetPos = new BlockPos(sourcePos.getX(), 64, sourcePos.getZ());

        TemporalTransfer transfer = new TemporalTransfer(
                sourcePos.immutable(),
                targetPos,
                capsule.getSealedOnDay(),
                deliveryDay,
                capsule.getOwnerName(),
                capsule.getStoredData()
        );

        TemporalTransferSavedData.get(server).addTransfer(transfer);
        LOGGER.info("[TemporalRift] Capsule scheduled by {} at {} for Overworld delivery on day {}", capsule.getOwnerName(), sourcePos, deliveryDay);
    }

    public static void forceDeliverAll(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        TemporalTransferSavedData data = TemporalTransferSavedData.get(server);
        for (TemporalTransfer transfer : new ArrayList<>(data.getPendingTransfers())) {
            if (!transfer.isFailed()) {
                deliverCapsule(server, overworld, transfer, data);
            }
        }
    }

    private static void processDeliveries(MinecraftServer server, ServerLevel overworld, long currentDay) {
        TemporalTransferSavedData data = TemporalTransferSavedData.get(server);
        List<TemporalTransfer> dueTransfers = new ArrayList<>();
        for (TemporalTransfer transfer : data.getPendingTransfers()) {
            if (!transfer.isFailed() && currentDay >= transfer.getDeliveryDay()) {
                dueTransfers.add(transfer);
            }
        }

        for (TemporalTransfer transfer : dueTransfers) {
            deliverCapsule(server, overworld, transfer, data);
        }
    }

    private static void deliverCapsule(MinecraftServer server, ServerLevel overworld, TemporalTransfer transfer, TemporalTransferSavedData data) {
        BlockPos target = transfer.getTargetPos();
        BlockPos safePos = SafeTeleportHelper.findSafePositionNearby(overworld, target.getX(), target.getY(), target.getZ(), 16);
        if (safePos == null) {
            LOGGER.warn("[TemporalRift] Capsule from {} (owner: {}) has no valid delivery position. Marked failed.", transfer.getSourcePos(), transfer.getOwnerName());
            transfer.markFailed();
            data.setDirty();
            return;
        }

        overworld.setBlock(safePos, TemporalRiftBlocks.ANCIENT_TIME_CAPSULE.get().defaultBlockState(), 3);
        if (overworld.getBlockEntity(safePos) instanceof AncientTimeCapsuleBlockEntity ancient) {
            ancient.setTransferData(transfer.getOwnerName(), transfer.getSealDay(), transfer.getData());
        }

        LOGGER.info("[TemporalRift] Time capsule delivered to Overworld at {} (owner: {}, source: {})", safePos, transfer.getOwnerName(), transfer.getSourcePos());
        data.removeTransfer(transfer);

        ServerLevel pastLevel = server.getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
        if (pastLevel != null && pastLevel.getBlockState(transfer.getSourcePos()).is(TemporalRiftBlocks.TIME_CAPSULE.get())) {
            pastLevel.removeBlock(transfer.getSourcePos(), false);
        }
    }
}
