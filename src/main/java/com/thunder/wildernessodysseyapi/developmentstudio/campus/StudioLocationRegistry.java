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

    /** Registers operational campus destinations and stable later-phase reservations. */
    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        register(location("main_hub", "Operations Hub", new BlockPos(32, 5, 27), true));
        register(location("structure_lab", "Structure Hangar", new BlockPos(17, 5, 32), true));
        register(location("water_lab", "Water Torture Lab", new BlockPos(48, 5, 32), true));
        register(location("entity_lab", "Entity Arena", new BlockPos(32, 5, 17), true));
        register(location("outdoor_test_area", "Outdoor Test Range", new BlockPos(32, 5, 48), true));
        register(location("ecosystem_lab", "Ecosystem Greenhouse", new BlockPos(16, 5, 11), true));
        register(location("weather_lab", "Weather Station", new BlockPos(48, 5, 11), true));
        register(location("worldgen_lab", "Worldgen Observatory", new BlockPos(11, 5, 48), true));

        // Reserved ids keep later campus expansions compatible without pretending
        // that their gameplay systems or finished buildings already exist.
        // The southeast systems wing is physically present, but remains sealed
        // until its real gameplay owners are implemented in later phases.
        register(location("lighting_lab", "Lighting Lab (Reserved)", new BlockPos(51, 5, 51), false));
        register(location("power_lab", "Power Lab (Reserved)", new BlockPos(56, 5, 51), false));
        register(location("security_lab", "Security Lab (Reserved)", new BlockPos(51, 5, 56), false));
        register(location("aether_lab", "Aether / Event Lab (Reserved)", new BlockPos(56, 5, 56), false));
        register(location("performance_lab", "Performance Lab (Reserved)", new BlockPos(53, 5, 53), false));
        register(location("scenario_lab", "Scenario Lab (Reserved)", new BlockPos(53, 5, 58), false));
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
