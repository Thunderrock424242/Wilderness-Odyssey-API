package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.structureblock.bridge.StructureBlockHostileSpawnToggleBridge;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockHostileSpawnContext;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockDetectionContext;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetStructureBlock", at = @At("HEAD"))
    private void wildernessodysseyapi$trackHostileSpawnToggle(ServerboundSetStructureBlockPacket packet, CallbackInfo ci) {
        boolean disabled = packet instanceof StructureBlockHostileSpawnToggleBridge bridge
                && bridge.wildernessodysseyapi$isHostileSpawnsDisabled();
        StructureBlockHostileSpawnContext.setDisableHostileSpawns(disabled);
        if (packet.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
            StructureBlockDetectionContext.begin(this.player);
        }
    }

    @Inject(method = "handleSetStructureBlock", at = @At("RETURN"))
    private void wildernessodysseyapi$clearHostileSpawnToggle(ServerboundSetStructureBlockPacket packet, CallbackInfo ci) {
        StructureBlockHostileSpawnContext.clear();
        StructureBlockDetectionContext.clear();
    }
}
