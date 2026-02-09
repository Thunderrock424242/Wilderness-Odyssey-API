package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.thunder.wildernessodysseyapi.config.DebugOverlayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.util.Mth;

public final class DebugOverlayAnimator {
    private static float progress = 0f;
    private static boolean targetVisible = false;
    private static boolean suppressUpdate = false;
    private static boolean lastRenderDebug = false;

    private DebugOverlayAnimator() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }

        DebugScreenOverlay debugOverlay = minecraft.getDebugOverlay();
        if (debugOverlay == null) {
            return;
        }

        boolean renderDebug = debugOverlay.showDebugScreen();
        if (!DebugOverlayConfig.ENABLE_ANIMATION.get()) {
            progress = renderDebug ? 1f : 0f;
            targetVisible = renderDebug;
            lastRenderDebug = renderDebug;
            return;
        }

        if (!suppressUpdate && renderDebug != lastRenderDebug) {
            targetVisible = renderDebug;
        }
        suppressUpdate = false;
        lastRenderDebug = renderDebug;

        int ticks = Math.max(1, DebugOverlayConfig.ANIMATION_TICKS.get());
        float step = 1f / ticks;
        progress = targetVisible
                ? Math.min(1f, progress + step)
                : Math.max(0f, progress - step);

        if (!targetVisible && progress > 0f && !renderDebug) {
            setRenderDebug(debugOverlay, true);
        } else if (!targetVisible && progress <= 0f && renderDebug) {
            setRenderDebug(debugOverlay, false);
        } else if (targetVisible && !renderDebug) {
            setRenderDebug(debugOverlay, true);
        }
    }

    public static float getAlpha() {
        if (!DebugOverlayConfig.ENABLE_ANIMATION.get()) {
            return 1f;
        }
        return Mth.clamp(progress, 0f, 1f);
    }

    private static void setRenderDebug(DebugScreenOverlay debugOverlay, boolean value) {
        if (debugOverlay.showDebugScreen() != value) {
            suppressUpdate = true;
            debugOverlay.toggleOverlay();
            lastRenderDebug = value;
        }
    }
}
