package com.thunder.wildernessodysseyapi.watersystem.water.render;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;

/** Visual fluid comparison for chunk meshing, independent of gameplay fluid identity. */
public final class WaterFluidRenderContinuity {

    private WaterFluidRenderContinuity() {
    }

    /** Preserves the renderer's comparison while allowing two tagged water fluids to share a height. */
    public static boolean sameRenderedVolume(Fluid rendered, Fluid sampled, boolean nativeMatches) {
        return nativeMatches
                || (rendered.defaultFluidState().is(FluidTags.WATER)
                && sampled.defaultFluidState().is(FluidTags.WATER));
    }
}
