package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public final class TemporalRiftTeleporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("TemporalRift");
    private static final String NBT_RETURN_X = "temporalrift_return_x";
    private static final String NBT_RETURN_Y = "temporalrift_return_y";
    private static final String NBT_RETURN_Z = "temporalrift_return_z";

    private TemporalRiftTeleporter() {
    }

    public static void teleportToPastDimension(ServerPlayer player, ServerLevel toLevel) {
        double sourceX = player.getX();
        double sourceY = player.getY();
        double sourceZ = player.getZ();

        CompoundTag persistentData = player.getPersistentData();
        persistentData.putDouble(NBT_RETURN_X, sourceX);
        persistentData.putDouble(NBT_RETURN_Y, sourceY);
        persistentData.putDouble(NBT_RETURN_Z, sourceZ);

        BlockPos safePos = SafeTeleportHelper.findSafePositionNearby(toLevel, (int) sourceX, 64, (int) sourceZ, 8);
        if (safePos == null) {
            safePos = new BlockPos((int) sourceX, 64, (int) sourceZ);
            LOGGER.warn("[TemporalRift] No safe destination found for {} in The Before. Using {}", player.getName().getString(), safePos);
        }

        player.teleportTo(toLevel, safePos.getX() + 0.5D, safePos.getY(), safePos.getZ() + 0.5D, Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
        if (TemporalRiftConfig.DEBUG_LOGGING.get()) {
            LOGGER.info("[TemporalRift] {} entered The Before at {}", player.getName().getString(), safePos);
        }
        player.sendSystemMessage(Component.literal("[Temporal Rift] The rift swallows you whole. You are now beyond your own time."));
    }

    public static void teleportToOverworld(ServerPlayer player, ServerLevel overworldLevel) {
        if (TemporalRiftConfig.RETURN_ONLY_ACTIVE_RIFT.get()) {
            MinecraftServer server = player.getServer();
            if (server == null || !TemporalRiftSavedData.get(server).isRiftOpen()) {
                player.sendSystemMessage(Component.literal("[Temporal Rift] There is no active rift. You cannot return until one opens again."));
                return;
            }
        }

        CompoundTag persistentData = player.getPersistentData();
        double returnX = player.getX();
        double returnY = player.getY();
        double returnZ = player.getZ();

        if (persistentData.contains(NBT_RETURN_X)) {
            returnX = persistentData.getDouble(NBT_RETURN_X);
            returnY = persistentData.getDouble(NBT_RETURN_Y);
            returnZ = persistentData.getDouble(NBT_RETURN_Z);
            persistentData.remove(NBT_RETURN_X);
            persistentData.remove(NBT_RETURN_Y);
            persistentData.remove(NBT_RETURN_Z);
        }

        BlockPos safePos = SafeTeleportHelper.findSafePositionNearby(overworldLevel, (int) returnX, (int) returnY, (int) returnZ, 8);
        if (safePos == null) {
            safePos = new BlockPos((int) returnX, (int) returnY, (int) returnZ);
        }

        player.teleportTo(overworldLevel, safePos.getX() + 0.5D, safePos.getY(), safePos.getZ() + 0.5D, Set.<RelativeMovement>of(), player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("[Temporal Rift] You have returned to the present."));
        if (TemporalRiftConfig.DEBUG_LOGGING.get()) {
            LOGGER.info("[TemporalRift] {} returned to the Overworld at {}", player.getName().getString(), safePos);
        }
    }
}
