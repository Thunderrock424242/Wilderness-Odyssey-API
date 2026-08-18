package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.api.ThreatAwarenessService;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;

import java.util.List;
import java.util.Optional;

/** Detects tagged/hostile threats, retains memory, and propagates herd warnings. */
public final class DefaultThreatAwarenessService implements ThreatAwarenessService {

    private final NearbyLivingEntityCache nearbyEntities;
    private final DisturbanceMemoryService disturbances;

    DefaultThreatAwarenessService(
            NearbyLivingEntityCache nearbyEntities,
            DisturbanceMemoryService disturbances
    ) {
        this.nearbyEntities = nearbyEntities;
        this.disturbances = disturbances;
    }

    @Override
    public Optional<EnvironmentalContext.Threat> assess(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            AnimalNeedsState needs,
            int radius
    ) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        long gameTime = level.getGameTime();
        int memoryTicks = profile.prey().threatMemoryTicks();

        LivingEntity attacker = animal.getLastHurtByMob();
        if (attacker != null && attacker.isAlive()) {
            EnvironmentalContext.Threat threat = threat(animal, attacker, gameTime + memoryTicks);
            needs.rememberThreat(threat.position(), threat.entityId(), threat.expiresAt());
            return Optional.of(threat);
        }

        List<TagKey<EntityType<?>>> threatTags = profile.prey().threatTags().stream()
                .map(id -> TagKey.create(Registries.ENTITY_TYPE, id))
                .toList();
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : nearbyEntities.query(
                level, animal.blockPosition(), radius, gameTime)) {
            if (candidate == animal || !candidate.isAlive()) {
                continue;
            }
            if (candidate instanceof TamableAnimal tamable && tamable.isTame()) {
                continue;
            }
            boolean dangerous = candidate instanceof Enemy
                    || threatTags.stream().anyMatch(candidate.getType().builtInRegistryHolder()::is);
            if (!dangerous || !animal.hasLineOfSight(candidate)) {
                continue;
            }
            double distance = animal.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            EnvironmentalContext.Threat threat = threat(animal, nearest, gameTime + memoryTicks);
            needs.rememberThreat(threat.position(), threat.entityId(), threat.expiresAt());
            return Optional.of(threat);
        }

        Optional<EnvironmentalContext.Disturbance> disturbance = disturbances.nearest(
                level, animal.blockPosition(), radius, gameTime);
        if (disturbance.isPresent() && disturbance.get().intensity() >= 0.35) {
            EnvironmentalContext.Disturbance source = disturbance.get();
            EnvironmentalContext.Threat threat = new EnvironmentalContext.Threat(
                    source.position(),
                    source.sourceId(),
                    source.position().distSqr(animal.blockPosition()),
                    Math.min(gameTime + memoryTicks, source.createdAt() + memoryTicks)
            );
            needs.rememberThreat(threat.position(), threat.entityId(), threat.expiresAt());
            return Optional.of(threat);
        }

        needs.forgetThreat(gameTime);
        if (needs.threatPosition() != null && gameTime < needs.threatExpiresAt()) {
            return Optional.of(new EnvironmentalContext.Threat(
                    needs.threatPosition(),
                    needs.threatEntityId(),
                    needs.threatPosition().distSqr(animal.blockPosition()),
                    needs.threatExpiresAt()
            ));
        }
        return Optional.empty();
    }

    @Override
    public void warnHerd(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            EnvironmentalContext.Threat threat,
            int radius
    ) {
        if (!(animal.level() instanceof ServerLevel level) || radius <= 0) {
            return;
        }
        if (EcosystemConfig.GROUP_AI_ENABLED.get()
                && EcosystemServices.groups().groupFor(animal).isPresent()) {
            EcosystemServices.groups().reportThreat(
                    animal, threat, EcosystemConfig.GROUP_FORMATION_RADIUS.get());
            return;
        }
        for (LivingEntity candidate : nearbyEntities.query(
                level, animal.blockPosition(), radius, level.getGameTime())) {
            if (!(candidate instanceof PathfinderMob herdMember)
                    || candidate == animal
                    || candidate.getType() != animal.getType()) {
                continue;
            }
            Optional<SpeciesBehaviorProfile> herdProfile = SpeciesBehaviorProfileManager.profileFor(herdMember);
            if (herdProfile.isEmpty() || !herdProfile.get().id().equals(profile.id())) {
                continue;
            }
            AnimalNeedsState state = herdMember.getData(ModAttachments.ANIMAL_NEEDS);
            state.rememberThreat(threat.position(), threat.entityId(), threat.expiresAt());
            state.scheduleEvaluation(level.getGameTime() + Math.floorMod(herdMember.getId(), 5));
        }
    }

    private static EnvironmentalContext.Threat threat(
            PathfinderMob animal,
            LivingEntity threat,
            long expiresAt
    ) {
        return new EnvironmentalContext.Threat(
                threat.blockPosition(),
                threat.getUUID(),
                animal.distanceToSqr(threat),
                expiresAt
        );
    }
}
