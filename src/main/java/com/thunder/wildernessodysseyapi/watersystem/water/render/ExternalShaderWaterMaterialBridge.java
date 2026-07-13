package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Aliases Wilderness fluid states to a shader pack's vanilla-water material ID.
 *
 * <p>Iris routes translucent fluids through {@code gbuffers_water}, but packs
 * such as Complementary also use {@code block.properties} numeric IDs to decide
 * which translucent material receives water waves and optics. A mod cannot
 * safely edit a user's shader-pack archive, so this bridge augments Iris's
 * resolved state map after each pack reload. Explicit mappings authored by a
 * pack are preserved.</p>
 */
public final class ExternalShaderWaterMaterialBridge {

    private static boolean reflectionFailureLogged;

    private ExternalShaderWaterMaterialBridge() {
    }

    /**
     * Applies the alias to an Iris/Oculus world-rendering settings instance.
     *
     * <p>Reflection keeps Iris optional and avoids loading its implementation
     * classes on clients that use only the built-in Wilderness renderer.</p>
     *
     * @param settings Iris/Oculus {@code WorldRenderingSettings} instance
     * @return number of Wilderness block states newly assigned to the water material
     */
    public static int applyToSettings(Object settings) {
        if (settings == null) {
            return 0;
        }
        try {
            Method getter = settings.getClass().getMethod("getBlockStateIds");
            Object stateIds = getter.invoke(settings);
            if (!(stateIds instanceof Map<?, ?> rawMap)) {
                return 0;
            }
            return aliasMissingWaterStates(castStateMap(rawMap));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
                 | RuntimeException | LinkageError exception) {
            if (!reflectionFailureLogged) {
                ModConstants.LOGGER.warn(
                        "Unable to alias Wilderness water into the active shader-pack material map",
                        exception
                );
                reflectionFailureLogged = true;
            }
            return 0;
        }
    }

    /**
     * Assigns unmapped Wilderness states to the numeric material used by vanilla water.
     *
     * @param stateIds resolved shader-pack block-state IDs
     * @return number of newly aliased states
     */
    public static int aliasMissingWaterStates(Map<BlockState, Integer> stateIds) {
        if (stateIds == null) {
            return 0;
        }
        Integer waterMaterial = findVanillaWaterMaterial(stateIds);
        if (waterMaterial == null) {
            return 0;
        }

        Block wildernessWater = WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get();
        int aliases = 0;
        for (BlockState state : wildernessWater.getStateDefinition().getPossibleStates()) {
            if (!stateIds.containsKey(state)) {
                stateIds.put(state, waterMaterial);
                aliases++;
            }
        }
        return aliases;
    }

    private static Integer findVanillaWaterMaterial(Map<BlockState, Integer> stateIds) {
        for (BlockState state : Blocks.WATER.getStateDefinition().getPossibleStates()) {
            if (stateIds.containsKey(state)) {
                return stateIds.get(state);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockState, Integer> castStateMap(Map<?, ?> stateIds) {
        return (Map<BlockState, Integer>) stateIds;
    }
}
