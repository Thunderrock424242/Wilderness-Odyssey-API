package com.thunder.wildernessodysseyapi.meteor.worldgen;

/**
 * Legacy entry point retained for integrations compiled against older builds.
 *
 * <p>Meteor biome injection is entirely data-driven through
 * {@code data/neoforge/biome_modifier/add_meteor_impact.json}. Calling this
 * compatibility method is intentionally a no-op.</p>
 *
 * @deprecated biome modifiers are loaded from data packs and require no Java registration
 */
@Deprecated(forRemoval = true)
public final class MeteorBiomeModifier {

    private MeteorBiomeModifier() {
    }

    /**
     * Retains source and binary compatibility with the former registration hook.
     *
     * @deprecated no registration call is required for the data-driven feature
     */
    @Deprecated(forRemoval = true)
    public static void register() {
        // Intentionally empty: NeoForge discovers the JSON biome modifier.
    }
}
