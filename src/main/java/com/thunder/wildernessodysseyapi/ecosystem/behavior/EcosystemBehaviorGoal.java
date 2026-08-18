package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.FoodAvailabilityService;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.api.WeatherReactionDecision;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeWeatherResponse;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroup;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupBehavior;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupRole;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.tags.FluidTags;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Conditional goal that temporarily extends, rather than replaces, vanilla AI.
 *
 * <p>The goal claims movement only while an urgent ecosystem action is active.
 * Vanilla panic/float goals retain their higher priority, and unprofiled mobs
 * never receive this goal.</p>
 */
public final class EcosystemBehaviorGoal extends Goal {

    private static final int MAXIMUM_ACTION_TICKS = 400;
    private final PathfinderMob animal;
    private final AnimalNeedsState needs;
    private final DefaultEcosystemBehaviorController controller = new DefaultEcosystemBehaviorController();
    private SpeciesBehaviorProfile activeProfile;
    private LivingEntity huntingTarget;
    private AnimalGroup activeGroup;
    private long activeGroupRevision = Long.MIN_VALUE;
    private int attackDelay;
    private long nextNavigationUpdateAt;

    public EcosystemBehaviorGoal(PathfinderMob animal) {
        this.animal = animal;
        this.needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(animal.level() instanceof ServerLevel level)
                || !animal.isAlive()
                || !EcosystemConfig.ENABLED.get()) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (gameTime < needs.nextEvaluationAt()) {
            return false;
        }
        WildlifeSimulationLod simulationLod = EcosystemSimulationManager.get()
                .getSimulationLevel(level, animal.blockPosition());
        needs.setSimulationLod(simulationLod);
        if (simulationLod == WildlifeSimulationLod.DISTANT
                || simulationLod == WildlifeSimulationLod.DORMANT) {
            if (simulationLod == WildlifeSimulationLod.DISTANT) {
                needs.abstractState(
                        EcosystemBehaviorState.IDLE,
                        gameTime,
                        "individual AI delegated to abstract regional population",
                        simulationLod
                );
            } else {
                needs.idle();
                needs.setDiagnostics(
                        "outside environmental simulation range",
                        WildlifeWeatherResponse.NOT_SAMPLED,
                        "dormant",
                        simulationLod
                );
            }
            needs.scheduleEvaluation(gameTime + EcosystemConfig.REGIONAL_UPDATE_INTERVAL.get());
            return false;
        }
        Optional<SpeciesBehaviorProfile> resolved = SpeciesBehaviorProfileManager.profileFor(animal);
        if (resolved.isEmpty()) {
            return false;
        }
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        double speciesMultiplier = EcosystemConfig.speciesMultiplier(entityId);
        if (speciesMultiplier <= 0.0) {
            return false;
        }
        activeProfile = resolved.get();
        activeGroup = null;
        activeGroupRevision = Long.MIN_VALUE;
        boolean budgetAcquired = false;
        AnimalGroup decisionGroup = null;

