package com.thunder.wildernessodysseyapi.weather.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherForecast;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereSimulationEngine;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericFrontModel;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Operator diagnostics and localized atmosphere controls.
 *
 * <p>All mutations pass through {@link WeatherAuthority}; clients cannot send
 * an authoritative weather payload or modify a cell directly.</p>
 */
public final class WeatherDebugCommand {

    private WeatherDebugCommand() {
    }

    /** Registers the permission-gated {@code /wilderness weather} command tree. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wilderness")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("weather")
                        .then(Commands.literal("sample")
                                .executes(context -> sample(context.getSource())))
                        .then(Commands.literal("cell")
                                .executes(context -> cell(context.getSource())))
                        .then(Commands.literal("forecast")
                                .executes(context -> forecast(context.getSource())))
                        .then(Commands.literal("systems")
                                .executes(context -> systems(context.getSource())))
                        .then(Commands.literal("set")
                                .then(scalar("humidity", WeatherAuthority.ControlField.HUMIDITY, 0.0, 1.0))
                                .then(scalar("pressure", WeatherAuthority.ControlField.PRESSURE, 0.5, 1.5))
                                .then(scalar("temperature", WeatherAuthority.ControlField.TEMPERATURE, -80.0, 60.0))
                                .then(scalar("storm_energy", WeatherAuthority.ControlField.STORM_ENERGY, 0.0, 1.0)))
                        .then(Commands.literal("force")
                                .then(Commands.literal("rain")
                                        .executes(context -> force(
                                                context.getSource(), PrecipitationType.RAIN)))
                                .then(Commands.literal("snow")
                                        .executes(context -> force(
                                                context.getSource(), PrecipitationType.SNOW)))
                                .then(Commands.literal("hail")
                                        .executes(context -> force(
                                                context.getSource(), PrecipitationType.HAIL))))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource())))
                        .then(Commands.literal("dump")
                                .executes(context -> dump(context.getSource())))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> scalar(
            String name,
            WeatherAuthority.ControlField field,
            double minimum,
            double maximum
    ) {
        return Commands.literal(name)
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(minimum, maximum))
                        .executes(context -> set(
                                context.getSource(),
                                field,
                                DoubleArgumentType.getDouble(context, "value")
                        )));
    }

    private static int sample(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos position = BlockPos.containing(source.getPosition());
        WeatherAuthority authority = WeatherAuthority.get();
        WeatherSample sample = authority.sample(level, position);
        int cellSize = WeatherConfig.scheduling().cellSize();
        AtmosphericFrontModel.FrontState front = AtmosphericFrontModel.analyze(
                sample,
                new AtmosphereSimulationEngine.Neighborhood(
                        authority.sample(level, position.offset(0, 0, -cellSize)),
                        authority.sample(level, position.offset(cellSize, 0, 0)),
                        authority.sample(level, position.offset(0, 0, cellSize)),
                        authority.sample(level, position.offset(-cellSize, 0, 0))
                )
        );
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Weather at %d %d %d: T %.2f C, H %.3f, P %.3f, wind %.3f %.3f, cloud %s %.3f, instability %.3f, storm %.3f, front %s %.3f, %s %.3f",
                position.getX(),
                position.getY(),
                position.getZ(),
                sample.temperature(),
                sample.humidity(),
                sample.pressure(),
                sample.wind().x(),
                sample.wind().z(),
                sample.cloudType().displayName(),
                sample.cloudWater(),
                sample.instability(),
                sample.stormEnergy(),
                front.type(),
                front.strength(),
                sample.precipitationType(),
                sample.precipitationIntensity()
        )), false);
        return 1;
    }

    private static int cell(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AtmosphereView view = WeatherAuthority.get().cellAt(
                level,
                BlockPos.containing(source.getPosition())
        );
        if (view == null) {
            source.sendFailure(Component.literal("No atmosphere cell is active here yet."));
            return 0;
        }
        WeatherAuthority.Activity activity = WeatherAuthority.get().activity(level, view);
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Cell %d,%d: revision %d, simulated %d, active %d, state %s",
                view.key().x(),
                view.key().z(),
                view.revision(),
                view.lastSimulatedTick(),
                view.lastActiveTick(),
                activity
        )), false);
        return 1;
    }

    private static int forecast(CommandSourceStack source) {
        BlockPos position = BlockPos.containing(source.getPosition());
        WeatherForecast forecast = WeatherAuthority.get().forecast(source.getLevel(), position);
        String approaching = forecast.approachingSystem() == null
                ? "no organized system detected"
                : String.format(
                        Locale.ROOT,
                        "%s in %.0f blocks, ETA about %.1f minutes",
                        forecast.approachingSystem().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                        forecast.distanceBlocks(),
                        forecast.estimatedArrivalTicks() / 1_200.0
                );
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Forecast: %s pressure (trend %+.4f), wind %.2f %.2f; %s; confidence %.0f%%",
                forecast.pressureTendency(),
                forecast.pressureTrend(),
                forecast.wind().x(),
                forecast.wind().z(),
                approaching,
                forecast.confidence() * 100.0
        )), false);
        return 1;
    }

    private static int systems(CommandSourceStack source) {
        BlockPos position = BlockPos.containing(source.getPosition());
        List<TrackedWeatherSystem> systems = WeatherAuthority.get().systems(source.getLevel());
        TrackedWeatherSystem nearest = systems.stream()
                .min(java.util.Comparator.comparingDouble(system ->
                        system.distanceSquared(position.getX(), position.getZ())))
                .orElse(null);
        if (nearest == null) {
            source.sendSuccess(() -> Component.literal("No persistent storm or front identities are active."), false);
            return 0;
        }
        double distance = Math.sqrt(nearest.distanceSquared(position.getX(), position.getZ()));
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "%d persistent systems; nearest #%d %s is %.0f blocks away, %s at %.0f%% intensity.",
                systems.size(),
                nearest.id(),
                nearest.type().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                distance,
                nearest.stage().name().toLowerCase(Locale.ROOT),
                nearest.intensity() * 100.0
        )), false);
        return systems.size();
    }

    private static int set(
            CommandSourceStack source,
            WeatherAuthority.ControlField field,
            double value
    ) {
        boolean changed = WeatherAuthority.get().setField(
                source.getLevel(),
                BlockPos.containing(source.getPosition()),
                field,
                value
        );
        if (!changed) {
            source.sendFailure(Component.literal("Localized weather is disabled in this dimension."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Set local %s to %.3f.",
                field.name().toLowerCase(Locale.ROOT),
                value
        )), true);
        return 1;
    }

    private static int force(CommandSourceStack source, PrecipitationType type) {
        int changed = WeatherAuthority.get().forcePrecipitation(
                source.getLevel(),
                BlockPos.containing(source.getPosition()),
                type
        );
        if (changed == 0) {
            source.sendFailure(Component.literal("Localized weather is disabled in this dimension."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Forced local " + type.name().toLowerCase(Locale.ROOT)
                        + " across " + changed + " atmosphere cells."
        ), true);
        return changed;
    }

    private static int clear(CommandSourceStack source) {
        int changed = WeatherAuthority.get().clearLocalWeather(
                source.getLevel(),
                BlockPos.containing(source.getPosition())
        );
        if (changed == 0) {
            source.sendFailure(Component.literal("Localized weather is disabled in this dimension."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Cleared local precipitation across " + changed + " atmosphere cells."
        ), true);
        return changed;
    }

    private static int dump(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<AtmosphereView> cells = WeatherAuthority.get().cells(level);
        Map<WeatherAuthority.Activity, Integer> counts = new EnumMap<>(WeatherAuthority.Activity.class);
        for (WeatherAuthority.Activity activity : WeatherAuthority.Activity.values()) {
            counts.put(activity, 0);
        }
        for (AtmosphereView view : cells) {
            WeatherAuthority.Activity activity = WeatherAuthority.get().activity(level, view);
            counts.put(activity, counts.get(activity) + 1);
        }

        WeatherConfig.SchedulingSettings settings = WeatherConfig.scheduling();
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Atmosphere %s: %d/%d retained cells, size %d, interval %d, active %d, grace %d, storms %d, dormant %d",
                level.dimension().location(),
                cells.size(),
                settings.maxPersistedCells(),
                settings.cellSize(),
                settings.simulationIntervalTicks(),
                counts.get(WeatherAuthority.Activity.ACTIVE),
                counts.get(WeatherAuthority.Activity.GRACE),
                counts.get(WeatherAuthority.Activity.PERSISTENT_STORM),
                counts.get(WeatherAuthority.Activity.DORMANT)
        )), false);
        return cells.size();
    }
}
