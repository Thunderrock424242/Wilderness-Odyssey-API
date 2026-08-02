package com.thunder.wildernessodysseyapi.watersystem.water.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Public, ordered registry for entity hydrodynamic profiles.
 *
 * <p>Optional vehicle mods can register a narrow entity predicate without
 * changing vanilla fluid identity or depending on internal solver classes.
 * Higher priority entries win; built-in boat, item, and living profiles remain
 * the fallback. Registration is synchronized and resolution reads an immutable
 * snapshot, so entity ticks never lock.</p>
 */
public final class WaterPhysicsProfileRegistry {

    /** Physically heavier, directionally stable profile for vanilla watercraft. */
    public static final WaterPhysicsProfile BOAT = new WaterPhysicsProfile(
            280.0, 180.0, 0.0,
            0.62,
            0.24, 0.90, 0.25,
            0.42, 0.95, 0.75,
            0.018, 0.72, 0.020,
            0.040,
            true
    );
    /** Low-mass profile that lets dropped stacks float without violent impulses. */
    public static final WaterPhysicsProfile ITEM = new WaterPhysicsProfile(
            0.25, 1.0, 0.025,
            0.040,
            0.18, 0.18, 0.08,
            0.55, 0.55, 0.40,
            0.0, 0.0, 0.0,
            0.018,
            false
    );
    /** Gentle additive profile that leaves vanilla swimming in control. */
    public static final WaterPhysicsProfile LIVING = new WaterPhysicsProfile(
            48.0, 30.0, 0.0,
            0.0,
            0.040, 0.040, 0.008,
            0.50, 0.50, 0.20,
            0.0, 0.0, 0.0,
            0.006,
            false
    );

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static volatile List<Entry> snapshot = List.of();

    static {
        register(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "vanilla_boat"),
                0, entity -> entity instanceof Boat, BOAT);
        register(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "vanilla_item"),
                -10, entity -> entity instanceof ItemEntity, ITEM);
        register(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "vanilla_living"),
                -20, entity -> entity instanceof LivingEntity, LIVING);
    }

    private WaterPhysicsProfileRegistry() {
    }

    /**
     * Registers one profile matcher.
     *
     * @param id stable integration identifier
     * @param priority higher values resolve before lower values
     * @param matcher narrow entity predicate owned by the integration
     * @param profile immutable physical profile
     */
    public static synchronized void register(
            ResourceLocation id,
            int priority,
            Predicate<Entity> matcher,
            WaterPhysicsProfile profile
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(matcher, "matcher");
        Objects.requireNonNull(profile, "profile");
        if (ENTRIES.containsKey(id)) {
            throw new IllegalStateException("Duplicate water physics profile: " + id);
        }
        ENTRIES.put(id, new Entry(id, priority, matcher, profile));
        List<Entry> ordered = new ArrayList<>(ENTRIES.values());
        ordered.sort(Comparator.comparingInt(Entry::priority).reversed());
        snapshot = List.copyOf(ordered);
    }

    /** Returns the first matching profile, or {@code null} for an unsupported entity. */
    public static WaterPhysicsProfile resolve(Entity entity) {
        if (entity == null) {
            return null;
        }
        for (Entry entry : snapshot) {
            if (entry.matcher.test(entity)) {
                return entry.profile;
            }
        }
        return null;
    }

    /** Returns registered identifiers in resolution order for diagnostics. */
    public static List<ResourceLocation> registeredIds() {
        return snapshot.stream().map(Entry::id).toList();
    }

    private record Entry(
            ResourceLocation id,
            int priority,
            Predicate<Entity> matcher,
            WaterPhysicsProfile profile
    ) {
    }
}