        // Local discovery is charged once, then followers skip every expensive context evaluation.
        if (groupAiApplies(activeProfile)) {
            Optional<AnimalGroup> cached = EcosystemServices.groups().groupFor(animal);
            if (cached.isEmpty()) {
                if (!acquireBudget(level, gameTime)) {
                    return false;
                }
                budgetAcquired = true;
                decisionGroup = EcosystemServices.groups().discoverOrCreate(
                        animal,
                        activeProfile,
                        Math.min(EcosystemConfig.MAXIMUM_SEARCH_RADIUS.get(), activeProfile.herd().searchRadius()),
                        EcosystemConfig.MAX_GROUP_SIZE.get(),
                        EcosystemConfig.MEMBER_VALIDATION_INTERVAL.get()
                );
            } else {
                decisionGroup = cached.get();
            }

            if (decisionGroup.roleOf(animal.getUUID()).orElse(null) == GroupRole.FOLLOWER) {
                inheritGroupDecision(decisionGroup, gameTime, simulationLod);
                needs.scheduleEvaluation(gameTime + 20L + Math.floorMod(animal.getId(), 13));
                return false;
            }
            if (decisionGroup.validationDue(gameTime)) {
                if (!budgetAcquired && !acquireBudget(level, gameTime)) {
                    return false;
                }
                budgetAcquired = true;
                EcosystemServices.groups().validateAndRecruit(
                        decisionGroup,
                        activeProfile,
                        Math.min(EcosystemConfig.MAXIMUM_SEARCH_RADIUS.get(), activeProfile.herd().searchRadius()),
                        EcosystemConfig.MAX_GROUP_SIZE.get(),
                        EcosystemConfig.MEMBER_VALIDATION_INTERVAL.get()
                );
                if (decisionGroup.roleOf(animal.getUUID()).orElse(null) != GroupRole.LEADER) {
                    inheritGroupDecision(decisionGroup, gameTime, simulationLod);
                    needs.scheduleEvaluation(gameTime + 20L + Math.floorMod(animal.getId(), 13));
                    return false;
                }
            }
            if (!decisionGroup.canLeaderDecide(gameTime)) {
                needs.scheduleEvaluation(Math.max(gameTime + 1L, decisionGroup.nextLeaderDecisionAt()));
                return false;
            }

            // A supplied destination is already a complete broad decision and needs no world scan.
            if (decisionGroup.hasPendingLeaderRequest()
                    && decisionGroup.requestedDestination() != null) {
                if (!budgetAcquired && !acquireBudget(level, gameTime)) {
                    return false;
                }
                long started = System.nanoTime();
                GroupBehavior requested = decisionGroup.requestedState();
                needs.begin(
                        requested == GroupBehavior.FLEE
                                ? EcosystemBehaviorState.FLEEING
                                : EcosystemBehaviorState.TRAVEL,
                        decisionGroup.requestedDestination(),
                        gameTime
                );
                activeGroup = decisionGroup;
                activeGroupRevision = decisionGroup.publishLeaderDecision(
                        requested,
                        decisionGroup.requestedDestination(),
                        gameTime,
                        EcosystemConfig.LEADER_DECISION_INTERVAL.get()
                );
                long elapsedNanos = System.nanoTime() - started;
                needs.recordEvaluation(
                        gameTime,
                        elapsedNanos,
                        Math.max(gameTime + evaluationInterval(level), decisionGroup.nextLeaderDecisionAt())
                );
                EcosystemSimulationManager.get().recordEntityEvaluation(level, gameTime, elapsedNanos);
                return true;
            }
            if (decisionGroup.hasPendingLeaderRequest()
                    && (decisionGroup.requestedState() == GroupBehavior.IDLE
                    || decisionGroup.requestedState() == GroupBehavior.FEED
                    || decisionGroup.requestedState() == GroupBehavior.REST)) {
                GroupBehavior requested = decisionGroup.requestedState();
                decisionGroup.publishLeaderDecision(
                        requested, null, gameTime, EcosystemConfig.LEADER_DECISION_INTERVAL.get());
                needs.idle();
                needs.recordEvaluation(gameTime, 0L, decisionGroup.nextLeaderDecisionAt());
                return false;
            }
        }

        if (!budgetAcquired && !acquireBudget(level, gameTime)) {
            return false;
        }

