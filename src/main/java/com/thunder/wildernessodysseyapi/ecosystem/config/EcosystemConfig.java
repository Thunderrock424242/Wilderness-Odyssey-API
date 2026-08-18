package com.thunder.wildernessodysseyapi.ecosystem.config;

import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server configuration for the bounded living-ecosystem simulation.
 *
 * <p>Behavior-tag assignments are the primary modpack-facing setup. Optional
 * species JSON files remain available as a fine-grained fallback, while this
 * config also provides pack-wide safety limits and rate multipliers.</p>
 */
public final class EcosystemConfig {

    private static final List<String> DEFAULT_BEHAVIOR_ASSIGNMENTS = List.of(
            "minecraft:cow=herbivore",
            "minecraft:sheep=herbivore",
            "minecraft:pig=omnivore",
            "minecraft:chicken=bird",
            "minecraft:wolf=wolf"
    );

    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue SIMULATION_ZONES_ENABLED;
    public static final ModConfigSpec.IntValue REGIONAL_UPDATE_INTERVAL;
    public static final ModConfigSpec.IntValue MAX_REGION_UPDATES_PER_TICK;
    public static final ModConfigSpec.IntValue ENTITY_TRANSITION_RATE;
    public static final ModConfigSpec.IntValue BEHAVIOR_UPDATE_FREQUENCY;
    public static final ModConfigSpec.IntValue FAR_ANIMAL_UPDATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue FAR_ANIMAL_DISTANCE;
    public static final ModConfigSpec.IntValue NEAR_ANIMAL_DISTANCE;
    public static final ModConfigSpec.IntValue DISTANT_ANIMAL_DISTANCE;
    public static final ModConfigSpec.IntValue DISTANT_ANIMAL_UPDATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue DORMANT_ANIMAL_UPDATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAXIMUM_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue THIRST_RATE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue WEATHER_SHELTER_ENABLED;
    public static final ModConfigSpec.BooleanValue PRE_STORM_REACTIONS_ENABLED;
    public static final ModConfigSpec.BooleanValue PREDATOR_HUNTING_ENABLED;
    public static final ModConfigSpec.BooleanValue HERD_BEHAVIOR_ENABLED;
    public static final ModConfigSpec.BooleanValue GROUP_AI_ENABLED;
    public static final ModConfigSpec.IntValue MAX_GROUP_SIZE;
    public static final ModConfigSpec.IntValue LEADER_DECISION_INTERVAL;
    public static final ModConfigSpec.IntValue MEMBER_VALIDATION_INTERVAL;
    public static final ModConfigSpec.DoubleValue FOLLOW_DISTANCE;
    public static final ModConfigSpec.DoubleValue GROUP_FORMATION_RADIUS;
    public static final ModConfigSpec.IntValue MAXIMUM_EXPENSIVE_EVALUATIONS_PER_TICK;
    public static final ModConfigSpec.DoubleValue DISTURBANCE_DECAY_PER_DAY;
    public static final ModConfigSpec.DoubleValue MOVEMENT_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue PLAYER_ACTIVITY_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue COMBAT_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue EXPLOSION_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue FIRE_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue LIGHTNING_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue SEVERE_WEATHER_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue FLOOD_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue DROUGHT_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue METEOR_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue RADIATION_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue RIFTFALL_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue MAXIMUM_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue DISTURBANCE_CLEANUP_THRESHOLD;
    public static final ModConfigSpec.DoubleValue WILDLIFE_MILD_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue WILDLIFE_REDUCED_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue WILDLIFE_STRONG_AVOIDANCE_DISTURBANCE;
    public static final ModConfigSpec.DoubleValue WILDLIFE_MILD_SPAWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WILDLIFE_REDUCED_SPAWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WILDLIFE_STRONG_SPAWN_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue DEBUG_COMMANDS_ENABLED;
    public static final ModConfigSpec.BooleanValue AUTO_DETECT_MODDED_ANIMALS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BEHAVIOR_TAG_ASSIGNMENTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPECIES_BEHAVIOR_MULTIPLIERS;
    public static final ModConfigSpec.BooleanValue ENABLE_DISTANT_WILDLIFE;
    public static final ModConfigSpec.IntValue REAL_ENTITY_DISTANCE;
    public static final ModConfigSpec.IntValue DISTANT_WILDLIFE_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_DISTANT_GROUPS;
    public static final ModConfigSpec.IntValue MAX_REPRESENTED_ANIMALS;
    public static final ModConfigSpec.IntValue DISTANT_WILDLIFE_UPDATE_INTERVAL;
    public static final ModConfigSpec.IntValue TRANSITION_BUFFER;

