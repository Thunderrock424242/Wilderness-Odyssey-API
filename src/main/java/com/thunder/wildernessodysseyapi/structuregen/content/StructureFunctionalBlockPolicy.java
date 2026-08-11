package com.thunder.wildernessodysseyapi.structuregen.content;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Explicit opt-in policy for known Wilderness Odyssey gameplay blocks.
 *
 * <p>This deliberately contains only first-party blocks whose placement enables
 * or participates in a Wilderness Odyssey system. It is not a guessed catalog
 * of third-party behavior: third-party literal blocks use Blueprint usage intent,
 * while future integrations may contribute their own exact rules or tags.</p>
 */
public final class StructureFunctionalBlockPolicy {

    private static final String MOD_ID = "wildernessodysseyapi";
    private static final Map<ResourceLocation, String> REQUIRED_SYSTEMS = Map.ofEntries(
            rule("anomaly_gateway", "anomaly"),
            rule("cryo_tube", "cryo_spawn"),
            rule("rift_core", "temporal_rift"),
            rule("time_capsule", "temporal_rift"),
            rule("ancient_time_capsule", "temporal_rift"),
            rule("wilderness_water_block", "canonical_water")
    );

    private StructureFunctionalBlockPolicy() {
    }

    /** Returns the exact functional-system token required to author this known first-party block. */
    public static Optional<String> requiredSystem(ResourceLocation blockId) {
        return Optional.ofNullable(REQUIRED_SYSTEMS.get(blockId));
    }

    private static Map.Entry<ResourceLocation, String> rule(String blockPath, String systemPath) {
        return Map.entry(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, blockPath),
                MOD_ID + ":" + systemPath
        );
    }
}
