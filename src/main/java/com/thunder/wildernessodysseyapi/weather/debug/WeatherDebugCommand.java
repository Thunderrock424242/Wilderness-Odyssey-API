package com.thunder.wildernessodysseyapi.weather.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherForecast;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.weather.api.WindManager;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereSimulationEngine;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericFrontModel;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import com.thunder.wildernessodysseyapi.weather.wildfire.WildfireIgnitionPolicy;
import com.thunder.wildernessodysseyapi.weather.wildfire.WildfireRiskModel;
import com.thunder.wildernessodysseyapi.weather.wildfire.WildfireScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

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
                        .then(Commands.literal("wind")
                                .executes(context -> wind(context.getSource())))
                        .then(Commands.literal("cell")
                                .executes(context -> cell(context.getSource())))
                        .then(Commands.literal("forecast")
                                .executes(context -> forecast(context.getSource())))
                        .then(Commands.literal("systems")
                                .executes(context -> systems(context.getSource())))
                        .then(Commands.literal("wildfire")
                                .then(Commands.literal("risk")
                                        .executes(context -> wildfireRisk(context.getSource())))
                                .then(Commands.literal("ignite")
                                        .executes(context -> igniteWildfire(context.getSource()))))
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
                                                context.getSource(), PrecipitationType.HAIL)))
                                .then(cloudTypes()))
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

    /** Builds literal children so every supported genus appears in tab completion. */
    private static LiteralArgumentBuilder<CommandSourceStack> cloudTypes() {
        LiteralArgumentBuilder<CommandSourceStack> cloud = Commands.literal("cloud");
        for (CloudType type : CloudType.values()) {
            cloud.then(Commands.literal(type.name().toLowerCase(Locale.ROOT))
                    .executes(context -> forceCloud(context.getSource(), type)));
        }
        return cloud;
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
        sendWind(source, position);
        return 1;
    }

    private static int wind(CommandSourceStack source) {
        return sendWind(source, BlockPos.containing(source.getPosition()));
    }

    private static int sendWind(CommandSourceStack source, BlockPos position) {
        WindSample wind = WindManager.getWind(source.getLevel(), source.getPosition());
        Vec3 direction = wind.direction();
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Wind at %d %d %d: %s direction %.3f %.3f %.3f, sustained %.2f blocks/s, gust +%.2f (factor %.3f, phase %.3f), effective %.2f, weather +%.2f, region %d,%d.",
                position.getX(),
                position.getY(),
                position.getZ(),
                compassDirection(direction),
                direction.x,
                direction.y,
                direction.z,
                wind.speed(),
                wind.gust(),
                wind.gustFactor(),
                wind.gustPhase(),
                wind.effectiveSpeed(),
                wind.weatherContribution(),
                wind.region().x(),
                wind.region().z()
        )), false);
        return 1;
    }

    private static String compassDirection(Vec3 direction) {
        if (direction.horizontalDistanceSqr() <= 1.0E-8D) {
            return "CALM";
        }
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double angle = Math.atan2(direction.x, -direction.z);
        int index = Math.floorMod((int) Math.round(angle / (Math.PI / 4.0D)), directions.length);
        return directions[index];
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
        WeatherAuthority authority = WeatherAuthority.get();
        WeatherForecast forecast = authority.forecast(source.getLevel(), position);
        WeatherThreatForecast threat = authority.getApproachingWeather(source.getLevel(), position, 7_200);
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
        String threatSummary = threat.incoming()
                ? String.format(
                        Locale.ROOT,
                        "%s %.0f%% at %.0f blocks, ETA %.1f minutes, source #%d %s/%s, ambient wildlife %.0f%%",
                        threat.type().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                        threat.intensity() * 100.0,
                        threat.distanceBlocks(),
                        threat.estimatedArrivalTicks() / 1_200.0,
                        threat.sourceSystemId(),
                        threat.sourceSystem().name().toLowerCase(Locale.ROOT),
                        threat.sourceStage().name().toLowerCase(Locale.ROOT),
                        threat.ambientWildlifeActivityScale() * 100.0
                )
                : "none within 6.0 minutes";
        source.sendSuccess(() -> Component.literal("  Wildlife threat forecast: " + threatSummary), false);
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

    private static int wildfireRisk(CommandSourceStack source) {
        BlockPos position = BlockPos.containing(source.getPosition());
        WildfireRiskModel.RiskProfile risk = WeatherAuthority.get().wildfireRisk(
                source.getLevel(),
                position
        );
        double chance = WildfireIgnitionPolicy.ignitionChance(risk, WeatherConfig.wildfires());
        String seasonSource = risk.calendarAvailable() ? "external calendar" : "extreme-weather fallback";
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Wildfire risk: %s, score %.1f%%, chance %.4f%% per due scan; drought %.3f, fire season %.3f (%s), air dryness %.3f, ground dryness %.3f, wind %.3f.",
                risk.eligible() ? "ELIGIBLE" : "not eligible",
                risk.risk() * 100.0,
                chance * 100.0,
                risk.drought(),
                risk.fireSeason(),
                seasonSource,
                risk.airDryness(),
                risk.groundDryness(),
                risk.wind()
        )), false);
        return risk.eligible() ? 1 : 0;
    }

    private static int igniteWildfire(CommandSourceStack source) {
        WildfireScheduler.IgnitionResult result = WeatherAuthority.get().forceWildfireIgnition(
                source.getLevel(),
                BlockPos.containing(source.getPosition())
        );
        if (result == WildfireScheduler.IgnitionResult.IGNITED) {
            source.sendSuccess(() -> Component.literal(
                    "Forced one nearby open campfire ember into exposed tagged fuel."
            ), true);
            return 1;
        }
        String reason = switch (result) {
            case DISABLED -> "Wildfires or localized weather are disabled here.";
            case FIRE_TICK_DISABLED -> "The dimension lacks open sky or doFireTick is disabled.";
            case NO_CAMPFIRE -> "No open, lit normal campfire was found within 32 blocks.";
            case NO_FUEL -> "No exposed tagged ignition fuel was found within ember range.";
            case IGNITED -> throw new IllegalStateException("Handled successful wildfire ignition");
        };
        source.sendFailure(Component.literal(reason));
        return 0;
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

    private static int forceCloud(CommandSourceStack source, CloudType type) {
        int changed = WeatherAuthority.get().forceCloudType(
                source.getLevel(),
                BlockPos.containing(source.getPosition()),
                type
        );
        if (changed == 0) {
            source.sendFailure(Component.literal("Localized weather is disabled in this dimension."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Forced the " + type.displayName() + " cloud preset across " + changed
                        + " atmosphere cells. Use '/wilderness weather clear' to reset it."
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
                "Cleared local weather across " + changed + " atmosphere cells."
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