    private static volatile BehaviorTagRules cachedBehaviorTagRules = BehaviorTagRules.EMPTY;
    private static volatile Map<ResourceLocation, Double> cachedSpeciesMultipliers = Map.of();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Server-authoritative, budgeted animal ecosystem behavior.")
                .push("ecosystem");

        ENABLED = builder
                .comment("Master switch for all Wilderness Odyssey ecosystem behavior.")
                .define("enabled", true);
        SIMULATION_ZONES_ENABLED = builder
                .comment(
                        "Use coarse nearest-player simulation cells and persistent abstract wildlife groups.",
                        "Disabling this leaves every loaded animal real and restores the legacy per-entity slowdown."
                )
                .define("simulationZonesEnabled", true);
        REGIONAL_UPDATE_INTERVAL = builder
                .comment("Ticks between player-coverage rebuilds and loaded-wildlife transition scans.")
                .defineInRange("regionalUpdateInterval", 40, 1, 1_200);
        MAX_REGION_UPDATES_PER_TICK = builder
                .comment("Maximum coarse cells allowed to change level or run one lazy update per dimension tick.")
                .defineInRange("maxRegionUpdatesPerTick", 16, 1, 4_096);
        ENTITY_TRANSITION_RATE = builder
                .comment(
                        "Maximum real-to-abstract or abstract-to-real wildlife transitions per dimension tick.",
                        "Low values prevent mass spawning after teleports, flight, and dimension changes."
                )
                .defineInRange("entityTransitionRate", 2, 1, 256);
        BEHAVIOR_UPDATE_FREQUENCY = builder
                .comment("Minimum ticks between environmental evaluations for animals near players.")
                .defineInRange("behaviorUpdateFrequencyTicks", 40, 10, 1_200);
        FAR_ANIMAL_UPDATE_MULTIPLIER = builder
                .comment("Evaluation interval multiplier for NEAR animals beyond the active distance.")
                .defineInRange("farAnimalUpdateMultiplier", 6, 1, 40);
        FAR_ANIMAL_DISTANCE = builder
                .comment(
                        "Maximum nearest-player distance for ACTIVE cells with full ecosystem entity behavior.",
                        "The public config key is activeRadius; the Java field keeps its historical name for compatibility."
                )
                .defineInRange("activeRadius", 96, 16, 4_096);
        NEAR_ANIMAL_DISTANCE = builder
                .comment("Maximum player distance for NEAR animals. They use full decisions at a reduced frequency.")
                .defineInRange("nearRadius", 224, 32, 8_192);
        DISTANT_ANIMAL_DISTANCE = builder
                .comment("Maximum player distance for DISTANT schedule-only simulation. Beyond this, environmental AI is dormant.")
                .defineInRange("distantRadius", 512, 64, 16_384);
        DISTANT_ANIMAL_UPDATE_MULTIPLIER = builder
                .comment("Schedule-only decision interval multiplier for DISTANT animals.")
                .defineInRange("distantAnimalUpdateMultiplier", 30, 2, 240);
        DORMANT_ANIMAL_UPDATE_MULTIPLIER = builder
                .comment("Wake-check interval multiplier for DORMANT animals. No environmental searches or paths run in this tier.")
                .defineInRange("dormantAnimalUpdateMultiplier", 120, 10, 1_200);
        MAXIMUM_SEARCH_RADIUS = builder
                .comment("Hard cap applied to every profile's water, shelter, threat, herd, food, and prey search radius.")
                .defineInRange("maximumSearchRadius", 32, 8, 64);
        THIRST_RATE_MULTIPLIER = builder
                .comment("Global multiplier for profile-defined thirst accumulation. Zero pauses thirst growth.")
                .defineInRange("thirstRateMultiplier", 1.0, 0.0, 10.0);
        WEATHER_SHELTER_ENABLED = builder
                .comment("Allow profiled animals to seek cover during localized hazardous weather.")
                .define("weatherShelterEnabled", true);
        PRE_STORM_REACTIONS_ENABLED = builder
                .comment(
                        "Allow profiled animals to sense significant localized weather before it arrives.",
                        "Light rain never causes a dramatic pre-storm response; herd and flock leaders share one cached decision."
                )
                .define("preStormReactionsEnabled", true);
        PREDATOR_HUNTING_ENABLED = builder
                .comment("Allow hunger-gated ecosystem predator hunts. Vanilla targeting remains otherwise intact.")
                .define("predatorHuntingEnabled", true);
        HERD_BEHAVIOR_ENABLED = builder
                .comment("Allow profiled herd animals to regroup when isolated.")
                .define("herdBehaviorEnabled", true);
        GROUP_AI_ENABLED = builder
                .comment(
                        "Allow social profiles to cache transient groups with one expensive-decision leader.",
                        "Followers retain vanilla/modded goals and use cheap relative movement unless catch-up pathfinding is required."
                )
                .define("groupAIEnabled", true);
        MAX_GROUP_SIZE = builder
                .comment("Maximum cached members in one local herd, flock, or pack.")
                .defineInRange("maxGroupSize", 16, 2, 64);
        LEADER_DECISION_INTERVAL = builder
                .comment("Minimum ticks between a group's leader-owned broad environmental decisions.")
                .defineInRange("leaderDecisionInterval", 60, 10, 1_200);
        MEMBER_VALIDATION_INTERVAL = builder
                .comment("Ticks between leader-owned cached membership validation and local recruitment passes.")
                .defineInRange("memberValidationInterval", 200, 20, 2_400);
        FOLLOW_DISTANCE = builder
                .comment("Baseline distance at which an idle follower starts catching up to its leader.")
                .defineInRange("followDistance", 8.0, 2.0, 32.0);
        GROUP_FORMATION_RADIUS = builder
                .comment("Loose radius used for stable randomized follower offsets around a moving leader.")
                .defineInRange("groupFormationRadius", 6.0, 1.0, 24.0);
        MAXIMUM_EXPENSIVE_EVALUATIONS_PER_TICK = builder
                .comment("Maximum animals across the server allowed to run block/entity searches in one server tick.")
                .defineInRange("maximumExpensiveAiEvaluationsPerTick", 24, 1, 512);

