package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public final class TemporalRiftTeleporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("TemporalRift");
    private static final String NBT_RETURN_X = "temporalrift_return_x";
    private static final String NBT_RETURN_Y = "temporalrift_return_y";
    private static final String NBT_RETURN_Z = "temporalrift_return_z";
    private static final int ARRIVAL_FADE_IN_TICKS = 10;
    private static final int ARRIVAL_STAY_TICKS = 50;
    private static final int ARRIVAL_FADE_OUT_TICKS = 20;

    private TemporalRiftTeleporter() {
    }

    public static void teleportToPastDimension(ServerPlayer player, ServerLevel toLevel) {
        double sourceX = player.getX();
        double sourceY = player.getY();
        double sourceZ = player.getZ();
        MinecraftServer server = player.getServer();
        TemporalRiftSavedData riftData = server == null ? null : TemporalRiftSavedData.get(server);
        BlockPos skyRiftPos = riftData == null ? null : riftData.getBeforeSkyRiftPosition();
        if (skyRiftPos == null) {
            int fallbackY = Math.min(toLevel.getMaxBuildHeight() - 16, (int) Math.floor(sourceY) + 64);
            skyRiftPos = new BlockPos((int) Math.floor(sourceX), fallbackY, (int) Math.floor(sourceZ));
        }
        BlockPos respawnPos = SafeTeleportHelper.findSafePositionNearby(
                toLevel,
                skyRiftPos.getX(),
                Math.max(toLevel.getMinBuildHeight() + 2, skyRiftPos.getY() - 64),
                skyRiftPos.getZ(),
                12
        );
        if (respawnPos == null) {
            respawnPos = BlockPos.containing(sourceX, sourceY, sourceZ);
        }

        CompoundTag persistentData = player.getPersistentData();
        persistentData.putDouble(NBT_RETURN_X, sourceX);
        persistentData.putDouble(NBT_RETURN_Y, sourceY);
        persistentData.putDouble(NBT_RETURN_Z, sourceZ);

        player.teleportTo(
                toLevel,
                respawnPos.getX() + 0.5D,
                respawnPos.getY(),
                respawnPos.getZ() + 0.5D,
                Set.<RelativeMovement>of(),
                player.getYRot(),
                player.getXRot()
        );
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 260, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, false, true));
        player.setRespawnPosition(toLevel.dimension(), respawnPos, player.getYRot(), true, false);
        playArrivalWakeUp(player);
        if (TemporalRiftConfig.DEBUG_LOGGING.get()) {
            LOGGER.info("[TemporalRift] {} woke in The Before at {} beneath sky tear {}", player.getName().getString(), respawnPos, skyRiftPos);
        }
        player.sendSystemMessage(Component.literal("[Temporal Rift] You wake in The Before. Above you, the sky is still torn open."));
    }

    public static void teleportToOverworld(ServerPlayer player, ServerLevel overworldLevel) {
        teleportToOverworld(player, overworldLevel, null);
    }

    public static void teleportToOverworld(ServerPlayer player, ServerLevel overworldLevel, BlockPos forcedDestination) {
        if (TemporalRiftConfig.RETURN_ONLY_ACTIVE_RIFT.get()) {
            MinecraftServer server = player.getServer();
            if (server == null || !TemporalRiftSavedData.get(server).isRiftOpen()) {
                player.sendSystemMessage(Component.literal("[Temporal Rift] There is no active rift. You cannot return until one opens again."));
                return;
            }
        }

        CompoundTag persistentData = player.getPersistentData();
        double returnX = forcedDestination == null ? player.getX() : forcedDestination.getX();
        double returnY = forcedDestination == null ? player.getY() : forcedDestination.getY();
        double returnZ = forcedDestination == null ? player.getZ() : forcedDestination.getZ();

        if (forcedDestination == null && persistentData.contains(NBT_RETURN_X)) {
            returnX = persistentData.getDouble(NBT_RETURN_X);
            returnY = persistentData.getDouble(NBT_RETURN_Y);
            returnZ = persistentData.getDouble(NBT_RETURN_Z);
        }

        if (persistentData.contains(NBT_RETURN_X)) {
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

    private static void playArrivalWakeUp(ServerPlayer player) {
        ServerGamePacketListenerImpl connection = player.connection;
        connection.send(new ClientboundSetTitlesAnimationPacket(ARRIVAL_FADE_IN_TICKS, ARRIVAL_STAY_TICKS, ARRIVAL_FADE_OUT_TICKS));
        connection.send(new ClientboundSetTitleTextPacket(Component.literal("You wake beneath a broken sky")));
    }
}
