package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.ecosystem.api.ActivityTime;
import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Thread-safe registry populated by server data-pack reloads.
 *
 * <p>Server-config behavior assignments are the primary easy path and override
 * JSON for matching entities. JSON explicit selectors then win over JSON tag
 * selectors, followed by profiles registered by compatibility modules.
 * Conservative third-party-animal inference is the final fallback, so
 * automatic compatibility never replaces pack-authored knowledge.</p>
 */
public final class SpeciesBehaviorProfileManager {

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static final Map<ConfiguredProfileKey, SpeciesBehaviorProfile> configuredProfiles =
            new ConcurrentHashMap<>();
    private static final Map<ConfiguredProfileKey, SpeciesBehaviorProfile> autoDetectedProfiles =
            new ConcurrentHashMap<>();
    private static volatile List<SpeciesBehaviorProfile> compatibilityProfiles = List.of();

    private SpeciesBehaviorProfileManager() {
    }

    /** Returns the active data-driven profile for an entity, if one exists. */
    public static Optional<SpeciesBehaviorProfile> profileFor(PathfinderMob animal) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        Optional<Set<AnimalBehaviorTag>> behaviorTags = EcosystemConfig.behaviorTagsFor(animal);
        if (behaviorTags.isPresent()) {
            if (behaviorTags.get().contains(AnimalBehaviorTag.DISABLED)) {
                return Optional.empty();
            }
            ConfiguredProfileKey key = new ConfiguredProfileKey(entityId, Set.copyOf(behaviorTags.get()));
            return Optional.of(configuredProfiles.computeIfAbsent(
                    key,
                    configured -> BehaviorTagProfileFactory.create(configured.entityId(), configured.behaviorTags())
            ));
        }
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
        for (SpeciesBehaviorProfile candidate : compatibilityProfiles) {
            if (candidate.entities().contains(entityId)) {
                return Optional.of(candidate);
            }
        }
        for (SpeciesBehaviorProfile candidate : compatibilityProfiles) {
            for (ResourceLocation tagId : candidate.entityTags()) {
                if (animal.getType().builtInRegistryHolder().is(
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, tagId))) {
                    return Optional.of(candidate);
                }
            }
        }
        if (EcosystemConfig.AUTO_DETECT_MODDED_ANIMALS.get()) {
            Optional<Set<AnimalBehaviorTag>> detected = ModdedMobBehaviorDetector.detect(animal);
            if (detected.isPresent()) {
                ConfiguredProfileKey key = new ConfiguredProfileKey(entityId, Set.copyOf(detected.get()));
                return Optional.of(autoDetectedProfiles.computeIfAbsent(key, inferred -> {
                    LOGGER.info("Auto-detected ecosystem behavior for modded animal {} as {}",
                            inferred.entityId(),
                            inferred.behaviorTags().stream()
                                    .map(AnimalBehaviorTag::serializedName)
                                    .sorted()
                                    .toList());
                    return BehaviorTagProfileFactory.createAutoDetected(
                            inferred.entityId(), inferred.behaviorTags());
                }));
            }
        }
        return Optional.empty();
    }

    /** Returns all loaded profiles for diagnostics and extension validation. */
    public static List<SpeciesBehaviorProfile> profiles() {
        return snapshot.all();
    }

    /** Returns runtime profiles generated for animals encountered since config load. */
    public static List<SpeciesBehaviorProfile> configuredProfiles() {
        return configuredProfiles.values().stream()
                .sorted(Comparator.comparing(profile -> profile.id().toString()))
                .toList();
    }

    /** Returns runtime profiles inferred for compatible third-party animals. */
    public static List<SpeciesBehaviorProfile> autoDetectedProfiles() {
        return autoDetectedProfiles.values().stream()
                .sorted(Comparator.comparing(profile -> profile.id().toString()))
                .toList();
    }

    /**
     * Registers a compatibility-module profile after config and data-pack profiles but before inference.
     *
     * <p>Call this during common setup. Registration is atomic, survives data-pack
     * reloads, and rejects duplicate profile IDs instead of silently replacing
     * another module's ownership.</p>
     */
    public static synchronized void registerCompatibilityProfile(SpeciesBehaviorProfile profile) {
        if (profile.entities().isEmpty() && profile.entityTags().isEmpty()) {
            throw new IllegalArgumentException("Compatibility profile requires an entity or entity-tag selector");
        }
        if (compatibilityProfiles.stream().anyMatch(candidate -> candidate.id().equals(profile.id()))) {
            throw new IllegalArgumentException("Duplicate ecosystem compatibility profile " + profile.id());
        }
        List<SpeciesBehaviorProfile> updated = new ArrayList<>(compatibilityProfiles);
        updated.add(profile);
        updated.sort(Comparator.comparing(candidate -> candidate.id().toString()));
        compatibilityProfiles = List.copyOf(updated);
    }

    /** Returns profiles registered directly by compatibility modules. */
    public static List<SpeciesBehaviorProfile> compatibilityProfiles() {
        return compatibilityProfiles;
    }

    /** Clears config-generated and inferred profiles so runtime settings rebuild on next lookup. */
    public static void clearConfiguredProfiles() {
        configuredProfiles.clear();
        autoDetectedProfiles.clear();
    }

    /** Parses and atomically publishes one complete reload generation. */
    public static void apply(Map<ResourceLocation, JsonElement> resources) {
        clearConfiguredProfiles();
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
        JsonObject environment = object(root, "environment");

        SpeciesBehaviorProfile base = new SpeciesBehaviorProfile(
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
        SpeciesBehaviorProfile.Environment defaults = base.environment();
        return new SpeciesBehaviorProfile(
                base.id(),
                base.entities(),
                base.entityTags(),
                base.needs(),
                base.drinking(),
                base.shelter(),
                base.herd(),
                base.prey(),
                base.predator(),
                new SpeciesBehaviorProfile.Environment(
                        activityTime(environment, defaults.activeTime(), id),
                        decimal(environment, "preferred_temperature_min_celsius",
                                defaults.preferredMinimumTemperatureCelsius(), -80.0, 60.0),
                        decimal(environment, "preferred_temperature_max_celsius",
                                defaults.preferredMaximumTemperatureCelsius(), -80.0, 60.0),
                        decimal(environment, "hot_dry_drink_threshold_reduction",
                                defaults.hotDryDrinkThresholdReduction(), 0.0, 0.75),
                        decimal(environment, "forage_hunger_threshold",
                                defaults.forageHungerThreshold(), 0.0, 1.0),
                        decimal(environment, "rest_threshold", defaults.restThreshold(), 0.0, 1.0),
                        decimal(environment, "minimum_food_for_forage",
                                defaults.minimumFoodForForage(), 0.0, 1.0),
                        integer(environment, "local_travel_radius", defaults.localTravelRadius(), 4, 32),
                        integer(environment, "migration_radius", defaults.migrationRadius(), 4, 64),
                        integer(environment, "schedule_jitter_ticks", defaults.scheduleJitterTicks(), 0, 4_000),
                        integer(environment, "rest_duration_ticks", defaults.restDurationTicks(), 20, 2_400),
                        integer(environment, "sleep_duration_ticks", defaults.sleepDurationTicks(), 40, 4_800),
                        behaviorStates(environment.getAsJsonArray("supported_states"), defaults.supportedStates(), id)
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

    private static String text(JsonObject object, String name, String fallback) {
        return object.has(name) ? object.get(name).getAsString() : fallback;
    }

    private static ActivityTime activityTime(
            JsonObject object,
            ActivityTime fallback,
            ResourceLocation profileId
    ) {
        if (!object.has("active_time")) {
            return fallback;
        }
        String value = text(object, "active_time", fallback.serializedName());
        ActivityTime parsed = ActivityTime.parse(value, null);
        if (parsed == null) {
            throw new IllegalArgumentException(profileId + " has invalid environment.active_time " + value);
        }
        return parsed;
    }

    private static Set<EcosystemBehaviorState> behaviorStates(
            JsonArray values,
            Set<EcosystemBehaviorState> fallback,
            ResourceLocation profileId
    ) {
        if (values == null) {
            return fallback;
        }
        EnumSet<EcosystemBehaviorState> states = EnumSet.noneOf(EcosystemBehaviorState.class);
        for (JsonElement value : values) {
            try {
                states.add(EcosystemBehaviorState.valueOf(value.getAsString().trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        profileId + " has invalid environment.supported_states value " + value);
            }
        }
        return states;
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

    private record ConfiguredProfileKey(
            ResourceLocation entityId,
            Set<AnimalBehaviorTag> behaviorTags
    ) {
    }
}
