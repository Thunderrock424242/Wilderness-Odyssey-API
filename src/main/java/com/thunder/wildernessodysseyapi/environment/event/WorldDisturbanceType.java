package com.thunder.wildernessodysseyapi.environment.event;

import com.thunder.wildernessodysseyapi.ecosystem.memory.DisturbanceSource;
import com.thunder.wildernessodysseyapi.vegetation.api.PlantDisturbanceType;

/** Typed cross-system events that may inform ecology and vegetation. */
public enum WorldDisturbanceType {
    LIGHTNING(DisturbanceSource.LIGHTNING, PlantDisturbanceType.LIGHTNING, 600),
    SEVERE_WEATHER(DisturbanceSource.SEVERE_WEATHER, PlantDisturbanceType.WIND, 600),
    WILDFIRE(DisturbanceSource.FIRE, PlantDisturbanceType.FIRE, 24_000),
    FLOOD(DisturbanceSource.FLOOD, PlantDisturbanceType.FLOOD, 2_400),
    DROUGHT(DisturbanceSource.DROUGHT, PlantDisturbanceType.DROUGHT, 24_000),
    METEOR_IMPACT(DisturbanceSource.METEOR, PlantDisturbanceType.METEOR, 24_000),
    RADIATION(DisturbanceSource.RADIATION, PlantDisturbanceType.RADIATION, 24_000),
    RIFTFALL(DisturbanceSource.RIFTFALL, PlantDisturbanceType.RIFTFALL, 2_400);

    private final DisturbanceSource ecosystemSource;
    private final PlantDisturbanceType plantSource;
    private final int plantDurationTicks;

    WorldDisturbanceType(
            DisturbanceSource ecosystemSource,
            PlantDisturbanceType plantSource,
            int plantDurationTicks
    ) {
        this.ecosystemSource = ecosystemSource;
        this.plantSource = plantSource;
        this.plantDurationTicks = plantDurationTicks;
    }

    /** Returns the persistent environmental-memory category for wildlife. */
    public DisturbanceSource ecosystemSource() {
        return ecosystemSource;
    }

    /** Returns the vegetation-owned category for regional plant pressure. */
    public PlantDisturbanceType plantSource() {
        return plantSource;
    }

    /** Returns the bounded lifetime of the vegetation-side signal. */
    public int plantDurationTicks() {
        return plantDurationTicks;
    }
}
