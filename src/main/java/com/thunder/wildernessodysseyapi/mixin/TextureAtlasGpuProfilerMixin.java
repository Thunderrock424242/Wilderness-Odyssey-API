package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.platform.TextureUtil;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuProfiler;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuProfilerContext;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attributes OpenGL texture-atlas allocations to their Minecraft resources.
 *
 * @deprecated Replace with backend-neutral texture-allocation telemetry in the
 * Vulkan-targeted version.
 */
@Deprecated(forRemoval = true)
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasGpuProfilerMixin extends AbstractTexture {

    @Shadow
    @Final
    private ResourceLocation location;

    @Redirect(
            method = "upload",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V")
    )
    private void wildernessOdysseyApi$labelAtlasAllocation(int textureId, int mipLevel, int width, int height) {
        try (GpuProfilerContext.Scope ignored = GpuProfilerContext.withResource(this.location)) {
            TextureUtil.prepareImage(textureId, mipLevel, width, height);
        }
        GpuProfiler.labelTexture(textureId, this.location);
    }

    @Inject(method = "upload", at = @At("TAIL"))
    private void wildernessOdysseyApi$recordAtlasContributors(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        GpuProfiler.recordAtlasContributors(this.getId(), this.location, preparations.regions());
    }
}
