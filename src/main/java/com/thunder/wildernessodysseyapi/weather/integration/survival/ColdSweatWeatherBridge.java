package com.thunder.wildernessodysseyapi.weather.integration.survival;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Guarded Cold Sweat adapter for localized outdoor air temperature.
 *
 * <p>The adapter subscribes by reflected event class so Cold Sweat remains an
 * optional dependency. It wraps only Cold Sweat's biome temperature modifier,
 * preserving that mod's structures, dimensions, insulation, and body-temperature
 * ownership. Any incompatible API change disables this bridge without stopping
 * Wilderness weather or server startup.</p>
 */
public final class ColdSweatWeatherBridge {

    public static final String MOD_ID = "cold_sweat";

    private static final String[] CALCULATE_POST_CLASSES = {
            "com.momosoftworks.coldsweat.api.event.common.temperautre.TempModifierEvent$Calculate$Post",
            "com.momosoftworks.coldsweat.api.event.common.temperature.TempModifierEvent$Calculate$Post"
    };
    private static final String BIOME_MODIFIER_CLASS =
            "com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier";

    private static volatile BridgeMethods methods;
    private static volatile boolean active;
    private static volatile boolean failureLogged;

    private ColdSweatWeatherBridge() {
    }

    /** Resolves and registers the optional event bridge exactly once. */
    public static boolean bootstrap() {
        if (active) {
            return true;
        }
        try {
            Class<? extends Event> eventClass = resolvePostEvent();
            Method getEntity = eventClass.getMethod("getEntity");
            Method getTrait = eventClass.getMethod("getTrait");
            Method getModifier = eventClass.getMethod("getModifier");
            Method getFunction = eventClass.getMethod("getFunction");
            Method setFunction = eventClass.getMethod("setFunction", Function.class);
            methods = new BridgeMethods(getEntity, getTrait, getModifier, getFunction, setFunction);
            register(eventClass);
            active = true;
            ModConstants.LOGGER.info("Enabled Cold Sweat localized-weather temperature integration");
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            disable("Cold Sweat is installed but its temperature modifier event API could not be resolved", exception);
            return false;
        }
    }

    /** Returns whether the optional Cold Sweat bridge registered successfully. */
    public static boolean active() {
        return active;
    }

    private static Class<? extends Event> resolvePostEvent() throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        for (String className : CALCULATE_POST_CLASSES) {
            try {
                return Class.forName(className, false, ColdSweatWeatherBridge.class.getClassLoader())
                        .asSubclass(Event.class);
            } catch (ClassNotFoundException exception) {
                failure = exception;
            }
        }
        throw failure == null ? new ClassNotFoundException(CALCULATE_POST_CLASSES[0]) : failure;
    }

    private static <T extends Event> void register(Class<T> eventClass) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, eventClass, ColdSweatWeatherBridge::onCalculatePost);
    }

    @SuppressWarnings("unchecked")
    private static void onCalculatePost(Event event) {
        BridgeMethods bridge = methods;
        WeatherConfig.SurvivalIntegrationSettings settings = WeatherConfig.survivalIntegrations();
        if (!active || bridge == null || !settings.coldSweatEnabled()) {
            return;
        }

        try {
            Object modifier = bridge.getModifier.invoke(event);
            if (modifier == null || !BIOME_MODIFIER_CLASS.equals(modifier.getClass().getName())) {
                return;
            }
            Object trait = bridge.getTrait.invoke(event);
            String traitName = trait instanceof Enum<?> enumValue
                    ? enumValue.name()
                    : String.valueOf(trait);
            if (!"WORLD".equalsIgnoreCase(traitName)) {
                return;
            }
            Object rawEntity = bridge.getEntity.invoke(event);
            if (!(rawEntity instanceof LivingEntity entity)
                    || !(entity.level() instanceof ServerLevel level)) {
                return;
            }

            BlockPos position = entity.blockPosition();
            AtmosphereView view = WeatherAuthority.get().cellAt(level, position);
            if (view == null) {
                return;
            }
            boolean exposed = level.canSeeSky(position.above()) && !entity.isInWater();
            Biome biome = level.getBiome(position).value();
            double biomeBaselineCelsius =
                    (biome.getModifiedClimateSettings().temperature() - 0.15) * 25.0;
            WeatherSample sample = view.sample();
            double offset = WeatherSurvivalExposureModel.coldSweatOffsetMinecraftUnits(
                    sample,
                    biomeBaselineCelsius,
                    exposed,
                    settings.coldSweatMaximumOffsetCelsius()
            );
            if (Math.abs(offset) < 1.0e-6) {
                return;
            }

            Function<Double, Double> original =
                    (Function<Double, Double>) bridge.getFunction.invoke(event);
            Function<Double, Double> adjusted = input -> original.apply(input) + offset;
            bridge.setFunction.invoke(event, adjusted);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            disable("Cold Sweat weather integration stopped after an incompatible runtime API response", exception);
        }
    }

    private static void disable(String message, Throwable throwable) {
        active = false;
        methods = null;
        if (!failureLogged) {
            failureLogged = true;
            ModConstants.LOGGER.warn("{}; the guarded adapter is disabled", message, throwable);
        }
    }

    private record BridgeMethods(
            Method getEntity,
            Method getTrait,
            Method getModifier,
            Method getFunction,
            Method setFunction
    ) {
    }
}
