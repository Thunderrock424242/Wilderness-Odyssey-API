package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerGpuProfilerMixin {

    @Inject(method = "_texImage2D", at = @At("HEAD"))
    private static void wildernessOdysseyApi$trackTextureStorage(int target, int level, int internalFormat,
                                                                  int width, int height, int border, int format,
                                                                  int type, IntBuffer pixels, CallbackInfo ci) {
        GpuProfiler.onTextureStorage(target, level, internalFormat, width, height, format, type);
    }

    @Inject(method = "_glBufferData(ILjava/nio/ByteBuffer;I)V", at = @At("HEAD"))
    private static void wildernessOdysseyApi$trackBufferStorage(int target, ByteBuffer data, int usage, CallbackInfo ci) {
        GpuProfiler.onBufferStorage(target, data);
    }

    @Inject(method = "_glBufferData(IJI)V", at = @At("HEAD"))
    private static void wildernessOdysseyApi$trackBufferStorage(int target, long size, int usage, CallbackInfo ci) {
        GpuProfiler.onBufferStorage(target, size);
    }

    @Inject(method = "_glRenderbufferStorage", at = @At("HEAD"))
    private static void wildernessOdysseyApi$trackRenderbufferStorage(int target, int internalFormat,
                                                                       int width, int height, CallbackInfo ci) {
        GpuProfiler.onRenderbufferStorage(internalFormat, width, height);
    }

    @Inject(method = "_deleteTexture", at = @At("HEAD"))
    private static void wildernessOdysseyApi$trackTextureDelete(int texture, CallbackInfo ci) {
        GpuProfiler.onTextureDeleted(texture);
    }

    @Inject(method = "_deleteTextures", at = @At("HEAD"))
    private static void wildernessOdysseyApi$trackTextureDeletes(int[] textures, CallbackInfo ci) {
        GpuProfiler.onTexturesDeleted(textures);
    }

    @Inject(method = "_glDeleteBuffers", at = @At("HEAD"))
    private static void wildernessOdysseyApi$trackBufferDelete(int buffer, CallbackInfo ci) {
        GpuProfiler.onBufferDeleted(buffer);
    }

    @Inject(method = "_glDeleteRenderbuffers", at = @At("HEAD"))
    private static void wildernessOdysseyApi$trackRenderbufferDelete(int renderbuffer, CallbackInfo ci) {
        GpuProfiler.onRenderbufferDeleted(renderbuffer);
    }
}
