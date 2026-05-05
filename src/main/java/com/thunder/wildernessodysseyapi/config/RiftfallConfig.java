package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class RiftfallConfig {
    public static final RiftfallConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<RiftfallConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(RiftfallConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    private final ModConfigSpec.BooleanValue enabled;
    private final ModConfigSpec.DoubleValue baseStartChance;
    private final ModConfigSpec.DoubleValue thunderMultiplier;
    private final ModConfigSpec.IntValue checkIntervalTicks;
    private final ModConfigSpec.IntValue cooldownTicks;

    private final ModConfigSpec.IntValue warningTicks;
    private final ModConfigSpec.IntValue activeTicks;
    private final ModConfigSpec.IntValue endingTicks;
    private final ModConfigSpec.DoubleValue meteorSurgeChance;
    private final ModConfigSpec.IntValue meteorSurgeTicks;

    private final ModConfigSpec.DoubleValue exposureGainPerTick;
    private final ModConfigSpec.DoubleValue exposureDecayShelteredPerTick;
    private final ModConfigSpec.DoubleValue exposureDecayClearPerTick;
    private final ModConfigSpec.BooleanValue allowNaturalBlockCorrosion;
    private final ModConfigSpec.BooleanValue allowCropDamage;
    private final ModConfigSpec.IntValue corrosionIntervalTicks;
    private final ModConfigSpec.IntValue corrosionChecksPerPlayerInterval;
    private final ModConfigSpec.IntValue riftbornSpawnIntervalTicks;
    private final ModConfigSpec.IntValue riftbornSpawnBudgetActive;
    private final ModConfigSpec.IntValue riftbornSpawnBudgetSurge;
    private final ModConfigSpec.IntValue maxRiftbornPerPlayer;
    private final ModConfigSpec.IntValue maxRiftbornGlobal;

    RiftfallConfig(ModConfigSpec.Builder builder) {
        builder.push("riftfall");
        enabled = builder.define("enabled", true);
        baseStartChance = builder.defineInRange("baseStartChance", 0.002D, 0D, 1D);
        thunderMultiplier = builder.defineInRange("thunderMultiplier", 2.5D, 1D, 10D);
        checkIntervalTicks = builder.defineInRange("checkIntervalTicks", 200, 20, 2400);
        cooldownTicks = builder.defineInRange("cooldownTicks", 48000, 1200, 480000);

        warningTicks = builder.defineInRange("warningTicks", 1200, 100, 12000);
        activeTicks = builder.defineInRange("activeTicks", 7200, 200, 48000);
        endingTicks = builder.defineInRange("endingTicks", 600, 100, 12000);
        meteorSurgeChance = builder.defineInRange("meteorSurgeChance", 0.2D, 0D, 1D);
        meteorSurgeTicks = builder.defineInRange("meteorSurgeTicks", 1800, 100, 12000);

        exposureGainPerTick = builder.defineInRange("exposureGainPerTick", 0.04D, 0D, 5D);
        exposureDecayShelteredPerTick = builder.defineInRange("exposureDecayShelteredPerTick", 0.08D, 0D, 5D);
        exposureDecayClearPerTick = builder.defineInRange("exposureDecayClearPerTick", 0.03D, 0D, 5D);
        allowNaturalBlockCorrosion = builder.define("allowNaturalBlockCorrosion", true);
        allowCropDamage = builder.define("allowCropDamage", false);
        corrosionIntervalTicks = builder.defineInRange("corrosionIntervalTicks", 100, 20, 1200);
        corrosionChecksPerPlayerInterval = builder.defineInRange("corrosionChecksPerPlayerInterval", 4, 1, 64);
        riftbornSpawnIntervalTicks = builder.defineInRange("riftbornSpawnIntervalTicks", 80, 20, 1200);
        riftbornSpawnBudgetActive = builder.defineInRange("riftbornSpawnBudgetActive", 1, 0, 10);
        riftbornSpawnBudgetSurge = builder.defineInRange("riftbornSpawnBudgetSurge", 2, 0, 20);
        maxRiftbornPerPlayer = builder.defineInRange("maxRiftbornPerPlayer", 6, 1, 64);
        maxRiftbornGlobal = builder.defineInRange("maxRiftbornGlobal", 60, 1, 512);
        builder.pop();
    }

    public boolean enabled() { return enabled.get(); }
    public double baseStartChance() { return baseStartChance.get(); }
    public double thunderMultiplier() { return thunderMultiplier.get(); }
    public int checkIntervalTicks() { return checkIntervalTicks.get(); }
    public int cooldownTicks() { return cooldownTicks.get(); }
    public int warningTicks() { return warningTicks.get(); }
    public int activeTicks() { return activeTicks.get(); }
    public int endingTicks() { return endingTicks.get(); }
    public double meteorSurgeChance() { return meteorSurgeChance.get(); }
    public int meteorSurgeTicks() { return meteorSurgeTicks.get(); }
    public double exposureGainPerTick() { return exposureGainPerTick.get(); }
    public double exposureDecayShelteredPerTick() { return exposureDecayShelteredPerTick.get(); }
    public double exposureDecayClearPerTick() { return exposureDecayClearPerTick.get(); }
    public boolean allowNaturalBlockCorrosion() { return allowNaturalBlockCorrosion.get(); }
    public boolean allowCropDamage() { return allowCropDamage.get(); }
    public int corrosionIntervalTicks() { return corrosionIntervalTicks.get(); }
    public int corrosionChecksPerPlayerInterval() { return corrosionChecksPerPlayerInterval.get(); }
    public int riftbornSpawnIntervalTicks() { return riftbornSpawnIntervalTicks.get(); }
    public int riftbornSpawnBudgetActive() { return riftbornSpawnBudgetActive.get(); }
    public int riftbornSpawnBudgetSurge() { return riftbornSpawnBudgetSurge.get(); }
    public int maxRiftbornPerPlayer() { return maxRiftbornPerPlayer.get(); }
    public int maxRiftbornGlobal() { return maxRiftbornGlobal.get(); }
}