        // Regional memory uses lazy elapsed-time decay; none of these settings creates tick work.
        builder.comment(
                "Persistent chunk-sized environmental memory used by wildlife and future simulations.",
                "All activity values are normalized and decay only when a cell is accessed."
        ).push("environmentalMemory");
        DISTURBANCE_DECAY_PER_DAY = builder
                .comment("Linear disturbance/activity decay applied per 24,000 game-time ticks.")
                .defineInRange("disturbanceDecayPerDay", 0.20, 0.0, 1.0);
        MOVEMENT_DISTURBANCE = builder
                .comment("Disturbance added when a moving player passes the rate-limited traffic sampler.")
                .defineInRange("movementDisturbance", 0.006, 0.0, 1.0);
        PLAYER_ACTIVITY_DISTURBANCE = builder
                .comment("Disturbance added by direct player activity such as breaking or placing a block.")
                .defineInRange("playerActivityDisturbance", 0.03, 0.0, 1.0);
        COMBAT_DISTURBANCE = builder
                .comment("Base disturbance added by a successful living-entity damage event.")
                .defineInRange("combatDisturbance", 0.15, 0.0, 1.0);
        EXPLOSION_DISTURBANCE = builder
                .comment("Base disturbance added by an explosion; large blasts scale this amount up to the configured maximum.")
                .defineInRange("explosionDisturbance", 0.30, 0.0, 1.0);
        FIRE_DISTURBANCE = builder
                .comment("Disturbance added when a Wilderness weather wildfire successfully ignites.")
                .defineInRange("fireDisturbance", 0.12, 0.0, 1.0);
        LIGHTNING_DISTURBANCE = builder
                .comment("Disturbance added after localized weather successfully spawns real lightning.")
                .defineInRange("lightningDisturbance", 0.18, 0.0, 1.0);
        SEVERE_WEATHER_DISTURBANCE = builder
                .comment("Regional disturbance published by mature tornadoes and cyclones.")
                .defineInRange("severeWeatherDisturbance", 0.16, 0.0, 1.0);
        FLOOD_DISTURBANCE = builder
                .comment("Disturbance added when a player-relevant watershed enters active flooding.")
                .defineInRange("floodDisturbance", 0.24, 0.0, 1.0);
        DROUGHT_DISTURBANCE = builder
                .comment("Regional pressure published when retained vegetation drought becomes severe.")
                .defineInRange("droughtDisturbance", 0.10, 0.0, 1.0);
        METEOR_DISTURBANCE = builder
                .comment("Disturbance added only after a meteor crater is successfully generated and persisted.")
                .defineInRange("meteorDisturbance", 0.90, 0.0, 1.0);
        RADIATION_DISTURBANCE = builder
                .comment("Persistent habitat pressure exposed around active meteor radiation zones.")
                .defineInRange("radiationDisturbance", 0.30, 0.0, 1.0);
        RIFTFALL_DISTURBANCE = builder
                .comment("Disturbance published when a dimension enters an active Riftfall stage.")
                .defineInRange("riftfallDisturbance", 0.75, 0.0, 1.0);
        MAXIMUM_DISTURBANCE = builder
                .comment("Maximum normalized value retained for disturbance and activity channels.")
                .defineInRange("maximumDisturbance", 1.0, 0.05, 1.0);
        DISTURBANCE_CLEANUP_THRESHOLD = builder
                .comment("Cells whose decayed activity channels are all at or below this value are removed.")
                .defineInRange("cleanupThreshold", 0.0025, 0.0, 0.05);

