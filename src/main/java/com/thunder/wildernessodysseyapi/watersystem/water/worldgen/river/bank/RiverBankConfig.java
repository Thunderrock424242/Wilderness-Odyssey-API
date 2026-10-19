package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank;

import net.neoforged.neoforge.common.ModConfigSpec;

/** New-terrain bank presentation controls; the owning river generation switch remains authoritative. */
public final class RiverBankConfig {
    private static ModConfigSpec.BooleanValue palettes;
    private static ModConfigSpec.BooleanValue vegetation;
    private static ModConfigSpec.DoubleValue chance;
    private static ModConfigSpec.IntValue writes;

    private RiverBankConfig() { }

    public static void defineCommon(ModConfigSpec.Builder builder) {
        builder.comment("Optional native riverbed palettes and sparse dry-bank plants in new terrain only.")
                .push("riverBanks");
        palettes = builder.define("dataDrivenPalettes", true);
        vegetation = builder.define("vegetation", false);
        chance = builder.comment("Probability per verified adjacent dry bank column.")
                .defineInRange("vegetationChance", 0.18, 0.0, 1.0);
        writes = builder.comment("Maximum single-block vegetation writes per chunk, also bounded by the river assembly's remaining budget.")
                .defineInRange("maximumVegetationWrites", 8, 0, 64);
        builder.pop();
    }

    public static boolean paletteEnabled() { return palettes == null || palettes.get(); }
    public static boolean vegetationEnabled() { return vegetation != null && vegetation.get(); }
    public static double vegetationChance() { return chance == null ? 0.0 : chance.get(); }
    public static int maximumVegetationWrites() { return writes == null ? 0 : writes.get(); }
}
