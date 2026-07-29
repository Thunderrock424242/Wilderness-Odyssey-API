package com.thunder.wildernessodysseyapi.watersystem.water.compat.create;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

/**
 * Runtime gate used by Create-specific water predicates.
 *
 * <p>Fluid identity is checked by registry name so Create may query the helper
 * during startup without forcing deferred Wilderness fluid holders early.</p>
 */
public final class CreateWaterCompatibilityState {

    private static final String SOURCE_PATH = "wilderness_water";
    private static final String FLOWING_PATH = "flowing_wilderness_water";
    private static volatile boolean active;

    private CreateWaterCompatibilityState() {
    }

    /** Activates focused Create behavior after dependency discovery succeeds. */
    public static void activate() {
        active = true;
    }

    /** Returns whether Create should treat this exact namespaced fluid as water. */
    public static boolean isCreateWater(Fluid fluid) {
        if (!active || !WaterSimulationConfig.createWaterCompatEnabled() || fluid == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return id != null
                && ModConstants.MOD_ID.equals(id.getNamespace())
                && (SOURCE_PATH.equals(id.getPath()) || FLOWING_PATH.equals(id.getPath()));
    }
}
