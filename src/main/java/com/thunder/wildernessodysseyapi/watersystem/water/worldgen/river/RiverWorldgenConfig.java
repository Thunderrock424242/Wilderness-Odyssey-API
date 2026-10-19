package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Installation-wide, opt-in controls for generation of new Overworld terrain. */
public final class RiverWorldgenConfig {
    public static ModConfigSpec.BooleanValue ENABLED;
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
    private static ModConfigSpec.BooleanValue connected;
    private static ModConfigSpec.EnumValue<PlanningQuality> planningQuality;
    private static ModConfigSpec.IntValue maximumTerrainSamples;
    private static ModConfigSpec.IntValue maximumRadius;
    private static ModConfigSpec.IntValue maximumDrop;

    private RiverWorldgenConfig() { }

    public static void defineCommon(ModConfigSpec.Builder builder) {
        builder.comment("Bounded connected tributaries, certified local outlets and sealed pools in new Overworld terrain only.",
                "Existing chunks are never recarved. Changing settings can create a transition at new terrain.",
                "Supported noise generators only; unresolved drainage remains untouched.")
                .push("riverWorldgen");
        ENABLED = builder.define("enabled", false);
        connected = builder.comment("Use bounded, chunk-independent source-to-existing-water routes on the supported noise generator.",
                "Protected or uncertain physical terrain is rejected. Closed local pools remain a conservative fallback.")
                .define("connectedNetworks", true);
        planningQuality = builder.comment("Topology-affecting planning preset; use a new world when changing this.")
                .defineEnum("planningQuality", PlanningQuality.STANDARD);
        maximumTerrainSamples = builder.comment("Maximum unique prediction samples per complete plan; exhaustion rejects the connected plan.")
                .defineInRange("maximumTerrainSamples", 4096, 128, 4096);
        maximumRadius = builder.comment("Maximum channel half-width in blocks. Actual width follows certified tributary contributions.")
                .defineInRange("maximumChannelRadius", 4, 1, 8);
        maximumDrop = builder.comment("Maximum supported vertical falling transition in blocks. Uncertified drops remain unchanged.")
                .defineInRange("maximumWaterfallDrop", 4, 1, 16);
        frequency = builder.comment("Climate-adjusted source probability; also controls conservative local-pool attempts.")
                .defineInRange("frequency", 0.35, 0.0, 1.0);
        contribution = builder.comment("Minimum bounded upstream cell count (16-block connected lattice; 4-block local fallback).")
                .defineInRange("minimumContributingCells", 3, 1, 64);
        streamDepth = builder.comment("Water depth in blocks.").defineInRange("streamDepth", 1, 1, 3);
        lakeDepth = builder.comment("Maximum pool depth in blocks.").defineInRange("lakeDepth", 2, 1, 4);
        maximumCut = builder.comment("Maximum removed terrain depth in blocks.")
                .defineInRange("maximumCut", 4, 1, 10);
        maximumLakeColumns = builder.defineInRange("maximumLakeColumns", 256, 1, 512);
        maximumWrites = builder.comment("Whole-assembly block write cap; over-budget plans are skipped entirely.")
                .defineInRange("maximumWrites", 12288, 1, 32768);
        lakes = builder.define("lakes", true);
        waterfalls = builder.comment("Allow supported falling transitions greater than one block, within maximumWaterfallDrop.",
                "The local fallback remains limited to four-block drops.")
                .define("waterfalls", true);
        naturalBanks = builder.comment("Use biome-aware gravel, sand, clay, mud and stone riverbeds.")
                .define("naturalBanks", true);
        builder.pop();
    }

    /** Cheap disabled-path check before allocating captures or invoking the feature. */
    public static boolean enabled() {
        WildernessConfigSpecs.initialize();
        return ENABLED.get();
    }

    /** Captures bounded values for a generation invocation and its compatibility fingerprint. */
    public static RiverGenerationSettings snapshot() {
        WildernessConfigSpecs.initialize();
        return new RiverGenerationSettings(ENABLED.get(), frequency.get(), contribution.get(), streamDepth.get(),
                lakeDepth.get(), maximumCut.get(), maximumLakeColumns.get(), maximumWrites.get(),
                lakes.get(), waterfalls.get(), naturalBanks.get(), connected.get()
                ? 1L + planningQuality.get().ordinal() * 1_000_000L + maximumTerrainSamples.get() * 128L
                + maximumRadius.get() * 16L + maximumDrop.get() : 0);
    }

    public static boolean connectedNetworks() { return connected.get(); }
    public static int maximumDrop() { return maximumDrop.get(); }
    public static RiverConnectedPlanner.Settings planningSettings() {
        return new RiverConnectedPlanner.Settings(planningQuality.get().steps, frequency.get(), contribution.get(),
                maximumRadius.get(), maximumTerrainSamples.get());
    }

    /** Different bounds intentionally change which source-to-outlet routes can be certified. */
    public enum PlanningQuality {
        CONSERVATIVE(4), STANDARD(8), DETAILED(12);
        private final int steps;
        PlanningQuality(int steps) { this.steps = steps; }
    }
}
