package com.thunder.wildernessodysseyapi.WorldGen.configurable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;

import java.util.HashMap;

/****
 * StructureConfig for the Wilderness Odyssey API mod.
 */
public class StructureConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    /** Debug toggle to skip meteor impact site placement */
    public static final ModConfigSpec.BooleanValue DEBUG_DISABLE_IMPACT_SITES;
    /** Emit detailed structure placement logs and retain a short history for debugging. */
    public static final ModConfigSpec.BooleanValue DEBUG_LOG_PLACEMENTS;
    /** Toggle for replacing terrain marker blocks with sampled terrain */
    public static final ModConfigSpec.BooleanValue ENABLE_TERRAIN_REPLACER;
    /** Warn when terrain replacer usage exceeds this fraction of the template volume */
    public static final ModConfigSpec.DoubleValue TERRAIN_REPLACER_WARNING_THRESHOLD;
    /** Maximum depth (blocks) the leveling marker may sit below the sampled surface; -1 disables clamping */
    public static final ModConfigSpec.IntValue MAX_LEVELING_DEPTH;
    /** Enable post-processing for Starter Structure bunkers using terrain replacer markers */
    public static final ModConfigSpec.BooleanValue ENABLE_STARTER_STRUCTURE_TERRAIN_REPLACER;
    /** Prevent hostile mob spawns inside the starter bunker immediately after placement */
    public static final ModConfigSpec.BooleanValue PREVENT_STARTER_STRUCTURE_HOSTILES;
    /** Horizontal search radius around the spawn bunker for terrain replacer markers */
    public static final ModConfigSpec.IntValue STARTER_STRUCTURE_SCAN_RADIUS;
    /** Vertical search range above and below the bunker origin for replacer markers */
    public static final ModConfigSpec.IntValue STARTER_STRUCTURE_SCAN_HEIGHT;
    /** Delay (ticks) after the starter structure is scheduled before applying the terrain replacer */
    public static final ModConfigSpec.IntValue STARTER_STRUCTURE_DELAY_TICKS;
    /** Additional layers of sampled terrain to place above the starter bunker roof to bury it */
    public static final ModConfigSpec.IntValue STARTER_STRUCTURE_EXTRA_COVER_DEPTH;
    /** Horizontal radius where hostile mobs may not spawn around the placed starter bunker */
    public static final ModConfigSpec.IntValue STARTER_STRUCTURE_SPAWN_DENY_RADIUS;
    /** Vertical half-height where hostile mobs may not spawn around the placed starter bunker */
    public static final ModConfigSpec.IntValue STARTER_STRUCTURE_SPAWN_DENY_HEIGHT;

    private static final HashMap<String, BooleanValue> STRUCTURES = new HashMap<>();
    private static final HashMap<String, BooleanValue> POIS = new HashMap<>();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("impactSites");
        DEBUG_DISABLE_IMPACT_SITES = BUILDER.comment(
                        "If true, meteor impact sites will not be generated."
                )
                .define("debugDisableImpactSites", false);
        BUILDER.pop();

        BUILDER.push("debug");
        DEBUG_LOG_PLACEMENTS = BUILDER.comment(
                        "If true, every structure placement attempt is recorded and logged for troubleshooting."
                )
                .define("debugLogPlacements", false);
        BUILDER.pop();

        BUILDER.push("placement");
        ENABLE_TERRAIN_REPLACER = BUILDER.comment(
                        "If false, terrain replacer markers are ignored and left as-is when structures are placed."
                )
                .define("enableTerrainReplacer", false);
        TERRAIN_REPLACER_WARNING_THRESHOLD = BUILDER.comment(
                        "Warn when more than this fraction of a template's volume is tagged as terrain replacer blocks."
                                + " Helps catch mistakenly-exported templates that would be overwritten by terrain."
                )
                .defineInRange("terrainReplacerWarningThreshold", 0.35D, 0.0D, 1.0D);
        MAX_LEVELING_DEPTH = BUILDER.comment(
                        "Maximum number of blocks the leveling marker may be placed below the sampled surface."
                                + " Prevents tall templates from being buried when the blue wool marker sits high above the"
                                + " intended ground contact point. Set to -1 to disable clamping."
                )
                .defineInRange("maxLevelingDepth", 12, -1, 256);
        ENABLE_STARTER_STRUCTURE_TERRAIN_REPLACER = BUILDER.comment(
                        "When true, Starter Structure bunkers that include wildernessodysseyapi:terrain_replacer markers"
                                + " will have those markers swapped for sampled surface blocks after generation."
                )
                .define("starterStructureTerrainReplacer", true);
        PREVENT_STARTER_STRUCTURE_HOSTILES = BUILDER.comment(
                        "When true, hostile mob spawns inside the starter bunker will be blocked after placement."
                )
                .define("starterStructurePreventHostiles", true);
        STARTER_STRUCTURE_SCAN_RADIUS = BUILDER.comment(
                        "Horizontal radius (in blocks) to scan around the starter bunker for terrain replacer markers."
                )
                .defineInRange("starterStructureScanRadius", 32, 1, 128);
        STARTER_STRUCTURE_SCAN_HEIGHT = BUILDER.comment(
                        "Vertical search range (in blocks up and down) to look for terrain replacer markers."
                )
                .defineInRange("starterStructureScanHeight", 16, 1, 128);
        STARTER_STRUCTURE_DELAY_TICKS = BUILDER.comment(
                        "Delay (in server ticks) after the starter structure is scheduled before applying the terrain replacer."
                                + " Gives the placer time to finish setting blocks."
                )
                .defineInRange("starterStructureDelayTicks", 10, 1, 200);
        STARTER_STRUCTURE_EXTRA_COVER_DEPTH = BUILDER.comment(
                        "Number of extra layers of sampled terrain to place over the starter bunker roof after generation."
                                + " Helps bury the oversized bunker under natural-looking ground."
                )
                .defineInRange("starterStructureExtraCoverDepth", 10, 0, 64);
        STARTER_STRUCTURE_SPAWN_DENY_RADIUS = BUILDER.comment(
                        "Horizontal radius (in blocks) around the starter bunker where hostile mob spawns are denied."
                )
                .defineInRange("starterStructureSpawnDenyRadius", 24, 1, 128);
        STARTER_STRUCTURE_SPAWN_DENY_HEIGHT = BUILDER.comment(
                        "Vertical half-height (in blocks up and down) where hostile mob spawns are denied around the starter bunker."
                )
                .defineInRange("starterStructureSpawnDenyHeight", 12, 1, 128);
        BUILDER.pop();

        registerAll();
        CONFIG_SPEC = BUILDER.build();
    }

    private static void registerAll() {
        // Structures
        BuiltInRegistries.STRUCTURE_TYPE.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location(); // Fixed: use .location()
            String modId = id.getNamespace();
            String structureName = id.getPath();

            BUILDER.push(modId);
            BUILDER.push("structures");
            STRUCTURES.put(id.toString(), BUILDER.define(structureName, true));
            BUILDER.pop(2);
        });

        // POIs
        BuiltInRegistries.POINT_OF_INTEREST_TYPE.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location(); // Fixed: use .location()
            String modId = id.getNamespace();
            String poiName = id.getPath();

            BUILDER.push(modId);
            BUILDER.push("pois");
            POIS.put(id.toString(), BUILDER.define(poiName, true));
            BUILDER.pop(2);
        });
    }

    public static boolean isStructureEnabled(ResourceLocation id) {
        BooleanValue value = STRUCTURES.get(id.toString());
        return value == null ? true : value.get(); // Fixed: No lambda, simple null-check
    }

    public static boolean isPOIEnabled(ResourceLocation id) {
        BooleanValue value = POIS.get(id.toString());
        return value == null ? true : value.get(); // Fixed: No lambda, simple null-check
    }
}
