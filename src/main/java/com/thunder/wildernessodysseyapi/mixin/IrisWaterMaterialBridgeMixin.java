package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.ExternalShaderWaterMaterialBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds Wilderness water to Iris/Oculus's resolved shader-pack water material.
 *
 * <p>The target is optional and has no public extension event for resolved
 * {@code block.properties} mappings. A pseudo mixin keeps the integration
 * absent on normal clients while applying the alias at the narrow map-publication
 * boundary whenever a shader pack is loaded or reloaded.</p>
 */
@Pseudo
@Mixin(targets = {
        "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings",
        "net.coderbot.iris.block_rendering.BlockRenderingSettings"
}, remap = false)
public abstract class IrisWaterMaterialBridgeMixin {

    @Inject(
            method = "setBlockStateIds(Lit/unimi/dsi/fastutil/objects/Object2IntMap;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void wildernessOdyssey$aliasWildernessWaterMaterial(CallbackInfo callbackInfo) {
        ExternalShaderWaterMaterialBridge.applyToSettings(this);
    }
}
