package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Unit-bearing server controls for the finite regional cycle, not per-block simulation. */
public final class RegionalHydrologyConfig {
    private static ModConfigSpec.DoubleValue rainfall;
    private static ModConfigSpec.DoubleValue evaporation;
    private static ModConfigSpec.DoubleValue roughness;
    private static ModConfigSpec.DoubleValue bankfullDepth;
    private static ModConfigSpec.DoubleValue aquiferTime;
    private static ModConfigSpec.IntValue maximumRegions;
    private static ModConfigSpec.IntValue topologyWork;
    private static ModConfigSpec.IntValue catchupSteps;
    private static ModConfigSpec.IntValue tolerance;
    private static ModConfigSpec.BooleanValue thermal;

    private RegionalHydrologyConfig() { }

    public static void define(ModConfigSpec.Builder builder) {
        builder.comment("Conserved regional water. One game-hour is 50 simulation seconds (1000 ticks).")
                .push("regional_hydrology");
        rainfall = builder.comment("Millimetres per game-hour at precipitation intensity 1.")
                .defineInRange("rainfallMillimetresPerGameHour", 12.0, 0.0, 200.0);
        evaporation = builder.comment("Maximum millimetres per game-hour before humidity, sunlight, wind and availability limits.")
                .defineInRange("evaporationMillimetresPerGameHour", 0.15, 0.0, 10.0);
        roughness = builder.comment("Manning channel roughness n (seconds per metre^(1/3)).")
                .defineInRange("channelRoughness", 0.045, 0.015, 0.25);
        bankfullDepth = builder.comment("Default channel containment depth in metres.")
                .defineInRange("bankfullDepthMetres", 1.25, 0.25, 8.0);
        aquiferTime = builder.comment("Groundwater exponential baseflow residence time in simulation seconds.")
                .defineInRange("aquiferResidenceSeconds", 1800.0, 60.0, 86400.0);
        maximumRegions = builder.comment("Admission cap. Existing saved physical inventories are never evicted when this is reduced.")
                .defineInRange("maximumRegions", 8192, 64, 65536);
        topologyWork = builder.comment("Maximum cached DEM nodes handled per tick while rebuilding drainage.")
                .defineInRange("topologyNodesPerTick", 128, 8, 2048);
        catchupSteps = builder.comment("Maximum analytical catch-up intervals per selected region per tick. Steps coarsen from 2 seconds up to 1 simulation hour; remaining time is retained.")
                .defineInRange("catchupStepsPerRegion", 4, 1, 32);
        tolerance = builder.comment("Developer budget warning tolerance in milli-canonical units; 1000 equals one canonical unit.")
                .defineInRange("budgetToleranceMilliUnits", 0, 0, 4096000);
        thermal = builder.comment("Exchange regional liquid/ice inventory and update aggregate water temperature without replacing blocks.")
                .define("thermalStorageEnabled", true);
        builder.pop();
    }

    public static double rainfall() { return rainfall.get(); }
    public static double evaporation() { return evaporation.get(); }
    public static double roughness() { return roughness.get(); }
    public static double bankfullDepth() { return bankfullDepth.get(); }
    public static double aquiferTime() { return aquiferTime.get(); }
    public static int maximumRegions() { return maximumRegions.get(); }
    public static int topologyWork() { return topologyWork.get(); }
    public static int catchupSteps() { return catchupSteps.get(); }
    public static int tolerance() { return tolerance.get(); }
    public static boolean thermal() { return thermal.get(); }
}
