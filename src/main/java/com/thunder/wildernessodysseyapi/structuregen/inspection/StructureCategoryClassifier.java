package com.thunder.wildernessodysseyapi.structuregen.inspection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Name-based, explicitly heuristic material categories used by reference analysis. */
final class StructureCategoryClassifier {

    private final Map<String, Predicate<String>> categories = new LinkedHashMap<>();

    StructureCategoryClassifier() {
        categories.put("containers", path -> containsAny(path,
                "chest", "barrel", "shulker_box", "hopper", "dispenser", "dropper"));
        categories.put("lighting", path -> containsAny(path,
                "torch", "lantern", "glowstone", "sea_lantern", "shroomlight", "campfire",
                "candle", "end_rod", "froglight", "light"));
        categories.put("doors", path -> path.endsWith("_door") && !path.endsWith("_trapdoor"));
        categories.put("trapdoors", path -> path.endsWith("_trapdoor"));
        categories.put("stairs", path -> path.endsWith("_stairs"));
        categories.put("slabs", path -> path.endsWith("_slab"));
        categories.put("fences", path -> path.endsWith("_fence") || path.endsWith("_fence_gate"));
        categories.put("walls", path -> path.endsWith("_wall"));
        categories.put("glass", path -> path.contains("glass"));
        categories.put("redstone", path -> containsAny(path,
                "redstone", "repeater", "comparator", "piston", "observer", "lever", "button",
                "pressure_plate", "tripwire", "dispenser", "dropper", "hopper", "target",
                "daylight_detector", "sculk_sensor"));
        categories.put("signs", path -> path.endsWith("_sign") || path.endsWith("_hanging_sign"));
        categories.put("decorative", path -> containsAny(path,
                "flower", "plant", "potted", "banner", "skull", "head", "candle", "coral",
                "leaves", "sapling", "vine", "carpet", "chain", "bookshelf", "chiseled",
                "polished", "glazed", "moss"));
        categories.put("functional", path -> containsAny(path,
                "crafting_table", "furnace", "smoker", "blast_furnace", "anvil", "chest", "barrel",
                "ladder", "scaffolding", "bed", "beacon", "enchanting_table", "brewing_stand",
                "cauldron", "lectern", "loom", "smithing_table", "grindstone", "stonecutter",
                "respawn_anchor", "cartography_table", "fletching_table"));
    }

    Map<String, Long> classify(Map<String, Long> blockCounts) {
        Map<String, Long> totals = new LinkedHashMap<>();
        categories.forEach((name, predicate) -> {
            long count = blockCounts.entrySet().stream()
                    .filter(entry -> predicate.test(path(entry.getKey())))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            totals.put(name, count);
        });
        return totals;
    }

    private String path(String resourceLocation) {
        int separator = resourceLocation.indexOf(':');
        return separator < 0 ? resourceLocation : resourceLocation.substring(separator + 1);
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
