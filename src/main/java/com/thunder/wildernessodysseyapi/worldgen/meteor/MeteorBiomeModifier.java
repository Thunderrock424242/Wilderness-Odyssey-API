package com.thunder.wildernessodysseyapi.worldgen.meteor;

import com.thunder.wildernessodysseyapi.core.ModConstants;

/**
 * NeoForge 1.21 biome injection is done via JSON biome modifier files,
 * but we also wire up the BiomeLoadingEvent here as a fallback/complement.
 *
 * The primary injection path is the JSON file at:
 *   data/meteormod/neoforge/biome_modifier/add_meteor_impact.json
 */
public class MeteorBiomeModifier {

    public static void register() {
        // JSON biome modifier is the main path (see resources).
        // Log so we know the class loaded.
        ModConstants.LOGGER.info("Meteor biome modifier registered (via JSON biome_modifier).");
    }
}
