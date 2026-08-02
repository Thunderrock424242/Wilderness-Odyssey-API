package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
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
    private static volatile MaterialBridgeStatus status = MaterialBridgeStatus.NOT_OBSERVED;

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
            Object stateIds = resolveStateMap(settings);
            if (!(stateIds instanceof Map<?, ?> rawMap)) {
                status = MaterialBridgeStatus.INCOMPATIBLE_API;
                logReflectionFailure(new IllegalStateException(
                        "No block-state material map was exposed by " + settings.getClass().getName()));
                return 0;
            }
            Map<BlockState, Integer> typedMap = castStateMap(rawMap);
            int aliases = aliasMissingWaterStates(typedMap);
            status = wildernessStatesMapped(typedMap)
                    ? MaterialBridgeStatus.MAPPED
                    : MaterialBridgeStatus.VANILLA_WATER_UNMAPPED;
            return aliases;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
                 | RuntimeException | LinkageError exception) {
            status = MaterialBridgeStatus.INCOMPATIBLE_API;
            logReflectionFailure(exception);
            return 0;
        }
    }

    /** Returns the latest optional shader-pack alias result for diagnostics. */
    public static MaterialBridgeStatus status() {
        return status;
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

    // Iris and Oculus have moved this state between public accessors and a
    // private field across releases. Prefer the supported accessor, then use a
    // narrow read-only reflective fallback rather than silently losing water IDs.
    private static Object resolveStateMap(Object settings)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Method getter = settings.getClass().getMethod("getBlockStateIds");
            return getter.invoke(settings);
        } catch (NoSuchMethodException missingPublicGetter) {
            for (String fieldName : new String[] {"blockStateIds", "blockStateIdMap"}) {
                for (Class<?> type = settings.getClass(); type != null; type = type.getSuperclass()) {
                    try {
                        Field field = type.getDeclaredField(fieldName);
                        if (!field.canAccess(settings)) {
                            field.setAccessible(true);
                        }
                        return field.get(settings);
                    } catch (NoSuchFieldException ignored) {
                        // Forks may inherit the field from a shared settings base.
                    }
                }
            }
            throw missingPublicGetter;
        }
    }

    private static boolean wildernessStatesMapped(Map<BlockState, Integer> stateIds) {
        for (BlockState state : WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()
                .getStateDefinition().getPossibleStates()) {
            if (!stateIds.containsKey(state)) {
                return false;
            }
        }
        return true;
    }

    private static void logReflectionFailure(Throwable exception) {
        if (!reflectionFailureLogged) {
            ModConstants.LOGGER.warn(
                    "Unable to alias Wilderness water into the active shader-pack material map",
                    exception
            );
            reflectionFailureLogged = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockState, Integer> castStateMap(Map<?, ?> stateIds) {
        return (Map<BlockState, Integer>) stateIds;
    }

    /** Latest result of applying the optional Iris/Oculus material bridge. */
    public enum MaterialBridgeStatus {
        NOT_OBSERVED("not observed"),
        MAPPED("mapped as water"),
        VANILLA_WATER_UNMAPPED("vanilla water ID unavailable"),
        INCOMPATIBLE_API("incompatible shader API");

        private final String label;

        MaterialBridgeStatus(String label) {
            this.label = label;
        }

        /** Human-readable status for the F3 diagnostics panel. */
        public String label() {
            return label;
        }
    }
}
