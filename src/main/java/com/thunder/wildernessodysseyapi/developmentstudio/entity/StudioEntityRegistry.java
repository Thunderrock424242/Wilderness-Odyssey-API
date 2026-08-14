package com.thunder.wildernessodysseyapi.developmentstudio.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fixed, conservative entity allowlist for repeatable lab behavior. */
public final class StudioEntityRegistry {
    private static final Map<ResourceLocation, StudioEntityDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private StudioEntityRegistry() {
    }

    /** Registers a small vanilla set so client ids can never select arbitrary entity types. */
    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        register(EntityType.COW, "Cow");
        register(EntityType.PIG, "Pig");
        register(EntityType.ZOMBIE, "Zombie");
        register(EntityType.SKELETON, "Skeleton");
    }

    public static Optional<StudioEntityDefinition> get(ResourceLocation id) {
        bootstrapDefaults();
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static List<StudioEntityOption> options() {
        bootstrapDefaults();
        return DEFINITIONS.values().stream()
                .map(definition -> new StudioEntityOption(definition.id(), definition.displayName()))
                .toList();
    }

    private static void register(EntityType<?> type, String displayName) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        StudioEntityDefinition definition = new StudioEntityDefinition(id, displayName, type);
        if (DEFINITIONS.putIfAbsent(id, definition) != null) {
            throw new IllegalStateException("Duplicate Studio entity id: " + id);
        }
    }
}
