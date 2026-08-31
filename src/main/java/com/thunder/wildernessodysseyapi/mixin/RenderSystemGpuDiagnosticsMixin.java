package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Samples final indexed draw submissions for the opt-in GPU profiler.
 *
 * <p>RenderSystem is the narrow common point where Minecraft and most mods submit
 * indexed OpenGL work. NeoForge does not expose an event around each draw, so a
 * mixin is required to pair asynchronous timestamp queries with the Java caller.</p>
 *
 * @deprecated The Vulkan-targeted profiler must time work through supported
 * backend instrumentation rather than the OpenGL draw-elements entry point.
 */
@Deprecated(forRemoval = true)
@Mixin(RenderSystem.class)
public abstract class RenderSystemGpuDiagnosticsMixin {

    // Starts a timestamp pair only when the session's bounded sampler selects this draw.
    @Inject(method = "drawElements", at = @At("HEAD"))
    private static void wildernessOdysseyApi$beginTimedDraw(int mode, int count, int type, CallbackInfo ci) {
        GpuDiagnostics.beginDraw(mode, count, type);
    }

    // Finishes the selected query without blocking for a result on the render thread.
    @Inject(method = "drawElements", at = @At("RETURN"))
    private static void wildernessOdysseyApi$endTimedDraw(int mode, int count, int type, CallbackInfo ci) {
        GpuDiagnostics.endDraw();
    }
}
