package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.thunder.wildernessodysseyapi.config.DebugOverlayConfig;
import net.minecraft.client.Minecraft;
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

        boolean renderDebug = minecraft.options.renderDebug;
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
            setRenderDebug(minecraft, true);
        } else if (!targetVisible && progress <= 0f && renderDebug) {
            setRenderDebug(minecraft, false);
        } else if (targetVisible && !renderDebug) {
            setRenderDebug(minecraft, true);
        }
    }

    public static float getAlpha() {
        if (!DebugOverlayConfig.ENABLE_ANIMATION.get()) {
            return 1f;
        }
        return Mth.clamp(progress, 0f, 1f);
    }

    private static void setRenderDebug(Minecraft minecraft, boolean value) {
        if (minecraft.options.renderDebug != value) {
            suppressUpdate = true;
            minecraft.options.renderDebug = value;
            lastRenderDebug = value;
        }
    }
}
