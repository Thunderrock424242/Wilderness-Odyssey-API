package com.thunder.wildernessodysseyapi.worldgen.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Defines common structure-placement and point-of-interest settings.
 *
 * <p>NeoForge owns the generated config file through {@link #CONFIG_SPEC};
 * worldgen and placement systems read typed values instead of hardcoding
 * balance or debug behavior.</p>
 */
public final class StructureConfig {
    public static ModConfigSpec CONFIG_SPEC;

    /** Debug toggle to skip meteor impact site placement */
    public static ModConfigSpec.BooleanValue DEBUG_DISABLE_IMPACT_SITES;
    /** Emit detailed structure placement logs and retain a short history for debugging. */
    public static ModConfigSpec.BooleanValue DEBUG_LOG_PLACEMENTS;
    /** Fill gaps below structures even when no terrain markers are present. */
    public static ModConfigSpec.BooleanValue ENABLE_AUTO_TERRAIN_BLEND;
    /** Skip auto-blend in columns where the structure does not touch the template's bottom layer. */
    public static ModConfigSpec.BooleanValue ENABLE_SMART_AUTO_TERRAIN_BLEND;
    /** Maximum height (in blocks) to fill when auto-blending under structures. */
    public static ModConfigSpec.IntValue AUTO_TERRAIN_BLEND_MAX_DEPTH;
    /** Horizontal radius (in blocks) to feather terrain around placed structures. */
    public static ModConfigSpec.IntValue AUTO_TERRAIN_BLEND_RADIUS;
    /** Maximum depth (blocks) the leveling marker may sit below the sampled surface; -1 disables clamping */
    public static ModConfigSpec.IntValue MAX_LEVELING_DEPTH;
    /** Prevent hostile mob spawns inside the starter bunker immediately after placement */
    public static ModConfigSpec.BooleanValue PREVENT_STARTER_STRUCTURE_HOSTILES;
    /** Debug toggle to skip starter bunker placement and keep vanilla spawn selection. */
    public static ModConfigSpec.BooleanValue DEBUG_DISABLE_STARTER_BUNKER;
    /** Decorate the generated starter island as an overgrown jungle ruin. */
    public static ModConfigSpec.BooleanValue STARTER_ISLAND_JUNGLE_ENABLED;
    /** Relative tree and undergrowth density used by the starter island decorator. */
    public static ModConfigSpec.DoubleValue STARTER_ISLAND_JUNGLE_DENSITY;
    /** Horizontal radius where hostile mobs may not spawn around the placed starter bunker */
    public static ModConfigSpec.IntValue STARTER_STRUCTURE_SPAWN_DENY_RADIUS;
    /** Vertical half-height where hostile mobs may not spawn around the placed starter bunker */
    public static ModConfigSpec.IntValue STARTER_STRUCTURE_SPAWN_DENY_HEIGHT;
    /** Per-chunk placement chance for Secret Order villages in eligible jungle biomes. */
    public static ModConfigSpec.DoubleValue SECRET_ORDER_VILLAGE_SPAWN_CHANCE;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the structures category in the unified common config. */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("impactSites");
        DEBUG_DISABLE_IMPACT_SITES = builder.comment(
                        "If true, meteor impact sites will not be generated."
                )
                .define("debugDisableImpactSites", false);
        builder.pop();

        builder.push("debug");
        DEBUG_LOG_PLACEMENTS = builder.comment(
                        "If true, every structure placement attempt is recorded and logged for troubleshooting."
                )
                .define("debugLogPlacements", false);
        builder.pop();

        builder.push("placement");
        ENABLE_AUTO_TERRAIN_BLEND = builder.comment(
                        "If true, structure placement will attempt to blend terrain even when no terrain marker blocks exist."
                )
                .define("enableAutoTerrainBlend", true);
        ENABLE_SMART_AUTO_TERRAIN_BLEND = builder.comment(
                        "If true, auto-blend will skip columns where the structure does not touch the template's bottom layer."
                                + " Helps prevent terrain from filling structure interiors."
                )
                .define("enableSmartAutoTerrainBlend", true);
        AUTO_TERRAIN_BLEND_MAX_DEPTH = builder.comment(
                        "Maximum number of blocks to fill upward from the surface when auto-blending structures."
                )
                .defineInRange("autoTerrainBlendMaxDepth", 6, 1, 64);
        AUTO_TERRAIN_BLEND_RADIUS = builder.comment(
                        "Horizontal radius to feather terrain around structures when auto-blending is enabled."
                )
                .defineInRange("autoTerrainBlendRadius", 2, 0, 8);
        MAX_LEVELING_DEPTH = builder.comment(
                        "Maximum number of blocks the leveling marker may be placed below the sampled surface."
                                + " Prevents tall templates from being buried when the blue wool marker sits high above the"
                                + " intended ground contact point. Set to -1 to disable clamping."
                )
                .defineInRange("maxLevelingDepth", 12, -1, 256);
        PREVENT_STARTER_STRUCTURE_HOSTILES = builder.comment(
                        "When true, hostile mob spawns inside the starter bunker will be blocked after placement."
                )
                .define("starterStructurePreventHostiles", true);
        DEBUG_DISABLE_STARTER_BUNKER = builder.comment(
                        "If true, the starter bunker will not be placed and vanilla spawn selection will run instead."
                )
                .define("debugDisableStarterBunker", false);
        STARTER_ISLAND_JUNGLE_ENABLED = builder.comment(
                        "When true, the starter bunker island receives deterministic jungle trees, bamboo,"
                                + " undergrowth, mossy rocks, and an entrance path after placement."
                )
                .define("starterIslandJungleEnabled", true);
        STARTER_ISLAND_JUNGLE_DENSITY = builder.comment(
                        "Controls starter island tree and undergrowth density. 0 disables vegetation while 1"
                                + " produces the intended full jungle density."
                )
                .defineInRange("starterIslandJungleDensity", 0.75D, 0.0D, 1.0D);
        STARTER_STRUCTURE_SPAWN_DENY_RADIUS = builder.comment(
                        "Horizontal radius (in blocks) around the starter bunker where hostile mob spawns are denied."
                )
                .defineInRange("starterStructureSpawnDenyRadius", 24, 1, 128);
        STARTER_STRUCTURE_SPAWN_DENY_HEIGHT = builder.comment(
                        "Vertical half-height (in blocks up and down) where hostile mob spawns are denied around the starter bunker."
                )
                .defineInRange("starterStructureSpawnDenyHeight", 12, 1, 128);
        SECRET_ORDER_VILLAGE_SPAWN_CHANCE = builder.comment(
                        "Chance per eligible jungle chunk to attempt Secret Order village placement."
                )
                .defineInRange("secretOrderVillageSpawnChance", 0.001D, 0.0D, 1.0D);
        builder.pop();
    }

    private StructureConfig() {
    }

    /**
     * Compatibility method retained after removal of the non-functional registry-type switches.
     *
     * @param id the structure type resource location
     * @return always {@code true}; use a feature-owned setting or data-pack biome/tag control
     * @deprecated registry types cannot be safely disabled after registration
     */
    @Deprecated(forRemoval = false)
    public static boolean isStructureEnabled(ResourceLocation id) {
        return true;
    }

    /**
     * Compatibility method retained after removal of the non-functional registry-type switches.
     *
     * @param id the point-of-interest type resource location
     * @return always {@code true}; configure the owning feature or data pack instead
     * @deprecated registry types cannot be safely disabled after registration
     */
    @Deprecated(forRemoval = false)
    public static boolean isPoiEnabled(ResourceLocation id) {
        return true;
    }
}
