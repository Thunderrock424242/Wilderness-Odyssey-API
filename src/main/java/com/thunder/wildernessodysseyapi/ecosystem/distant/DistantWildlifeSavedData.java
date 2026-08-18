package com.thunder.wildernessodysseyapi.ecosystem.distant;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dimension-scoped authority for wildlife that is not currently an entity.
 *
 * <p>A represented animal exists either in this population ledger or as a real
 * entity. Mutators mark the ledger dirty only after a transition succeeds, so
 * the two forms are never intentionally counted at the same time.</p>
 */
public final class DistantWildlifeSavedData extends SavedData {
    private static final String DATA_NAME = ModConstants.MOD_ID + "_distant_wildlife";
    private static final int DATA_VERSION = 2;
    private static final int MAXIMUM_PERSISTED_GROUPS = 256;
    private static final double GROUP_MERGE_DISTANCE_SQUARED = 48.0 * 48.0;

    private final List<DistantWildlifeGroup> groups = new ArrayList<>();
    private long nextGroupId = 1L;

    /** Returns the persistent abstract population owner for one dimension. */
    public static DistantWildlifeSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(DistantWildlifeSavedData::new, DistantWildlifeSavedData::load),
                DATA_NAME
        );
    }

    private static DistantWildlifeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DistantWildlifeSavedData data = new DistantWildlifeSavedData();
        data.nextGroupId = Math.max(1L, tag.getLong("next_group_id"));
        ListTag list = tag.getList("groups", Tag.TAG_COMPOUND);
        int count = Math.min(list.size(), MAXIMUM_PERSISTED_GROUPS);
        for (int index = 0; index < count; index++) {
            try {
                DistantWildlifeGroup group = readGroup(list.getCompound(index));
                data.groups.add(group);
                data.nextGroupId = Math.max(data.nextGroupId, group.id() + 1L);
            } catch (IllegalArgumentException exception) {
                ModConstants.LOGGER.warn(
                        "Skipping invalid persisted distant wildlife group at index {}: {}",
                        index,
                        exception.getMessage()
                );
                data.setDirty();
            }
        }
        if (list.size() > MAXIMUM_PERSISTED_GROUPS) {
            ModConstants.LOGGER.warn(
                    "Trimmed distant wildlife data from {} to {} groups while loading",
                    list.size(),
                    MAXIMUM_PERSISTED_GROUPS
            );
            data.setDirty();
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putInt("data_version", DATA_VERSION);
        tag.putLong("next_group_id", nextGroupId);
        ListTag list = new ListTag();
        for (DistantWildlifeGroup group : groups) {
            list.add(writeGroup(group));
        }
        tag.put("groups", list);
        return tag;
    }

    /** Returns an immutable point-in-time view for manager and debug callers. */
    public List<DistantWildlifeGroup> groups() {
        return List.copyOf(groups);
    }

    /** Returns a group by its stable persisted identity. */
    public Optional<DistantWildlifeGroup> group(long id) {
        return groups.stream().filter(group -> group.id() == id).findFirst();
    }

    /** Returns the number of real entities currently avoided by this ledger. */
    public int representedAnimals() {
        return groups.stream().mapToInt(DistantWildlifeGroup::populationEstimate).sum();
    }

    /**
     * Commits one eligible entity into an existing nearby group or a new group.
     *
     * @return true only when capacity was available and the population changed
     */
    public boolean absorb(
            ResourceLocation species,
            Vec3 position,
            double directionX,
            double directionZ,
            double cruiseSpeed,
            long seed,
            long gameTime,
            DistantWildlifeForm form,
            boolean nocturnal,
            boolean weatherSensitive,
            int maximumGroups,
            int maximumRepresentedAnimals
    ) {
        if (representedAnimals() >= maximumRepresentedAnimals) {
            return false;
        }

        DistantWildlifeGroup nearest = null;
        double nearestDistanceSquared = GROUP_MERGE_DISTANCE_SQUARED;
        for (DistantWildlifeGroup group : groups) {
            if (!group.species().equals(species)
                    || group.form() != form
                    || group.nocturnal() != nocturnal
                    || group.weatherSensitive() != weatherSensitive
                    || group.populationEstimate() >= DistantWildlifeGroup.MAXIMUM_GROUP_POPULATION) {
                continue;
            }
            double distanceSquared = group.positionAt(gameTime).distanceToSqr(position);
            if (distanceSquared < nearestDistanceSquared) {
                nearest = group;
                nearestDistanceSquared = distanceSquared;
            }
        }

        if (nearest != null) {
            replace(nearest.absorb(position, cruiseSpeed, gameTime));
            return true;
        }
        if (groups.size() >= maximumGroups || groups.size() >= MAXIMUM_PERSISTED_GROUPS) {
            return false;
        }

        DistantWildlifeGroup created = new DistantWildlifeGroup(
                reserveGroupId(), species, 1,
                position.x, position.y, position.z,
                directionX, directionZ,
                cruiseSpeed, 1.0,
                seed, gameTime, form, nocturnal, weatherSensitive
        );
        groups.add(created);
        setDirty();
        return true;
    }

    /** Replaces one group after a group-level movement evaluation. */
    public boolean replace(DistantWildlifeGroup replacement) {
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).id() == replacement.id()) {
                if (groups.get(index).equals(replacement)) {
                    return false;
                }
                groups.set(index, replacement);
                setDirty();
                return true;
            }
        }
        return false;
    }

    /**
     * Decrements a group after one replacement entity was accepted by the level.
     * Empty groups are removed only on that successful path.
     */
    public boolean materializedOne(long groupId) {
        for (int index = 0; index < groups.size(); index++) {
            DistantWildlifeGroup group = groups.get(index);
            if (group.id() != groupId) {
                continue;
            }
            if (group.populationEstimate() == 1) {
                groups.remove(index);
            } else {
                groups.set(index, group.withPopulation(group.populationEstimate() - 1));
            }
            setDirty();
            return true;
        }
        return false;
    }

    private long reserveGroupId() {
        if (nextGroupId == Long.MAX_VALUE) {
            throw new IllegalStateException("Distant wildlife group id space exhausted");
        }
        return nextGroupId++;
    }

    private static CompoundTag writeGroup(DistantWildlifeGroup group) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", group.id());
        tag.putString("species", group.species().toString());
        tag.putInt("population", group.populationEstimate());
        tag.putDouble("x", group.anchorX());
        tag.putDouble("y", group.anchorY());
        tag.putDouble("z", group.anchorZ());
        tag.putDouble("direction_x", group.directionX());
        tag.putDouble("direction_z", group.directionZ());
        tag.putDouble("cruise_speed", group.cruiseSpeed());
        tag.putDouble("activity_scale", group.activityScale());
        tag.putLong("seed", group.seed());
        tag.putLong("reference_time", group.referenceGameTime());
        tag.putLong("population_reference_time", group.populationReferenceGameTime());
        tag.putDouble("food_availability", group.foodAvailability());
        tag.putDouble("water_availability", group.waterAvailability());
        tag.putDouble("food_pressure", group.foodPressure());
        tag.putDouble("disturbance", group.disturbance());
        tag.putDouble("weather_impact", group.weatherImpact());
        tag.putString("form", group.form().name());
        tag.putBoolean("nocturnal", group.nocturnal());
        tag.putBoolean("weather_sensitive", group.weatherSensitive());
        return tag;
    }

    private static DistantWildlifeGroup readGroup(CompoundTag tag) {
        ResourceLocation species = ResourceLocation.tryParse(tag.getString("species"));
        if (species == null) {
            throw new IllegalArgumentException("Invalid or missing species id");
        }
        DistantWildlifeForm form;
        try {
            form = DistantWildlifeForm.valueOf(tag.getString("form"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown distant wildlife form", exception);
        }
        long referenceTime = tag.getLong("reference_time");
        long populationReferenceTime = tag.contains("population_reference_time", Tag.TAG_LONG)
                ? tag.getLong("population_reference_time")
                : referenceTime;
        return new DistantWildlifeGroup(
                tag.getLong("id"),
                species,
                tag.getInt("population"),
                tag.getDouble("x"),
                tag.getDouble("y"),
                tag.getDouble("z"),
                tag.getDouble("direction_x"),
                tag.getDouble("direction_z"),
                tag.getDouble("cruise_speed"),
                tag.getDouble("activity_scale"),
                tag.getLong("seed"),
                referenceTime,
                populationReferenceTime,
                tag.contains("food_availability", Tag.TAG_DOUBLE) ? tag.getDouble("food_availability") : 0.65,
                tag.contains("water_availability", Tag.TAG_DOUBLE) ? tag.getDouble("water_availability") : 0.60,
                tag.getDouble("food_pressure"),
                tag.getDouble("disturbance"),
                tag.getDouble("weather_impact"),
                form,
                tag.getBoolean("nocturnal"),
                tag.getBoolean("weather_sensitive")
        );
    }
}
