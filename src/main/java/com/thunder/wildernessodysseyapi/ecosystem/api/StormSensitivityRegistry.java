package com.thunder.wildernessodysseyapi.ecosystem.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public exact-entity registry for modded-animal storm sensitivity profiles.
 *
 * <p>Mods may register during common setup. Exact registrations override the
 * conservative runtime defaults inferred from an animal's established
 * ecosystem profile; they do not register entities or weather systems.</p>
 */
public final class StormSensitivityRegistry {

    private static final Map<ResourceLocation, StormSensitivity> PROFILES = new ConcurrentHashMap<>();

    private StormSensitivityRegistry() {
    }

    /** Registers or replaces one exact entity-type sensitivity profile. */
    public static void register(ResourceLocation entityTypeId, StormSensitivity sensitivity) {
        PROFILES.put(Objects.requireNonNull(entityTypeId, "entityTypeId"),
                Objects.requireNonNull(sensitivity, "sensitivity"));
    }

    /** Convenience overload for a registered Minecraft entity type. */
    public static void register(EntityType<?> entityType, StormSensitivity sensitivity) {
        register(BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entityType, "entityType")), sensitivity);
    }

    /** Returns one exact programmatic registration without applying defaults. */
    public static Optional<StormSensitivity> registered(ResourceLocation entityTypeId) {
        return Optional.ofNullable(PROFILES.get(entityTypeId));
    }

    /** Returns immutable registered entries in deterministic diagnostic order. */
    public static Map<ResourceLocation, StormSensitivity> profiles() {
        Map<ResourceLocation, StormSensitivity> ordered = new LinkedHashMap<>();
        PROFILES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
    }

    /** Resolves an exact registration or a conservative species-family default. */
    public static StormSensitivity resolve(PathfinderMob animal, SpeciesBehaviorProfile behaviorProfile) {
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        StormSensitivity exact = PROFILES.get(entityTypeId);
        if (exact != null) {
            return exact;
        }
        if (animal instanceof WaterAnimal || !behaviorProfile.shelter().enabled()) {
            return StormSensitivity.AQUATIC;
        }
        if (animal instanceof FlyingAnimal) {
            return StormSensitivity.BIRD;
        }
        if (behaviorProfile.herd().enabled()) {
            return StormSensitivity.HERD;
        }
        return StormSensitivity.GENERIC;
    }
}
