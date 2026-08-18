package com.thunder.wildernessodysseyapi.environment.api;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallDimensionRules;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Declarative feature policy for one dimension's shared environment.
 *
 * <p>The profile owns no state and never enables a subsystem by itself. It only
 * tells integrations which authoritative services are meaningful in a given
 * dimension, keeping The Before's empty-world policy and The Echo's Riftfall
 * identity out of scattered consumer checks.</p>
 */
public record EnvironmentDimensionProfile(
        boolean atmosphere,
        boolean dynamicWater,
        boolean ecosystem,
        boolean reactiveVegetation,
        boolean naturalMeteors,
        boolean radiation,
        boolean riftfall
) {

    /** Standard living-world policy used by the Overworld and ordinary dimensions. */
    public static final EnvironmentDimensionProfile LIVING_WORLD = new EnvironmentDimensionProfile(
            true, true, true, true, false, true, false
    );
    /** Deliberately inert policy used by The Before. */
    public static final EnvironmentDimensionProfile EMPTY_WORLD = new EnvironmentDimensionProfile(
            false, false, false, false, false, false, false
    );

    /** Resolves stable dimension identity without consulting mutable runtime state. */
    public static EnvironmentDimensionProfile forDimension(ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        if (TemporalRiftDimensions.THE_BEFORE_KEY.equals(dimension)) {
            return EMPTY_WORLD;
        }
        if (TemporalRiftDimensions.THE_ECHO_KEY.equals(dimension)) {
            return new EnvironmentDimensionProfile(true, true, true, true, false, true, true);
        }
        if (AnomalyDimensions.ANOMALY_DIMENSION_KEY.equals(dimension)) {
            return new EnvironmentDimensionProfile(true, true, true, true, false, true, false);
        }
        if (Level.OVERWORLD.equals(dimension)) {
            return new EnvironmentDimensionProfile(true, true, true, true, true, true, false);
        }
        return LIVING_WORLD;
    }

    /** Applies current server configuration to the stable dimension policy. */
    public static EnvironmentDimensionProfile forLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        EnvironmentDimensionProfile base = forDimension(level.dimension());
        return new EnvironmentDimensionProfile(
                base.atmosphere() && WeatherConfig.dimensionEnabled(level.dimension()),
                base.dynamicWater(),
                base.ecosystem(),
                base.reactiveVegetation(),
                base.naturalMeteors(),
                base.radiation(),
                base.riftfall() && RiftfallDimensionRules.isEligible(level.dimension())
        );
    }

    /** Returns whether this dimension participates in any shared environment channel. */
    public boolean participates() {
        return atmosphere || dynamicWater || ecosystem || reactiveVegetation
                || naturalMeteors || radiation || riftfall;
    }
}
