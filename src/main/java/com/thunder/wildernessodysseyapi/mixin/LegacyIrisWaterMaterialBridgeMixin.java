package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.ExternalShaderWaterMaterialBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the water-material alias to legacy Iris and Oculus package layouts.
 *
 * <p>This target is separate from the modern bridge so the mixin plugin can
 * suppress only the absent optional class without hiding either supported
 * implementation when it is installed.</p>
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.block_rendering.BlockRenderingSettings", remap = false)
public abstract class LegacyIrisWaterMaterialBridgeMixin {

    @Inject(
            method = "setBlockStateIds(Lit/unimi/dsi/fastutil/objects/Object2IntMap;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void wildernessOdyssey$aliasWildernessWaterMaterial(CallbackInfo callbackInfo) {
        ExternalShaderWaterMaterialBridge.applyToSettings(this);
    }

    @Inject(
            method = "setBlockStateIds(Ljava/util/Map;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void wildernessOdyssey$aliasMapBackedWaterMaterial(CallbackInfo callbackInfo) {
        ExternalShaderWaterMaterialBridge.applyToSettings(this);
    }

    @Inject(
            method = {
                    "setBlockStateIdMap(Lit/unimi/dsi/fastutil/objects/Object2IntMap;)V",
                    "setBlockStateIdMap(Ljava/util/Map;)V"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void wildernessOdyssey$aliasRenamedWaterMaterialMap(CallbackInfo callbackInfo) {
        ExternalShaderWaterMaterialBridge.applyToSettings(this);
    }
}
