package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.structureblock.bridge.StructureBlockHostileSpawnToggleBridge;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockHostileSpawnContext;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockDetectionContext;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockSettings;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockWorkBudget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries expanded structure-block request context through vanilla packet handling.
 *
 * <p>A mixin is required because vanilla applies the packet fields and invokes Save, Load, or Detect in one method;
 * there is no NeoForge event between packet validation and the potentially expensive block-entity operation.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetStructureBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread"
                    + "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
                    + "Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void wildernessodysseyapi$validateAndTrackRequest(ServerboundSetStructureBlockPacket packet,
            CallbackInfo ci) {
        // PacketUtils has already moved handling to the server thread at this injection point. Keep vanilla's
        // permission gate authoritative, then reject unsafe work before the block entity is mutated.
        if (!this.player.canUseGameMasterBlocks()) {
            return;
        }

        StructureBlockEntity.UpdateType updateType = packet.getUpdateType();
        if (updateType == StructureBlockEntity.UpdateType.SAVE_AREA
                || updateType == StructureBlockEntity.UpdateType.LOAD_AREA
                || updateType == StructureBlockEntity.UpdateType.SCAN_AREA) {
            long volume = StructureBlockWorkBudget.volume(packet.getSize());
            long volumeBudget = StructureBlockSettings.getMaxOperationVolume();
            if (volume > volumeBudget) {
                this.player.displayClientMessage(Component.literal("Wilderness Odyssey blocked this structure-block "
                        + "operation: " + volume + " blocks exceeds the configured limit of " + volumeBudget + ".")
                        .withStyle(ChatFormatting.RED), false);
                StructureBlockHostileSpawnContext.clear();
                StructureBlockDetectionContext.clear();
                ci.cancel();
                return;
            }
            if (volume > 0L && updateType != StructureBlockEntity.UpdateType.SCAN_AREA) {
                BlockPos start = packet.getPos().offset(packet.getOffset());
                BlockPos end = start.offset(packet.getSize().getX() - 1, packet.getSize().getY() - 1,
                        packet.getSize().getZ() - 1);
                long chunkCount = StructureBlockWorkBudget.chunkCount(start.getX(), end.getX(), start.getZ(),
                        end.getZ());
                int chunkBudget = StructureBlockSettings.getLoadedChunkScanBudget();
                if (chunkCount > chunkBudget) {
                    this.player.displayClientMessage(Component.literal(
                            "Wilderness Odyssey blocked this structure-block operation: " + chunkCount
                                    + " chunks exceeds the configured loaded-chunk limit of " + chunkBudget + ".")
                            .withStyle(ChatFormatting.RED), false);
                    StructureBlockHostileSpawnContext.clear();
                    StructureBlockDetectionContext.clear();
                    ci.cancel();
                    return;
                }
                if (!wildernessodysseyapi$areChunksLoaded(this.player.serverLevel(), start, end)) {
                    this.player.displayClientMessage(Component.literal(
                            "Wilderness Odyssey blocked this structure-block operation because part of its area is "
                                    + "not loaded. Move closer or load the area first; no chunks were forced.")
                            .withStyle(ChatFormatting.RED), false);
                    StructureBlockHostileSpawnContext.clear();
                    StructureBlockDetectionContext.clear();
                    ci.cancel();
                    return;
                }
            }
        }

        boolean disabled = packet instanceof StructureBlockHostileSpawnToggleBridge bridge
                && bridge.wildernessodysseyapi$isHostileSpawnsDisabled();
        StructureBlockHostileSpawnContext.setDisableHostileSpawns(disabled);
        StructureBlockDetectionContext.begin(this.player);
    }

    @Inject(method = "handleSetStructureBlock", at = @At("RETURN"))
    private void wildernessodysseyapi$clearHostileSpawnToggle(ServerboundSetStructureBlockPacket packet, CallbackInfo ci) {
        StructureBlockHostileSpawnContext.clear();
        StructureBlockDetectionContext.clear();
    }

    @Unique
    private static boolean wildernessodysseyapi$areChunksLoaded(ServerLevel level, BlockPos start, BlockPos end) {
        ServerChunkCache chunks = level.getChunkSource();
        int minChunkX = Math.min(start.getX(), end.getX()) >> 4;
        int maxChunkX = Math.max(start.getX(), end.getX()) >> 4;
        int minChunkZ = Math.min(start.getZ(), end.getZ()) >> 4;
        int maxChunkZ = Math.max(start.getZ(), end.getZ()) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (chunks.getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
