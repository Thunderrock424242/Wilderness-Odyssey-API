package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.FoodAvailabilityService;
import com.thunder.wildernessodysseyapi.ecosystem.api.ShelterLocator;
import com.thunder.wildernessodysseyapi.ecosystem.api.ThreatAwarenessService;
import com.thunder.wildernessodysseyapi.ecosystem.api.WaterSourceLocator;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroupManager;
import net.minecraft.server.level.ServerLevel;

/** Process-wide service registry for the server-authoritative ecosystem runtime. */
public final class EcosystemServices {

    private static final NearbyLivingEntityCache NEARBY_ENTITIES = new NearbyLivingEntityCache();
    private static final DisturbanceMemoryService DISTURBANCES = new DisturbanceMemoryService();
    private static final CachedWaterSourceLocator WATER = new CachedWaterSourceLocator();
    private static final CachedShelterLocator SHELTER = new CachedShelterLocator();
    private static final CachedFoodAvailabilityService FOOD = new CachedFoodAvailabilityService(NEARBY_ENTITIES);
    private static final HerdAwarenessService HERD = new HerdAwarenessService(NEARBY_ENTITIES);
    private static final AnimalGroupManager GROUPS = new AnimalGroupManager(HERD::candidates);
    private static final StormReactionService STORM_REACTIONS = new StormReactionService(GROUPS, SHELTER);
    private static final DefaultThreatAwarenessService THREATS =
            new DefaultThreatAwarenessService(NEARBY_ENTITIES, DISTURBANCES);
    private static final EcosystemUpdateBudget BUDGET = new EcosystemUpdateBudget();

    private EcosystemServices() {
    }

    public static WaterSourceLocator water() {
        return WATER;
    }

    public static ShelterLocator shelter() {
        return SHELTER;
    }

    public static FoodAvailabilityService food() {
        return FOOD;
    }

    public static HerdAwarenessService herd() {
        return HERD;
    }

    /** Returns the transient server-owned social-animal group manager. */
    public static AnimalGroupManager groups() {
        return GROUPS;
    }

    /** Returns the leader-aware, region-cached pre-storm reaction coordinator. */
    public static StormReactionService stormReactions() {
        return STORM_REACTIONS;
    }

    public static ThreatAwarenessService threats() {
        return THREATS;
    }

    public static DisturbanceMemoryService disturbances() {
        return DISTURBANCES;
    }

    public static EcosystemUpdateBudget budget() {
        return BUDGET;
    }

    /** Clears every world-derived cache when a server level unloads. */
    public static void clear(ServerLevel level) {
        NEARBY_ENTITIES.clear(level);
        DISTURBANCES.clear(level);
        WATER.clear(level);
        SHELTER.clear(level);
        FOOD.clear(level);
        HERD.clear(level);
        STORM_REACTIONS.clear(level);
        BUDGET.clear(level);
        GROUPS.clear(level);
    }
}
