package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Thread-safe registry populated by server data-pack reloads.
 *
 * <p>Explicit entity selectors win over tag selectors. Profiles are sorted by
 * resource id so overlapping tag selectors remain deterministic.</p>
 */
public final class SpeciesBehaviorProfileManager {

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private SpeciesBehaviorProfileManager() {
    }

    /** Returns the active data-driven profile for an entity, if one exists. */
    public static Optional<SpeciesBehaviorProfile> profileFor(PathfinderMob animal) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        SpeciesBehaviorProfile explicit = snapshot.explicit().get(entityId);
        if (explicit != null) {
            return Optional.of(explicit);
        }
        for (SpeciesBehaviorProfile candidate : snapshot.tagged()) {
            for (ResourceLocation tagId : candidate.entityTags()) {
                if (animal.getType().builtInRegistryHolder().is(
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, tagId))) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** Returns all loaded profiles for diagnostics and extension validation. */
    public static List<SpeciesBehaviorProfile> profiles() {
        return snapshot.all();
    }

    /** Parses and atomically publishes one complete reload generation. */
    public static void apply(Map<ResourceLocation, JsonElement> resources) {
        List<Map.Entry<ResourceLocation, JsonElement>> ordered = resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .toList();
        Map<ResourceLocation, SpeciesBehaviorProfile> explicit = new HashMap<>();
        List<SpeciesBehaviorProfile> tagged = new ArrayList<>();
        List<SpeciesBehaviorProfile> all = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : ordered) {
            try {
                SpeciesBehaviorProfile profile = parse(entry.getKey(), entry.getValue().getAsJsonObject());
                all.add(profile);
                for (ResourceLocation entity : profile.entities()) {
                    SpeciesBehaviorProfile replaced = explicit.put(entity, profile);
                    if (replaced != null) {
                        LOGGER.warn("Ecosystem entity {} profile {} replaced {}", entity, profile.id(), replaced.id());
                    }
                }
                if (!profile.entityTags().isEmpty()) {
                    tagged.add(profile);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to load ecosystem profile {}: {}", entry.getKey(), exception.getMessage());
            }
        }

        snapshot = new Snapshot(Map.copyOf(explicit), List.copyOf(tagged), List.copyOf(all));
        LOGGER.info("Loaded {} ecosystem species profiles for {} explicit entity types", all.size(), explicit.size());
    }

    private static SpeciesBehaviorProfile parse(ResourceLocation id, JsonObject root) {
        Set<ResourceLocation> entities = locations(root.getAsJsonArray("entities"), "entities", id);
        Set<ResourceLocation> entityTags = locations(root.getAsJsonArray("entity_tags"), "entity_tags", id);
        if (entities.isEmpty() && entityTags.isEmpty()) {
            throw new IllegalArgumentException("requires at least one entities or entity_tags selector");
        }

        JsonObject needs = object(root, "needs");
        JsonObject drinking = object(root, "drinking");
        JsonObject shelter = object(root, "shelter");
        JsonObject herd = object(root, "herd");
        JsonObject prey = object(root, "prey");
        JsonObject predator = object(root, "predator");

        return new SpeciesBehaviorProfile(
                id,
                Set.copyOf(entities),
                Set.copyOf(entityTags),
                new SpeciesBehaviorProfile.Needs(
                        decimal(needs, "thirst_per_minute", 0.012, 0.0, 1.0),
                        decimal(needs, "hunger_per_minute", 0.006, 0.0, 1.0),
                        decimal(needs, "rest_per_minute", 0.004, 0.0, 1.0),
                        decimal(needs, "hot_temperature_celsius", 28.0, -20.0, 60.0),
                        decimal(needs, "heat_thirst_multiplier", 1.6, 1.0, 8.0),
                        decimal(needs, "activity_thirst_multiplier", 1.25, 1.0, 4.0),
                        bool(needs, "nocturnal", false)
                ),
                new SpeciesBehaviorProfile.Drinking(
                        bool(drinking, "enabled", true),
                        decimal(drinking, "thirst_threshold", 0.65, 0.05, 1.0),
                        integer(drinking, "search_radius", 20, 4, 64),
                        integer(drinking, "duration_ticks", 60, 20, 400),
                        decimal(drinking, "move_speed", 1.0, 0.1, 2.5),
                        decimal(drinking, "thirst_restored", 0.9, 0.05, 1.0),
                        bool(drinking, "can_swim", false),
                        decimal(drinking, "maximum_safe_depth", 1.0, 0.25, 8.0)
                ),
                new SpeciesBehaviorProfile.Shelter(
                        bool(shelter, "enabled", true),
                        integer(shelter, "search_radius", 18, 4, 64),
                        decimal(shelter, "precipitation_threshold", 0.35, 0.0, 1.0),
                        decimal(shelter, "thunder_threshold", 0.35, 0.0, 1.0),
                        decimal(shelter, "wind_threshold", 0.65, 0.0, 1.5),
                        integer(shelter, "minimum_release_delay_ticks", 80, 0, 2_400),
                        integer(shelter, "maximum_release_delay_ticks", 240, 0, 4_800),
                        decimal(shelter, "move_speed", 1.05, 0.1, 2.5)
                ),
                new SpeciesBehaviorProfile.Herd(
                        bool(herd, "enabled", false),
                        integer(herd, "search_radius", 16, 4, 64),
                        decimal(herd, "preferred_distance", 8.0, 2.0, 32.0),
                        decimal(herd, "motivation_threshold", 0.55, 0.0, 1.0),
                        decimal(herd, "move_speed", 0.9, 0.1, 2.5)
                ),
                new SpeciesBehaviorProfile.Prey(
                        bool(prey, "enabled", false),
                        integer(prey, "threat_radius", 16, 4, 64),
                        integer(prey, "threat_memory_ticks", 240, 20, 2_400),
                        integer(prey, "propagation_radius", 12, 0, 48),
                        decimal(prey, "flee_speed", 1.35, 0.1, 3.0),
                        List.copyOf(locations(prey.getAsJsonArray("threat_tags"), "prey.threat_tags", id))
                ),
                new SpeciesBehaviorProfile.Predator(
                        bool(predator, "enabled", false),
                        integer(predator, "hunt_radius", 20, 4, 64),
                        decimal(predator, "hunger_threshold", 0.78, 0.05, 1.0),
                        integer(predator, "hunt_cooldown_ticks", 12_000, 200, 72_000),
                        integer(predator, "minimum_nearby_prey", 4, 1, 32),
                        integer(predator, "attack_interval_ticks", 20, 10, 100),
                        decimal(predator, "move_speed", 1.15, 0.1, 2.5),
                        bool(predator, "wild_only", true),
                        List.copyOf(locations(predator.getAsJsonArray("prey_tags"), "predator.prey_tags", id))
                )
        );
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static Set<ResourceLocation> locations(JsonArray values, String field, ResourceLocation profileId) {
        Set<ResourceLocation> parsed = new LinkedHashSet<>();
        if (values == null) {
            return parsed;
        }
        for (JsonElement value : values) {
            ResourceLocation location = ResourceLocation.tryParse(value.getAsString());
            if (location == null) {
                throw new IllegalArgumentException(profileId + " has invalid " + field + " id " + value);
            }
            parsed.add(location);
        }
        return parsed;
    }

    private static boolean bool(JsonObject object, String name, boolean fallback) {
        return object.has(name) ? object.get(name).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject object, String name, int fallback, int minimum, int maximum) {
        int value = object.has(name) ? object.get(name).getAsInt() : fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double decimal(JsonObject object, String name, double fallback, double minimum, double maximum) {
        double value = object.has(name) ? object.get(name).getAsDouble() : fallback;
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Snapshot(
            Map<ResourceLocation, SpeciesBehaviorProfile> explicit,
            List<SpeciesBehaviorProfile> tagged,
            List<SpeciesBehaviorProfile> all
    ) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), List.of(), List.of());
    }
}