        builder.comment(
                "Wildlife response bands. Keep mild < reduced < strong for the intended progression.",
                "Spawn multipliers never reach zero, so disturbance cannot completely disable wildlife."
        ).push("wildlifeResponse");
        WILDLIFE_MILD_DISTURBANCE = builder
                .comment("Beginning of the mildly cautious wildlife band.")
                .defineInRange("mildThreshold", 0.25, 0.0, 1.0);
        WILDLIFE_REDUCED_DISTURBANCE = builder
                .comment("Beginning of the reduced wildlife activity band.")
                .defineInRange("reducedThreshold", 0.50, 0.0, 1.0);
        WILDLIFE_STRONG_AVOIDANCE_DISTURBANCE = builder
                .comment("Beginning of strong avoidance and active wild-animal retreat.")
                .defineInRange("strongAvoidanceThreshold", 0.75, 0.0, 1.0);
        WILDLIFE_MILD_SPAWN_MULTIPLIER = builder
                .comment("Natural wildlife spawn chance at the mild threshold.")
                .defineInRange("mildSpawnMultiplier", 0.85, 0.05, 1.0);
        WILDLIFE_REDUCED_SPAWN_MULTIPLIER = builder
                .comment("Natural wildlife spawn chance at the reduced threshold.")
                .defineInRange("reducedSpawnMultiplier", 0.55, 0.05, 1.0);
        WILDLIFE_STRONG_SPAWN_MULTIPLIER = builder
                .comment("Minimum natural wildlife spawn chance at maximum disturbance.")
                .defineInRange("strongSpawnMultiplier", 0.25, 0.05, 1.0);
        builder.pop();
        builder.pop();

