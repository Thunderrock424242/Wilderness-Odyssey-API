package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.debugoverlay.client.DebugOverlayAnimator;
import com.thunder.wildernessodysseyapi.debugoverlay.client.DebugOverlayLineFilter;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuProfiler;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderDiagnostics;
import com.thunder.wildernessodysseyapi.weather.client.WeatherClientEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void wildernessOdysseyApi$applyOverlayAlpha(GuiGraphics guiGraphics, CallbackInfo ci) {
        float alpha = DebugOverlayAnimator.getAlpha();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void wildernessOdysseyApi$resetOverlayAlpha(GuiGraphics guiGraphics, CallbackInfo ci) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void wildernessOdysseyApi$filterGameInformation(CallbackInfoReturnable<List<String>> cir) {
        cir.setReturnValue(DebugOverlayLineFilter.filterGameLines(cir.getReturnValue()));
    }

    @Inject(method = "getSystemInformation", at = @At("RETURN"), cancellable = true)
    private void wildernessOdysseyApi$filterSystemInformation(CallbackInfoReturnable<List<String>> cir) {
        List<String> lines = new ArrayList<>(DebugOverlayLineFilter.filterSystemLines(cir.getReturnValue()));
        lines.addAll(GpuProfiler.debugLines());
        // Water diagnostics live beside the existing GPU counters so render
        // regressions can be inspected without opening a separate HUD.
        lines.addAll(WaterRenderDiagnostics.debugLines());
        lines.addAll(WeatherClientEvents.debugLines());
        cir.setReturnValue(lines);
    }
}
