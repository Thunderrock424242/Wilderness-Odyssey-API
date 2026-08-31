package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.platform.TextureUtil;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuProfiler;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuProfilerContext;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Attributes OpenGL simple-texture allocations to their Minecraft resources.
 *
 * @deprecated Replace with backend-neutral texture-allocation telemetry in the
 * Vulkan-targeted version.
 */
@Deprecated(forRemoval = true)
@Mixin(SimpleTexture.class)
public abstract class SimpleTextureGpuProfilerMixin {

    @Shadow
    @Final
    protected ResourceLocation location;

    @Redirect(
            method = "doLoad",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V")
    )
    private void wildernessOdysseyApi$labelTextureAllocation(int textureId, int mipLevel, int width, int height) {
        try (GpuProfilerContext.Scope ignored = GpuProfilerContext.withResource(this.location)) {
            TextureUtil.prepareImage(textureId, mipLevel, width, height);
        }
        GpuProfiler.labelTexture(textureId, this.location);
    }
}
