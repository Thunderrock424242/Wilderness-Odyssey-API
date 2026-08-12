package com.thunder.wildernessodysseyapi.developmentstudio.campus;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Internal extension registry for predictable Development Campus locations. */
public final class StudioLocationRegistry {
    private static final Map<ResourceLocation, StudioLocationDefinition> LOCATIONS = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private StudioLocationRegistry() {
    }

    /** Registers the Phase 1 pads and reserves stable ids for future campus labs. */
    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        register(location("main_hub", "Main Studio Hub", new BlockPos(10, 1, 10), true));
        register(location("structure_lab", "Structure Lab Pad", new BlockPos(3, 1, 10), true));
        register(location("water_lab", "Water Torture Lab Pad", new BlockPos(17, 1, 10), true));
        register(location("entity_lab", "Entity / Mob Lab Pad", new BlockPos(10, 1, 3), true));
        register(location("outdoor_test_area", "Outdoor Test Area", new BlockPos(10, 1, 17), true));

        // Reserved ids keep later campus expansions compatible without pretending
        // that their gameplay systems or finished buildings already exist.
        register(location("weather_lab", "Weather Lab (Reserved)", new BlockPos(10, 1, -18), false));
        register(location("ecosystem_lab", "Ecosystem Lab (Reserved)", new BlockPos(-18, 1, 10), false));
        register(location("worldgen_lab", "Worldgen Lab (Reserved)", new BlockPos(38, 1, 10), false));
        register(location("lighting_lab", "Lighting Lab (Reserved)", new BlockPos(10, 1, 38), false));
        register(location("power_lab", "Power Lab (Reserved)", new BlockPos(-18, 1, -18), false));
        register(location("security_lab", "Security Lab (Reserved)", new BlockPos(38, 1, -18), false));
        register(location("aether_lab", "Aether / Event Lab (Reserved)", new BlockPos(-18, 1, 38), false));
        register(location("performance_lab", "Performance Lab (Reserved)", new BlockPos(38, 1, 38), false));
        register(location("scenario_lab", "Scenario Lab (Reserved)", new BlockPos(10, 1, 58), false));
    }

    /** Registers one unique location definition. */
    public static synchronized void register(StudioLocationDefinition definition) {
        StudioLocationDefinition previous = LOCATIONS.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Studio location id: " + definition.id());
        }
    }

    public static Optional<StudioLocationDefinition> get(ResourceLocation id) {
        bootstrapDefaults();
        return Optional.ofNullable(LOCATIONS.get(id));
    }

    public static Collection<StudioLocationDefinition> values() {
        bootstrapDefaults();
        return List.copyOf(LOCATIONS.values());
    }

    private static StudioLocationDefinition location(String path, String name, BlockPos offset, boolean available) {
        return new StudioLocationDefinition(
                ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path),
                name,
                offset,
                available
        );
    }
}