        long started = System.nanoTime();
        EnvironmentalContext context = buildContext(
                level, activeProfile, speciesMultiplier, gameTime, decisionGroup);
        boolean selected;
        if (decisionGroup != null && decisionGroup.hasPendingLeaderRequest()) {
            selected = evaluateRequestedGroupState(context, decisionGroup);
            if (!selected) {
                decisionGroup.recordLeaderAttempt(
                        gameTime, EcosystemConfig.LEADER_DECISION_INTERVAL.get());
            }
        } else {
            selected = controller.evaluate(context, needs);
        }
        long nextEvaluation = gameTime + evaluationInterval(level);
        if (decisionGroup != null) {
            if (selected) {
                activeGroup = decisionGroup;
                activeGroupRevision = decisionGroup.publishLeaderDecision(
                        groupBehavior(needs.behavior()),
                        needs.behaviorTarget(),
                        gameTime,
                        EcosystemConfig.LEADER_DECISION_INTERVAL.get()
                );
            } else if (!decisionGroup.hasPendingLeaderRequest()) {
                decisionGroup.publishLeaderDecision(
                        GroupBehavior.IDLE, null, gameTime, EcosystemConfig.LEADER_DECISION_INTERVAL.get());
            }
            nextEvaluation = Math.max(nextEvaluation, decisionGroup.nextLeaderDecisionAt());
        }
        long elapsedNanos = System.nanoTime() - started;
        needs.recordEvaluation(gameTime, elapsedNanos, nextEvaluation);
        EcosystemSimulationManager.get().recordEntityEvaluation(level, gameTime, elapsedNanos);
        return selected;
    }

    @Override
    public boolean canContinueToUse() {
        return animal.isAlive()
                && EcosystemConfig.ENABLED.get()
                && needs.behavior() != EcosystemBehaviorState.IDLE
                && animal.level() instanceof ServerLevel;
    }

    @Override
    public void start() {
        attackDelay = 0;
        nextNavigationUpdateAt = 0L;
        EcosystemBehaviorState behavior = needs.behavior();
        if (behavior == EcosystemBehaviorState.HUNTING
                || (behavior == EcosystemBehaviorState.FORAGE && needs.huntTargetId() != null)) {
            huntingTarget = findLivingEntity(needs.huntTargetId());
            if (huntingTarget != null && huntingTarget.isAlive()) {
                animal.setTarget(huntingTarget);
                animal.getNavigation().moveTo(huntingTarget, activeProfile.predator().moveSpeed());
                nextNavigationUpdateAt = animal.level().getGameTime() + 10L;
                return;
            }
            needs.idle();
            return;
        }
        BlockPos target = needs.behaviorTarget();
        if (target != null) {
            animal.getNavigation().moveTo(
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5,
                    movementSpeed(behavior)
            );
        }
    }

    @Override
    public void tick() {
        if (!(animal.level() instanceof ServerLevel level) || activeProfile == null) {
            needs.idle();
            return;
        }
        long gameTime = level.getGameTime();
        adoptUrgentGroupThreat(gameTime);
        switch (needs.behavior()) {
            case ALERT -> tickAlert(gameTime);
            case DRINK -> {
                if (needs.interactionStartedAt() == 0L) {
                    tickSeekingWater(gameTime);
                } else {
                    tickDrinking(gameTime);
                }
            }
            case SEEKING_WATER -> tickSeekingWater(gameTime);
            case DRINKING -> tickDrinking(gameTime);
            case SEEK_SHELTER -> {
                if (needs.interactionStartedAt() == 0L) {
                    tickSeekingShelter(gameTime);
                } else {
                    tickSheltering(level, gameTime);
                }
            }
            case SEEKING_SHELTER -> tickSeekingShelter(gameTime);
            case SHELTERING -> tickSheltering(level, gameTime);
            case TRAVEL, FLEE, MIGRATE, FLEEING, REGROUPING -> tickBoundedMovement(gameTime);
            case FORAGE -> {
                if (needs.huntTargetId() != null) {
                    tickHunting(level, gameTime);
                } else {
                    tickBoundedMovement(gameTime);
                }
            }
            case HUNTING -> tickHunting(level, gameTime);
            case REST, SLEEP -> tickResting(gameTime);
            case IDLE -> {
            }
            default -> needs.idle();
        }
    }

    @Override
    public void stop() {
        animal.getNavigation().stop();
        if ((needs.behavior() == EcosystemBehaviorState.HUNTING
                || (needs.behavior() == EcosystemBehaviorState.FORAGE && needs.huntTargetId() != null))
                && animal.level() instanceof ServerLevel level
                && activeProfile != null) {
            needs.setNextHuntAllowedAt(level.getGameTime() + activeProfile.predator().huntCooldownTicks() / 4L);
        }
        if (huntingTarget != null && animal.getTarget() == huntingTarget) {
            animal.setTarget(null);
        }
        huntingTarget = null;
        if (activeGroup != null) {
            activeGroup.clearDecisionIfRevision(activeGroupRevision);
        }
        activeGroup = null;
        activeGroupRevision = Long.MIN_VALUE;
        if (needs.behavior() != EcosystemBehaviorState.IDLE) {
            needs.idle();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean acquireBudget(ServerLevel level, long gameTime) {
        if (EcosystemServices.budget().tryAcquire(level, gameTime)) {
            return true;
        }
        needs.scheduleEvaluation(gameTime + 5L + Math.floorMod(animal.getId(), 16));
        return false;
    }

    private boolean groupAiApplies(SpeciesBehaviorProfile profile) {
        return EcosystemConfig.HERD_BEHAVIOR_ENABLED.get()
                && EcosystemConfig.GROUP_AI_ENABLED.get()
                && profile.herd().enabled()
                && !animal.isNoAi()
                && !(animal instanceof TamableAnimal tamable && tamable.isTame());
    }

    private void adoptUrgentGroupThreat(long gameTime) {
        if (activeGroup == null
                || !activeGroup.hasPendingLeaderRequest()
                || activeGroup.requestedState() != GroupBehavior.FLEE
                || activeGroup.requestedDestination() == null
                || activeGroup.roleOf(animal.getUUID()).orElse(null) != GroupRole.LEADER) {
            return;
        }
        BlockPos escapeTarget = activeGroup.requestedDestination();
        needs.begin(EcosystemBehaviorState.FLEEING, escapeTarget, gameTime);
        activeGroupRevision = activeGroup.publishLeaderDecision(
                GroupBehavior.FLEE,
                escapeTarget,
                gameTime,
                EcosystemConfig.LEADER_DECISION_INTERVAL.get()
        );
        animal.getNavigation().moveTo(
                escapeTarget.getX() + 0.5,
                escapeTarget.getY(),
                escapeTarget.getZ() + 0.5,
                movementSpeed(EcosystemBehaviorState.FLEEING)
        );
    }

    private boolean evaluateRequestedGroupState(EnvironmentalContext context, AnimalGroup group) {
        GroupBehavior requested = group.requestedState();
        if (requested == GroupBehavior.SEEK_WATER && context.water().isPresent()) {
            EnvironmentalContext.WaterTarget water = context.water().get();
            needs.setWaterPosition(water.waterPosition());
            needs.begin(EcosystemBehaviorState.SEEKING_WATER, water.approachPosition(), context.gameTime());
            return true;
        }
        if (requested == GroupBehavior.SEEK_SHELTER && context.shelter().isPresent()) {
            EnvironmentalContext.ShelterTarget shelter = context.shelter().get();
            needs.setShelterPosition(shelter.position());
            needs.begin(EcosystemBehaviorState.SEEKING_SHELTER, shelter.position(), context.gameTime());
            return true;
        }
        if (requested == GroupBehavior.FLEE && context.threat().isPresent()) {
            return controller.evaluate(context, needs);
        }
        needs.idle();
        return false;
    }

    private static GroupBehavior groupBehavior(EcosystemBehaviorState behavior) {
        return switch (behavior) {
            case FORAGE, HUNTING -> GroupBehavior.FEED;
            case TRAVEL, MIGRATE, REGROUPING -> GroupBehavior.TRAVEL;
            case DRINK, SEEKING_WATER, DRINKING -> GroupBehavior.SEEK_WATER;
            case REST, SLEEP -> GroupBehavior.REST;
            case ALERT -> GroupBehavior.ALERT;
            case SEEK_SHELTER, SEEKING_SHELTER, SHELTERING -> GroupBehavior.SEEK_SHELTER;
            case FLEE, FLEEING -> GroupBehavior.FLEE;
            case IDLE -> GroupBehavior.IDLE;
        };
    }

    private EnvironmentalContext buildContext(
            ServerLevel level,
            SpeciesBehaviorProfile profile,
            double speciesMultiplier,
            long gameTime,
            AnimalGroup decisionGroup
    ) {
        int radiusCap = EcosystemConfig.MAXIMUM_SEARCH_RADIUS.get();
        RegionalEnvironmentSnapshot regionalEnvironment = EnvironmentServices.query()
                .sample(level, animal.blockPosition());
        WeatherSample weather = regionalEnvironment.weather();
        WeatherReactionDecision weatherReaction = EcosystemServices.stormReactions().assess(
                animal,
                profile,
                radiusCap
        );
        needs.rememberWeatherReaction(weatherReaction);
        boolean exposed = level.canSeeSky(animal.blockPosition().above());
        Optional<EnvironmentalContext.Disturbance> disturbance = EcosystemServices.disturbances().nearest(
                level, animal.blockPosition(), Math.min(radiusCap, 16), gameTime);
        Optional<EnvironmentalContext.Threat> threat = profile.prey().enabled()
                ? EcosystemServices.threats().assess(
                animal, profile, needs, Math.min(radiusCap, profile.prey().threatRadius()))
                : Optional.empty();
        Optional<EnvironmentalContext.HerdCenter> herd = profile.herd().enabled()
                ? decisionGroup == null
                ? EcosystemServices.herd().assess(animal, Math.min(radiusCap, profile.herd().searchRadius()))
                : EcosystemServices.groups().center(decisionGroup, animal)
                : Optional.empty();

        double foodAvailability = profile.predator().enabled()
                ? 0.0
                : EcosystemServices.food().availability(animal, Math.min(radiusCap, 12));
        if (!profile.predator().enabled()) {
            foodAvailability *= 0.55
                    + regionalEnvironment.influence().habitatProductivity() * 0.45;
        }
        FoodAvailabilityService.PredatorFoodSample prey = profile.predator().enabled()
                ? EcosystemServices.food().prey(animal, profile, Math.min(radiusCap, profile.predator().huntRadius()))
                : new FoodAvailabilityService.PredatorFoodSample(java.util.List.of(), Optional.empty());

        long elapsedTicks = needs.lastEvaluatedAt() == 0L
                ? EcosystemConfig.BEHAVIOR_UPDATE_FREQUENCY.get()
                : gameTime - needs.lastEvaluatedAt();
        int scheduleOffset = WildlifeSchedule.deterministicOffset(
                animal.getUUID(), profile.environment().scheduleJitterTicks());
        boolean preferredActive = WildlifeSchedule.period(
                profile.environment().activeTime(), level.getDayTime(), scheduleOffset)
                == WildlifeSchedule.Period.ACTIVE;
        EcosystemNeedModel.Values updated = EcosystemNeedModel.advance(
                new EcosystemNeedModel.Values(
                        needs.thirst(), needs.hunger(), needs.rest(), needs.social(), needs.safetyConcern()),
                profile.needs(),
                elapsedTicks,
                weather.temperature(),
                animal.getDeltaMovement().horizontalDistanceSqr() > 0.0025,
                preferredActive,
                !exposed,
                foodAvailability,
                herd.isPresent() && herd.get().distanceSquared()
                        <= profile.herd().preferredDistance() * profile.herd().preferredDistance(),
                threat.isPresent(),
                EcosystemConfig.THIRST_RATE_MULTIPLIER.get(),
                speciesMultiplier
        );
        needs.setNeeds(updated.thirst(), updated.hunger(), updated.rest(), updated.social(), updated.safetyConcern());

        var watershed = regionalEnvironment.watershed();
        boolean weatherHazard = weather.precipitationIntensity() >= profile.shelter().precipitationThreshold()
                || weather.thunderIntensity() >= profile.shelter().thunderThreshold()
                || weather.wind().magnitude() >= profile.shelter().windThreshold()
                || watershed.flooding()
                || watershed.floodRisk() >= 0.82f;
        boolean groupRequestsWater = decisionGroup != null
                && decisionGroup.requestedState() == GroupBehavior.SEEK_WATER;
        boolean hotOrDry = weather.temperature() > profile.environment().preferredMaximumTemperatureCelsius()
                || (weather.humidity() < 0.30 && watershed.soilSaturation() < 0.25f)
                || regionalEnvironment.vegetation().droughtLevel() >= 0.72;
        double drinkThreshold = profile.drinking().thirstThreshold()
                - (hotOrDry ? profile.environment().hotDryDrinkThresholdReduction() : 0.0);
        Optional<EnvironmentalContext.WaterTarget> water = profile.drinking().enabled()
                && (groupRequestsWater || needs.thirst() >= Math.max(0.05, drinkThreshold))
                ? EcosystemServices.water().find(
                animal, profile, Math.min(radiusCap, profile.drinking().searchRadius()))
                : Optional.empty();
        boolean groupRequestsShelter = decisionGroup != null
                && decisionGroup.requestedState() == GroupBehavior.SEEK_SHELTER;
        Optional<EnvironmentalContext.ShelterTarget> shelter;
        if (weatherReaction.shelter().isPresent()) {
            shelter = weatherReaction.shelter();
        } else if (profile.shelter().enabled()
                && EcosystemConfig.WEATHER_SHELTER_ENABLED.get()
                && (weatherHazard || groupRequestsShelter)
                && exposed) {
            shelter = EcosystemServices.shelter().find(
                    animal,
                    profile,
                    Math.min(radiusCap, profile.shelter().searchRadius())
            );
        } else {
            shelter = Optional.empty();
        }
        Optional<EnvironmentalContext.PreyTarget> preyTarget = prey.target().map(target ->
                new EnvironmentalContext.PreyTarget(
                        target.getUUID(), target.blockPosition(), prey.population().size()));
        ResourceLocation biome = level.getBiome(animal.blockPosition()).unwrapKey()
                .map(key -> key.location())
                .orElse(ResourceLocation.withDefaultNamespace("plains"));

        return new EnvironmentalContext(
                level,
                animal,
                profile,
                gameTime,
                level.getDayTime(),
                biome,
                weather,
                weatherReaction,
                watershed,
                exposed,
                foodAvailability,
                water,
                shelter,
                threat,
                herd,
                preyTarget,
                disturbance,
                regionalEnvironment
        );
    }

    private void tickSeekingWater(long gameTime) {
        BlockPos target = needs.behaviorTarget();
        if (target != null && animal.distanceToSqr(target.getCenter()) <= 2.25) {
            if (!isWaterPresent(needs.waterPosition())) {
                needs.idle();
                needs.scheduleEvaluation(gameTime + 100L);
                return;
            }
            animal.getNavigation().stop();
            if (needs.behavior() == EcosystemBehaviorState.DRINK) {
                needs.markInteractionStarted(gameTime);
            } else {
                needs.begin(EcosystemBehaviorState.DRINKING, needs.waterPosition(), gameTime);
            }
            return;
        }
        stopFailedOrTimedOut(gameTime);
    }

    // Alert animals pause low-priority wandering and look around without
    // manufacturing panic movement or replacing higher-priority vanilla goals.
    private void tickAlert(long gameTime) {
        animal.getNavigation().stop();
        if (gameTime % 30L == Math.floorMod(animal.getId(), 30)) {
            double lookX = animal.getX() + animal.getRandom().nextInt(13) - 6;
            double lookY = animal.getEyeY() + animal.getRandom().nextInt(5) - 2;
            double lookZ = animal.getZ() + animal.getRandom().nextInt(13) - 6;
            animal.getLookControl().setLookAt(lookX, lookY, lookZ, 28.0F, 24.0F);
        }
        long alertTicks = Math.round(
                60.0 + needs.weatherReaction().sensitivity().alertness() * 100.0);
        if (gameTime - needs.actionStartedAt() >= alertTicks) {
            needs.idle();
            needs.scheduleEvaluation(gameTime + 20L + Math.floorMod(animal.getId(), 20));
        }
    }

    private void tickDrinking(long gameTime) {
        BlockPos water = needs.waterPosition();
        if (water != null) {
            animal.getLookControl().setLookAt(
                    water.getX() + 0.5, water.getY() + 0.25, water.getZ() + 0.5, 20.0F, 20.0F);
        }
        long startedAt = needs.interactionStartedAt() == 0L
                ? needs.actionStartedAt()
                : needs.interactionStartedAt();
        if (gameTime - startedAt >= activeProfile.drinking().durationTicks()) {
            needs.restoreThirst(activeProfile.drinking().thirstRestored());
            needs.idle();
        }
    }

    private void tickSeekingShelter(long gameTime) {
        BlockPos target = needs.behaviorTarget();
        if (target != null
                && animal.distanceToSqr(target.getCenter()) <= 2.25
                && !animal.level().canSeeSky(animal.blockPosition().above())) {
            animal.getNavigation().stop();
            if (needs.behavior() == EcosystemBehaviorState.SEEK_SHELTER) {
                needs.markInteractionStarted(gameTime);
            } else {
                needs.begin(EcosystemBehaviorState.SHELTERING, target, gameTime);
            }
            return;
        }
        stopFailedOrTimedOut(gameTime);
    }

    private void tickSheltering(ServerLevel level, long gameTime) {
        animal.getNavigation().stop();
        if (gameTime % 40L != Math.floorMod(animal.getId(), 40)) {
            return;
        }
        WeatherSample weather = WeatherServices.query().sample(level, animal.blockPosition());
        WeatherReactionDecision reaction = EcosystemServices.stormReactions().assess(
                animal,
                activeProfile,
                EcosystemConfig.MAXIMUM_SEARCH_RADIUS.get()
        );
        needs.rememberWeatherReaction(reaction);
        boolean hazardous = weather.precipitationIntensity() >= activeProfile.shelter().precipitationThreshold()
                || weather.thunderIntensity() >= activeProfile.shelter().thunderThreshold()
                || weather.wind().magnitude() >= activeProfile.shelter().windThreshold()
                || reaction.response().active();
        if (hazardous) {
            needs.setShelterReleaseAt(Long.MAX_VALUE);
            return;
        }
        if (needs.shelterReleaseAt() == Long.MAX_VALUE) {
            int minimum = activeProfile.shelter().minimumReleaseDelayTicks();
            int maximum = Math.max(minimum, activeProfile.shelter().maximumReleaseDelayTicks());
            int delay = minimum + animal.getRandom().nextInt(maximum - minimum + 1);
            needs.setShelterReleaseAt(gameTime + delay);
        } else if (gameTime >= needs.shelterReleaseAt()) {
            needs.idle();
        }
    }

    private void tickBoundedMovement(long gameTime) {
        BlockPos target = needs.behaviorTarget();
        EcosystemBehaviorState completedState = needs.behavior();
        if (target == null
                || animal.distanceToSqr(target.getCenter()) <= 4.0
                || gameTime - needs.actionStartedAt() >= 120L
                || (animal.getNavigation().isDone() && gameTime - needs.actionStartedAt() > 20L)) {
            if (completedState == EcosystemBehaviorState.FORAGE
                    && needs.huntTargetId() == null
                    && target != null
                    && animal.distanceToSqr(target.getCenter()) <= 4.0) {
                needs.restoreHunger(0.12);
            }
            needs.idle();
            needs.scheduleEvaluation(gameTime + 5L + Math.floorMod(animal.getId(), 12));
        }
    }

    private void tickResting(long gameTime) {
        animal.getNavigation().stop();
        int duration = needs.behavior() == EcosystemBehaviorState.SLEEP
                ? activeProfile.environment().sleepDurationTicks()
                : activeProfile.environment().restDurationTicks();
        if (gameTime - needs.actionStartedAt() >= duration) {
            needs.recoverRest(needs.behavior() == EcosystemBehaviorState.SLEEP ? 0.45 : 0.20);
            needs.idle();
        }
    }

    private void tickHunting(ServerLevel level, long gameTime) {
        if (huntingTarget == null) {
            huntingTarget = findLivingEntity(needs.huntTargetId());
        }
        if (huntingTarget == null) {
            needs.setNextHuntAllowedAt(gameTime + activeProfile.predator().huntCooldownTicks() / 4L);
            needs.idle();
            return;
        }
        if (!huntingTarget.isAlive()) {
            needs.satisfyHunger(0.15);
            needs.setNextHuntAllowedAt(gameTime + activeProfile.predator().huntCooldownTicks());
            needs.idle();
            return;
        }
        double maximumDistance = activeProfile.predator().huntRadius() * 1.5;
        if (animal.distanceToSqr(huntingTarget) > maximumDistance * maximumDistance
                || gameTime - needs.actionStartedAt() >= MAXIMUM_ACTION_TICKS) {
            needs.setNextHuntAllowedAt(gameTime + activeProfile.predator().huntCooldownTicks() / 4L);
            needs.idle();
            return;
        }
        animal.getLookControl().setLookAt(huntingTarget, 30.0F, 30.0F);
        if (gameTime >= nextNavigationUpdateAt) {
            animal.getNavigation().moveTo(huntingTarget, activeProfile.predator().moveSpeed());
            nextNavigationUpdateAt = gameTime + 10L;
        }
        if (attackDelay > 0) {
            attackDelay--;
        }
        double reach = animal.getBbWidth() * 2.0F + huntingTarget.getBbWidth();
        if (attackDelay <= 0
                && animal.distanceToSqr(huntingTarget) <= reach * reach
                && animal.hasLineOfSight(huntingTarget)) {
            animal.doHurtTarget(huntingTarget);
            attackDelay = activeProfile.predator().attackIntervalTicks();
        }
    }

    private void stopFailedOrTimedOut(long gameTime) {
        if (gameTime - needs.actionStartedAt() >= MAXIMUM_ACTION_TICKS
                || (animal.getNavigation().isDone() && gameTime - needs.actionStartedAt() > 20L)) {
            needs.idle();
            needs.scheduleEvaluation(gameTime + 100L + Math.floorMod(animal.getId(), 40));
        }
    }

    private double movementSpeed(EcosystemBehaviorState behavior) {
        return switch (behavior) {
            case DRINK, SEEKING_WATER -> activeProfile.drinking().moveSpeed();
            case SEEK_SHELTER, SEEKING_SHELTER -> activeProfile.shelter().moveSpeed();
            case FLEE, FLEEING -> activeProfile.prey().enabled()
                    ? activeProfile.prey().fleeSpeed()
                    : activeProfile.herd().moveSpeed();
            case TRAVEL, MIGRATE, REGROUPING -> activeProfile.herd().moveSpeed();
            case FORAGE, HUNTING -> activeProfile.predator().enabled()
                    ? activeProfile.predator().moveSpeed()
                    : activeProfile.herd().moveSpeed();
            default -> 1.0;
        };
    }

    private long evaluationInterval(ServerLevel level) {
        long base = EcosystemConfig.BEHAVIOR_UPDATE_FREQUENCY.get();
        WildlifeSimulationLod simulationLod = WildlifeSimulationLod.ACTIVE;
        if (EcosystemConfig.SIMULATION_ZONES_ENABLED.get()) {
            simulationLod = EcosystemSimulationManager.get()
                    .getSimulationLevel(level, animal.blockPosition());
        } else if (level.getNearestPlayer(animal, EcosystemConfig.FAR_ANIMAL_DISTANCE.get()) == null) {
            simulationLod = WildlifeSimulationLod.NEAR;
        }
        return WildlifeSimulationLodPolicy.staggeredInterval(
                base,
                simulationLod,
                EcosystemConfig.FAR_ANIMAL_UPDATE_MULTIPLIER.get(),
                EcosystemConfig.DISTANT_ANIMAL_UPDATE_MULTIPLIER.get(),
                EcosystemConfig.DORMANT_ANIMAL_UPDATE_MULTIPLIER.get(),
                animal.getUUID()
        );
    }

    private void inheritGroupDecision(
            AnimalGroup group,
            long gameTime,
            WildlifeSimulationLod simulationLod
    ) {
        EcosystemBehaviorState inherited = switch (group.state()) {
            case ALERT -> EcosystemBehaviorState.ALERT;
            case TRAVEL -> EcosystemBehaviorState.TRAVEL;
            case FLEE -> EcosystemBehaviorState.FLEE;
            case SEEK_WATER -> EcosystemBehaviorState.DRINK;
            case SEEK_SHELTER -> EcosystemBehaviorState.SEEK_SHELTER;
            case FEED -> EcosystemBehaviorState.FORAGE;
            case REST -> EcosystemBehaviorState.REST;
            case IDLE -> EcosystemBehaviorState.IDLE;
        };
        if (!activeProfile.environment().supports(inherited)
                && inherited != EcosystemBehaviorState.ALERT) {
            inherited = EcosystemBehaviorState.IDLE;
        }
        if (inherited == EcosystemBehaviorState.IDLE) {
            needs.idle();
            needs.setDiagnostics(
                    "inherited idle group state",
                    needs.weatherResponse(),
                    "follower members=" + group.memberCount(),
                    simulationLod
            );
        } else {
            needs.begin(
                    inherited,
                    group.destination(),
                    gameTime,
                    "inherited leader decision",
                    needs.weatherResponse(),
                    "follower members=" + group.memberCount(),
                    simulationLod
            );
        }
    }

    private LivingEntity findLivingEntity(UUID id) {
        if (id == null || !(animal.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    private boolean isWaterPresent(BlockPos position) {
        if (position == null) {
            return false;
        }
        return WaterServices.access().isWaterAt(animal.level(), position)
                || animal.level().getFluidState(position).is(FluidTags.WATER);
    }
}