        DEBUG_COMMANDS_ENABLED = builder
                .comment("Enable operator-only /woecosystem diagnostics. Disabled by default for production servers.")
                .define("debugCommandsEnabled", false);
        AUTO_DETECT_MODDED_ANIMALS = builder
                .comment(
                        "Automatically give conservative ecosystem profiles to compatible non-Minecraft animals that have no config or JSON profile.",
                        "Third-party subclasses of vanilla cows, sheep, rabbits, horses, pigs, chickens, and wolves inherit that known family archetype.",
                        "Animal subclasses receive a neutral animal profile, FlyingAnimal implementations receive bird behavior,",
                        "WaterAnimal subclasses receive aquatic behavior, and animals with attack-target AI are marked as predators.",
                        "Hostile Enemy mobs and unknown PathfinderMob subclasses are never auto-detected. Use entity=disabled to exclude a false positive."
                )
                .define("autoDetectModdedAnimals", true);
        BEHAVIOR_TAG_ASSIGNMENTS = builder
                .comment(
                        "Assign behavior archetypes to exact entity IDs or #entity_type_tags.",
                        "Examples: minecraft:cow=herbivore, minecraft:wolf=wolf, #c:animals/birds=bird,flock.",
                        "Available tags: animal, herbivore, omnivore, bird, wolf, aquatic, herd, flock, pack, prey, predator, swimmer, diurnal, nocturnal, crepuscular, flexible, shelter, solitary, disabled.",
                        "Exact entity assignments override broader #entity_type_tag assignments."
                )
                .defineListAllowEmpty(
                        "behaviorTagAssignments",
                        DEFAULT_BEHAVIOR_ASSIGNMENTS,
                        () -> "examplemod:deer=herbivore,herd",
                        BehaviorTagRules::isValid
                );
        SPECIES_BEHAVIOR_MULTIPLIERS = builder
                .comment("Optional entity behavior-rate overrides written as namespace:id=multiplier. Zero disables that species.")
                .defineListAllowEmpty(
                        "speciesBehaviorMultipliers",
                        List.<String>of(),
                        () -> "minecraft:cow=1.0",
                        EcosystemConfig::isSpeciesOverride
                );

        // Distant wildlife is a population-level extension of the ecosystem,
        // so its server authority and transition distances live in this spec.
        builder.comment("Lightweight, server-authoritative distant wildlife population representation.")
                .push("distantWildlife");
        ENABLE_DISTANT_WILDLIFE = builder
                .comment("Enable abstract distant groups, client synchronization, and real-entity transitions.")
                .define("enableDistantWildlife", true);
        REAL_ENTITY_DISTANCE = builder
                .comment("Distance in blocks where wildlife should be represented by real Minecraft entities.")
                .defineInRange("realEntityDistance", 96, 32, 512);
        DISTANT_WILDLIFE_DISTANCE = builder
                .comment("Maximum distance in blocks at which abstract wildlife may be sent and rendered.")
                .defineInRange("distantWildlifeDistance", 512, 96, 2_048);
        MAX_DISTANT_GROUPS = builder
                .comment("Maximum persisted abstract wildlife groups in each dimension.")
                .defineInRange("maxDistantGroups", 64, 1, 256);
        MAX_REPRESENTED_ANIMALS = builder
                .comment("Maximum animals represented by all abstract groups in each dimension.")
                .defineInRange("maxRepresentedAnimals", 512, 1, 4_096);
        DISTANT_WILDLIFE_UPDATE_INTERVAL = builder
                .comment("Ticks between group movement, absorption scans, and full client snapshots.")
                .defineInRange("updateInterval", 100, 20, 1_200);
        TRANSITION_BUFFER = builder
                .comment("Cross-fade and early-materialization band outside realEntityDistance, in blocks.")
                .defineInRange("transitionBuffer", 32, 8, 256);
        builder.pop();

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private EcosystemConfig() {
    }

