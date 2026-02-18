package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.worldgen.structure.bridge.StructureBlockCornerCacheBridge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void wildernessodysseyapi$syncCornerCacheOnLevel(Level level, CallbackInfo ci) {
        if ((Object) this instanceof StructureBlockCornerCacheBridge bridge) {
            bridge.wildernessodysseyapi$bridge$syncCornerCache();
        }
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void wildernessodysseyapi$cleanupCornerCacheOnRemoval(CallbackInfo ci) {
        if ((Object) this instanceof StructureBlockCornerCacheBridge bridge) {
            bridge.wildernessodysseyapi$bridge$removeCornerFromCache();
        }
    }

    @Inject(method = "clearRemoved", at = @At("TAIL"))
    private void wildernessodysseyapi$restoreCornerCacheOnClear(CallbackInfo ci) {
        if ((Object) this instanceof StructureBlockCornerCacheBridge bridge) {
            bridge.wildernessodysseyapi$bridge$syncCornerCache();
        }
    }
}
