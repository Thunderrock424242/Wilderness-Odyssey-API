package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.bridge.StructureBlockHostileSpawnToggleBridge;
import com.thunder.wildernessodysseyapi.util.StructureBlockHostileSpawnContext;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Inject(method = "handleSetStructureBlock", at = @At("HEAD"))
    private void wildernessodysseyapi$trackHostileSpawnToggle(ServerboundSetStructureBlockPacket packet, CallbackInfo ci) {
        boolean disabled = packet instanceof StructureBlockHostileSpawnToggleBridge bridge
                && bridge.wildernessodysseyapi$isHostileSpawnsDisabled();
        StructureBlockHostileSpawnContext.setDisableHostileSpawns(disabled);
    }

    @Inject(method = "handleSetStructureBlock", at = @At("RETURN"))
    private void wildernessodysseyapi$clearHostileSpawnToggle(ServerboundSetStructureBlockPacket packet, CallbackInfo ci) {
        StructureBlockHostileSpawnContext.clear();
    }
}