    /** Rebuilds the immutable per-species override map after config load or reload. */
    public static void reload() {
        cachedBehaviorTagRules = BehaviorTagRules.parse(
                BEHAVIOR_TAG_ASSIGNMENTS.get().stream().map(String::valueOf).toList());
        Map<ResourceLocation, Double> parsed = new HashMap<>();
        for (String entry : SPECIES_BEHAVIOR_MULTIPLIERS.get().stream().map(String::valueOf).toList()) {
            int separator = entry.indexOf('=');
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, separator).trim());
            double multiplier = Double.parseDouble(entry.substring(separator + 1).trim());
            parsed.put(id, Math.max(0.0, Math.min(10.0, multiplier)));
        }
        cachedSpeciesMultipliers = Map.copyOf(parsed);
    }

    /** Returns the configured behavior-rate multiplier for an entity type. */
    public static double speciesMultiplier(ResourceLocation entityType) {
        return cachedSpeciesMultipliers.getOrDefault(entityType, 1.0);
    }

    /** Returns the configured behavior labels for a runtime entity type. */
    public static Optional<Set<AnimalBehaviorTag>> behaviorTagsFor(PathfinderMob animal) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        return cachedBehaviorTagRules.resolve(
                entityId,
                tagId -> animal.getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, tagId))
        );
    }

    /** Returns parsed config assignments for diagnostics. */
    public static List<BehaviorTagRules.Rule> behaviorTagRules() {
        return cachedBehaviorTagRules.rules();
    }

    /** Returns how many exact or entity-type-tag behavior selectors are configured. */
    public static int behaviorTagAssignmentCount() {
        return cachedBehaviorTagRules.size();
    }

    /** Returns one relationally valid snapshot of the distant-wildlife limits. */
    public static DistantWildlifeSettings distantWildlifeSettings() {
        int realDistance = REAL_ENTITY_DISTANCE.get();
        int transitionBuffer = TRANSITION_BUFFER.get();
        int distantDistance = Math.max(
                DISTANT_WILDLIFE_DISTANCE.get(),
                realDistance + transitionBuffer
        );
        return new DistantWildlifeSettings(
                ENABLED.get() && ENABLE_DISTANT_WILDLIFE.get(),
                realDistance,
                distantDistance,
                MAX_DISTANT_GROUPS.get(),
                MAX_REPRESENTED_ANIMALS.get(),
                DISTANT_WILDLIFE_UPDATE_INTERVAL.get(),
                transitionBuffer
        );
    }

    private static boolean isSpeciesOverride(Object value) {
        if (!(value instanceof String entry)) {
            return false;
        }
        int separator = entry.indexOf('=');
        if (separator <= 0 || separator == entry.length() - 1) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, separator).trim().toLowerCase(Locale.ROOT));
        if (id == null) {
            return false;
        }
        try {
            double multiplier = Double.parseDouble(entry.substring(separator + 1).trim());
            return Double.isFinite(multiplier) && multiplier >= 0.0 && multiplier <= 10.0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /** Immutable effective settings shared by server transitions and payload headers. */
    public record DistantWildlifeSettings(
            boolean enabled,
            int realEntityDistance,
            int distantWildlifeDistance,
            int maximumGroups,
            int maximumRepresentedAnimals,
            int updateInterval,
            int transitionBuffer
    ) {
    }
}
