package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Installation-wide, opt-in controls for generation of new Overworld terrain. */
public final class RiverWorldgenConfig {
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.DoubleValue frequency;
    private static ModConfigSpec.IntValue contribution;
    private static ModConfigSpec.IntValue streamDepth;
    private static ModConfigSpec.IntValue lakeDepth;
    private static ModConfigSpec.IntValue maximumCut;
    private static ModConfigSpec.IntValue maximumLakeColumns;
    private static ModConfigSpec.IntValue maximumWrites;
    private static ModConfigSpec.BooleanValue lakes;
    private static ModConfigSpec.BooleanValue waterfalls;
    private static ModConfigSpec.BooleanValue naturalBanks;

    private RiverWorldgenConfig() { }

    public static void defineCommon(ModConfigSpec.Builder builder) {
        builder.comment("Bounded tributaries and sealed pools in new Overworld terrain only.",
                "Existing chunks are never recarved. Changing settings can create a transition at new terrain.",
                "Supported noise generators only; unresolved drainage remains untouched.")
                .push("riverWorldgen");
        enabled = builder.define("enabled", false);
        frequency = builder.comment("Probability of attempting a complete 32 by 32 block catchment.")
                .defineInRange("frequency", 0.35, 0.0, 1.0);
        contribution = builder.comment("Minimum contributing 4 by 4 block planning cells for a tributary.")
                .defineInRange("minimumContributingCells", 3, 1, 64);
        streamDepth = builder.comment("Water depth in blocks.").defineInRange("streamDepth", 1, 1, 3);
        lakeDepth = builder.comment("Maximum pool depth in blocks.").defineInRange("lakeDepth", 2, 1, 4);
        maximumCut = builder.comment("Maximum removed terrain depth in blocks.")
                .defineInRange("maximumCut", 4, 1, 10);
        maximumLakeColumns = builder.defineInRange("maximumLakeColumns", 256, 1, 512);
        maximumWrites = builder.comment("Whole-assembly block write cap; over-budget plans are skipped entirely.")
                .defineInRange("maximumWrites", 12288, 1, 32768);
        lakes = builder.define("lakes", true);
        waterfalls = builder.comment("Allow supported drops greater than one block, up to four blocks.")
                .define("waterfalls", true);
        naturalBanks = builder.comment("Use biome-aware gravel, sand, clay, mud and stone riverbeds.")
                .define("naturalBanks", true);
        builder.pop();
    }

    /** Cheap disabled-path check before allocating captures or invoking the feature. */
    public static boolean enabled() {
        WildernessConfigSpecs.initialize();
        return enabled.get();
    }

    /** Captures bounded values for a generation invocation and its compatibility fingerprint. */
    public static RiverGenerationSettings snapshot() {
        WildernessConfigSpecs.initialize();
        return new RiverGenerationSettings(enabled.get(), frequency.get(), contribution.get(), streamDepth.get(),
                lakeDepth.get(), maximumCut.get(), maximumLakeColumns.get(), maximumWrites.get(),
                lakes.get(), waterfalls.get(), naturalBanks.get());
    }
}
