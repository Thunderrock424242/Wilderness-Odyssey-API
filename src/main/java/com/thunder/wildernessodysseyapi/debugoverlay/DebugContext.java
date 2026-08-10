package com.thunder.wildernessodysseyapi.debugoverlay;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Objects;

/**
 * Per-render context supplied to the active page only.
 *
 * <p>The raw lists are the exact collections produced by NeoForge's patched
 * {@code DebugScreenOverlay} for this frame. They are consumed synchronously
 * and are never retained after the event returns.</p>
 */
public record DebugContext(
        Minecraft minecraft,
        List<String> vanillaLeft,
        List<String> vanillaRight,
        long capturedNanos
) {
    public DebugContext {
        minecraft = Objects.requireNonNull(minecraft, "Minecraft is required");
        vanillaLeft = Objects.requireNonNull(vanillaLeft, "Vanilla left lines are required");
        vanillaRight = Objects.requireNonNull(vanillaRight, "Vanilla right lines are required");
    }
}
