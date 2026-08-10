package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the two ray hits already calculated by vanilla's debug overlay.
 *
 * <p>NeoForge's debug-text event exposes the generated line collections but
 * not these underlying values. Reading the fields avoids repeating both
 * 20-block ray casts in Wilderness data providers. All rendering and page
 * behavior remains in ordinary client code.</p>
 */
@Mixin(DebugScreenOverlay.class)
public interface DebugScreenOverlayAccessor {
    /** Returns vanilla's current non-fluid block ray hit. */
    @Accessor("block")
    HitResult wildernessOdysseyApi$getBlockTarget();

    /** Returns vanilla's current fluid-inclusive ray hit. */
    @Accessor("liquid")
    HitResult wildernessOdysseyApi$getFluidTarget();
}
