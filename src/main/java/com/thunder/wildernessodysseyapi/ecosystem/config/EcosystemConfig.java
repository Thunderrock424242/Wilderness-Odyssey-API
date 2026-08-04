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
    public static final ModConfigSpec.IntValue BEHAVIOR_UPDATE_FREQUENCY;
    public static final ModConfigSpec.IntValue FAR_ANIMAL_UPDATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue FAR_ANIMAL_DISTANCE;
    public static final ModConfigSpec.IntValue MAXIMUM_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue THIRST_RATE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue WEATHER_SHELTER_ENABLED;
    public static final ModConfigSpec.BooleanValue PREDATOR_HUNTING_ENABLED;
    public static final ModConfigSpec.BooleanValue HERD_BEHAVIOR_ENABLED;
    public static final ModConfigSpec.IntValue MAXIMUM_EXPENSIVE_EVALUATIONS_PER_TICK;
    public static final ModConfigSpec.BooleanValue DEBUG_COMMANDS_ENABLED;
    public static final ModConfigSpec.BooleanValue AUTO_DETECT_MODDED_ANIMALS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BEHAVIOR_TAG_ASSIGNMENTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPECIES_BEHAVIOR_MULTIPLIERS;

    private static volatile BehaviorTagRules cachedBehaviorTagRules = BehaviorTagRules.EMPTY;
    private static volatile Map<ResourceLocation, Double> cachedSpeciesMultipliers = Map.of();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Server-authoritative, budgeted animal ecosystem behavior.")
                .push("ecosystem");

        ENABLED = builder
                .comment("Master switch for all Wilderness Odyssey ecosystem behavior.")
                .define("enabled", true);
        BEHAVIOR_UPDATE_FREQUENCY = builder
                .comment("Minimum ticks between environmental evaluations for animals near players.")
                .defineInRange("behaviorUpdateFrequencyTicks", 40, 10, 1_200);
        FAR_ANIMAL_UPDATE_MULTIPLIER = builder
                .comment("Evaluation interval multiplier for animals farther than farAnimalDistance blocks from a player.")
                .defineInRange("farAnimalUpdateMultiplier", 6, 1, 40);
        FAR_ANIMAL_DISTANCE = builder
                .comment("Distance beyond which animals use the slower simulation interval.")
                .defineInRange("farAnimalDistance", 64, 16, 256);
        MAXIMUM_SEARCH_RADIUS = builder
                .comment("Hard cap applied to every profile's water, shelter, threat, herd, food, and prey search radius.")
                .defineInRange("maximumSearchRadius", 32, 8, 64);
        THIRST_RATE_MULTIPLIER = builder
                .comment("Global multiplier for profile-defined thirst accumulation. Zero pauses thirst growth.")
                .defineInRange("thirstRateMultiplier", 1.0, 0.0, 10.0);
        WEATHER_SHELTER_ENABLED = builder
                .comment("Allow profiled animals to seek cover during localized hazardous weather.")
                .define("weatherShelterEnabled", true);
        PREDATOR_HUNTING_ENABLED = builder
                .comment("Allow hunger-gated ecosystem predator hunts. Vanilla targeting remains otherwise intact.")
                .define("predatorHuntingEnabled", true);
        HERD_BEHAVIOR_ENABLED = builder
                .comment("Allow profiled herd animals to regroup when isolated.")
                .define("herdBehaviorEnabled", true);
        MAXIMUM_EXPENSIVE_EVALUATIONS_PER_TICK = builder
                .comment("Maximum animals across the server allowed to run block/entity searches in one server tick.")
                .defineInRange("maximumExpensiveAiEvaluationsPerTick", 24, 1, 512);
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
                        "Available tags: animal, herbivore, omnivore, bird, wolf, aquatic, herd, flock, pack, prey, predator, swimmer, nocturnal, shelter, solitary, disabled.",
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
}
