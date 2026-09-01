package com.thunder.wildernessodysseyapi.environment.glacial;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.ModTags;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Optional;

/** Public biome-family boundary for glacial generation, runtime effects, and future content hooks. */
public final class GlacialBiomeManager {

    private static final List<Family> COAST_TO_INTERIOR = List.of(
            Family.ICEBERG_COAST,
            Family.MELTWATER_VALLEY,
            Family.GLACIAL_BASIN,
            Family.GLACIAL_HIGHLANDS,
            Family.POLAR_ICE_SHEET
    );

    private GlacialBiomeManager() {
    }

    /** Logs the stable family order and effective generation master switch once during common setup. */
    public static void bootstrap() {
        ModConstants.LOGGER.info(
                "Polar glacial biome system {} with {} connected biome families.",
                GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get() ? "enabled" : "disabled",
                COAST_TO_INTERIOR.size()
        );
        ModConstants.LOGGER.info(
                "Glacial season compatibility source: {}.",
                GlacialSeasonManager.calendarSource()
        );
    }

    /** Returns the intentional ocean-to-interior transition order. */
    public static List<Family> coastToInterior() {
        return COAST_TO_INTERIOR;
    }

    /** Returns whether a biome participates in any glacial system. */
    public static boolean isGlacial(Holder<Biome> biome) {
        return biome != null && (biome.is(ModTags.Biomes.IS_GLACIAL)
                || environmentalFamily(biome).isPresent());
    }

    /**
     * Resolves environmental behavior for stable family biomes and coastal satellites.
     *
     * <p>The authored glacial beach behaves like the iceberg coast for tint,
     * ambience, and seasonal exposure without changing the stable five-biome
     * coast-to-interior exploration family.</p>
     */
    public static Optional<Family> environmentalFamily(Holder<Biome> biome) {
        if (biome == null) {
            return Optional.empty();
        }
        return biome.unwrapKey().flatMap(GlacialBiomeManager::environmentalFamily);
    }

    /** Resolves environmental behavior from an exact registered biome key. */
    public static Optional<Family> environmentalFamily(ResourceKey<Biome> key) {
        Optional<Family> stableFamily = family(key);
        if (stableFamily.isPresent()) {
            return stableFamily;
        }
        return ModBiomes.GLACIAL_BEACH_KEY.equals(key)
                ? Optional.of(Family.ICEBERG_COAST)
                : Optional.empty();
    }

    /** Resolves the exact family without relying on optional worldgen-mod classes. */
    public static Optional<Family> family(Holder<Biome> biome) {
        if (biome == null) {
            return Optional.empty();
        }
        return biome.unwrapKey().flatMap(GlacialBiomeManager::family);
    }

    /** Resolves a registered biome key into its glacial family. */
    public static Optional<Family> family(ResourceKey<Biome> key) {
        if (ModBiomes.ICEBERG_COAST_KEY.equals(key)) {
            return Optional.of(Family.ICEBERG_COAST);
        }
        if (ModBiomes.GLACIAL_MELTWATER_VALLEY_KEY.equals(key)) {
            return Optional.of(Family.MELTWATER_VALLEY);
        }
        if (ModBiomes.POLAR_GLACIAL_BASIN_KEY.equals(key)) {
            return Optional.of(Family.GLACIAL_BASIN);
        }
        if (ModBiomes.GLACIAL_HIGHLANDS_KEY.equals(key)) {
            return Optional.of(Family.GLACIAL_HIGHLANDS);
        }
        if (ModBiomes.POLAR_ICE_SHEET_KEY.equals(key)) {
            return Optional.of(Family.POLAR_ICE_SHEET);
        }
        return Optional.empty();
    }

    /** Stable family identity exposed for exploration systems and future structure placement. */
    public enum Family {
        ICEBERG_COAST(0, ModBiomes.ICEBERG_COAST_KEY),
        MELTWATER_VALLEY(1, ModBiomes.GLACIAL_MELTWATER_VALLEY_KEY),
        GLACIAL_BASIN(2, ModBiomes.POLAR_GLACIAL_BASIN_KEY),
        GLACIAL_HIGHLANDS(3, ModBiomes.GLACIAL_HIGHLANDS_KEY),
        POLAR_ICE_SHEET(4, ModBiomes.POLAR_ICE_SHEET_KEY);

        private final int inlandRank;
        private final ResourceKey<Biome> biomeKey;

        Family(int inlandRank, ResourceKey<Biome> biomeKey) {
            this.inlandRank = inlandRank;
            this.biomeKey = biomeKey;
        }

        /** Zero at the ocean edge and increasing toward the continental interior. */
        public int inlandRank() {
            return inlandRank;
        }

        /** Registered biome key owned by this family member. */
        public ResourceKey<Biome> biomeKey() {
            return biomeKey;
        }
    }
}
