package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.ShelterLocator;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.api.StormReaction;
import com.thunder.wildernessodysseyapi.ecosystem.api.StormSensitivity;
import com.thunder.wildernessodysseyapi.ecosystem.api.StormSensitivityRegistry;
import com.thunder.wildernessodysseyapi.ecosystem.api.WeatherReactionDecision;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.StormReactionPolicy;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroup;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroupManager;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupRole;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Coordinates infrequent pre-storm decisions for individuals, herds, and flocks.
 *
 * <p>Grouped animals elect a stable local leader. Only that leader reads the
 * region-cached weather forecast and runs the bounded shelter locator; followers
 * inherit the immutable result. Individual decisions use the same ten-second
 * cache so no animal performs a forecast scan every behavior evaluation.</p>
 */
public final class StormReactionService {

    public static final int LOOK_AHEAD_TICKS = 7_200;
    private static final long DECISION_CACHE_TICKS = 200L;
    private static final int MAXIMUM_ENTRIES_PER_LEVEL = 4_096;

    private final AnimalGroupManager groups;
    private final ShelterLocator shelterLocator;
    private final Map<ServerLevel, Map<UUID, Entry>> levels = new WeakHashMap<>();

    StormReactionService(AnimalGroupManager groups, ShelterLocator shelterLocator) {
        this.groups = groups;
        this.shelterLocator = shelterLocator;
    }

    /** Returns a cached individual decision or a leader-owned group decision. */
    public WeatherReactionDecision assess(
            PathfinderMob animal,
            SpeciesBehaviorProfile behaviorProfile,
            int radiusCap
    ) {
        if (!(animal.level() instanceof ServerLevel level)
                || !EcosystemConfig.PRE_STORM_REACTIONS_ENABLED.get()) {
            return WeatherReactionDecision.NONE;
        }
        long gameTime = level.getGameTime();
        StormSensitivity sensitivity = StormSensitivityRegistry.resolve(animal, behaviorProfile);
        Optional<AnimalGroup> resolvedGroup = behaviorProfile.herd().enabled()
                ? groups.groupFor(animal)
                : Optional.empty();
        UUID decisionId = resolvedGroup.map(AnimalGroup::getLeader).orElse(animal.getUUID());
        boolean leader = resolvedGroup
                .flatMap(group -> group.roleOf(animal.getUUID()))
                .map(role -> role == GroupRole.LEADER)
                .orElse(true);
        int groupSize = resolvedGroup.map(AnimalGroup::memberCount).orElse(1);
        Map<UUID, Entry> decisions = levels.computeIfAbsent(level, ignored -> new HashMap<>());
        Entry cached = decisions.get(decisionId);
        if (cached != null && cached.expiresAt() > gameTime && cached.sensitivity().equals(sensitivity)) {
            return leader ? cached.decision() : cached.decision().asInherited();
        }

        if (!leader) {
            // The follower deliberately waits for the leader's next staggered
            // decision instead of performing an independent forecast search.
            return new WeatherReactionDecision(
                    WeatherThreatForecast.NONE,
                    sensitivity,
                    StormReaction.WAITING_FOR_LEADER,
                    Optional.empty(),
                    decisionId,
                    groupSize,
                    true
            );
        }

        WeatherThreatForecast forecast = WeatherServices.query().getApproachingWeather(
                level,
                animal.blockPosition(),
                LOOK_AHEAD_TICKS
        );
        StormReaction response = StormReactionPolicy.decide(forecast, sensitivity);
        Optional<EnvironmentalContext.ShelterTarget> shelter = Optional.empty();
        if (response == StormReaction.SEEK_SHELTER && behaviorProfile.shelter().enabled()) {
            shelter = shelterLocator.find(
                    animal,
                    behaviorProfile,
                    Math.min(radiusCap, behaviorProfile.shelter().searchRadius()),
                    sensitivity.shelterPreference()
            );
            if (shelter.isEmpty()) {
                response = StormReaction.ALERT;
            }
        }
        WeatherReactionDecision decision = new WeatherReactionDecision(
                forecast,
                sensitivity,
                response,
                shelter,
                decisionId,
                groupSize,
                false
        );
        decisions.put(decisionId, new Entry(gameTime + DECISION_CACHE_TICKS, sensitivity, decision));
        trim(decisions);
        return decision;
    }

    /**
     * Returns a still-current decision for diagnostics without calculating weather or shelter.
     */
    public Optional<WeatherReactionDecision> cached(
            PathfinderMob animal,
            SpeciesBehaviorProfile behaviorProfile
    ) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Optional<AnimalGroup> resolvedGroup = behaviorProfile.herd().enabled()
                ? groups.groupFor(animal)
                : Optional.empty();
        UUID decisionId = resolvedGroup.map(AnimalGroup::getLeader).orElse(animal.getUUID());
        Entry entry = levels.getOrDefault(level, Map.of()).get(decisionId);
        if (entry == null || entry.expiresAt() <= level.getGameTime()) {
            return Optional.empty();
        }
        boolean leader = resolvedGroup
                .flatMap(group -> group.roleOf(animal.getUUID()))
                .map(role -> role == GroupRole.LEADER)
                .orElse(true);
        return Optional.of(leader ? entry.decision() : entry.decision().asInherited());
    }

    /** Releases cached decisions for an unloading server level. */
    public void clear(ServerLevel level) {
        levels.remove(level);
    }

    private static void trim(Map<UUID, Entry> decisions) {
        while (decisions.size() > MAXIMUM_ENTRIES_PER_LEVEL) {
            decisions.remove(decisions.keySet().iterator().next());
        }
    }

    private record Entry(
            long expiresAt,
            StormSensitivity sensitivity,
            WeatherReactionDecision decision
    ) {
    }
}
