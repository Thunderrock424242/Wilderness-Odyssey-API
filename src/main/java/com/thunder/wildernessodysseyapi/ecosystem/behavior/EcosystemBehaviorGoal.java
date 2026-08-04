package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.FoodAvailabilityService;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
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
        Optional<SpeciesBehaviorProfile> resolved = SpeciesBehaviorProfileManager.profileFor(animal);
        if (resolved.isEmpty()) {
            return false;
        }
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        double speciesMultiplier = EcosystemConfig.speciesMultiplier(entityId);
        if (speciesMultiplier <= 0.0) {
            return false;
        }
        if (!EcosystemServices.budget().tryAcquire(level, gameTime)) {
            needs.scheduleEvaluation(gameTime + 5L + Math.floorMod(animal.getId(), 16));
            return false;
        }

        long started = System.nanoTime();
        activeProfile = resolved.get();
        EnvironmentalContext context = buildContext(level, activeProfile, speciesMultiplier, gameTime);
        boolean selected = controller.evaluate(context, needs);
        long nextEvaluation = gameTime + evaluationInterval(level);
        needs.recordEvaluation(gameTime, System.nanoTime() - started, nextEvaluation);
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
        if (behavior == EcosystemBehaviorState.HUNTING) {
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
        switch (needs.behavior()) {
            case SEEKING_WATER -> tickSeekingWater(gameTime);
            case DRINKING -> tickDrinking(gameTime);
            case SEEKING_SHELTER -> tickSeekingShelter(gameTime);
            case SHELTERING -> tickSheltering(level, gameTime);
            case FLEEING, REGROUPING -> tickBoundedMovement(gameTime);
            case HUNTING -> tickHunting(level, gameTime);
            case IDLE -> {
            }
        }
    }

    @Override
    public void stop() {
        animal.getNavigation().stop();
        if (needs.behavior() == EcosystemBehaviorState.HUNTING
                && animal.level() instanceof ServerLevel level
                && activeProfile != null) {
            needs.setNextHuntAllowedAt(level.getGameTime() + activeProfile.predator().huntCooldownTicks() / 4L);
        }
        if (huntingTarget != null && animal.getTarget() == huntingTarget) {
            animal.setTarget(null);
        }
        huntingTarget = null;
        if (needs.behavior() != EcosystemBehaviorState.IDLE) {
            needs.idle();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private EnvironmentalContext buildContext(
            ServerLevel level,
            SpeciesBehaviorProfile profile,
            double speciesMultiplier,
            long gameTime
    ) {
        int radiusCap = EcosystemConfig.MAXIMUM_SEARCH_RADIUS.get();
        WeatherSample weather = WeatherServices.query().sample(level, animal.blockPosition());
        boolean exposed = level.canSeeSky(animal.blockPosition().above());
        Optional<EnvironmentalContext.Disturbance> disturbance = EcosystemServices.disturbances().nearest(
                level, animal.blockPosition(), Math.min(radiusCap, 16), gameTime);
        Optional<EnvironmentalContext.Threat> threat = profile.prey().enabled()
                ? EcosystemServices.threats().assess(
                animal, profile, needs, Math.min(radiusCap, profile.prey().threatRadius()))
                : Optional.empty();
        Optional<EnvironmentalContext.HerdCenter> herd = profile.herd().enabled()
                ? EcosystemServices.herd().assess(animal, Math.min(radiusCap, profile.herd().searchRadius()))
                : Optional.empty();

        double foodAvailability = profile.predator().enabled()
                ? 0.0
                : EcosystemServices.food().availability(animal, Math.min(radiusCap, 12));
        FoodAvailabilityService.PredatorFoodSample prey = profile.predator().enabled()
                ? EcosystemServices.food().prey(animal, profile, Math.min(radiusCap, profile.predator().huntRadius()))
                : new FoodAvailabilityService.PredatorFoodSample(java.util.List.of(), Optional.empty());

        long elapsedTicks = needs.lastEvaluatedAt() == 0L
                ? EcosystemConfig.BEHAVIOR_UPDATE_FREQUENCY.get()
                : gameTime - needs.lastEvaluatedAt();
        boolean preferredActive = profile.needs().nocturnal() != level.isDay();
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

        boolean weatherHazard = weather.precipitationIntensity() >= profile.shelter().precipitationThreshold()
                || weather.thunderIntensity() >= profile.shelter().thunderThreshold()
                || weather.wind().magnitude() >= profile.shelter().windThreshold();
        Optional<EnvironmentalContext.WaterTarget> water = profile.drinking().enabled()
                && needs.thirst() >= profile.drinking().thirstThreshold()
                ? EcosystemServices.water().find(
                animal, profile, Math.min(radiusCap, profile.drinking().searchRadius()))
                : Optional.empty();
        Optional<EnvironmentalContext.ShelterTarget> shelter = profile.shelter().enabled()
                && EcosystemConfig.WEATHER_SHELTER_ENABLED.get()
                && weatherHazard
                && exposed
                ? EcosystemServices.shelter().find(
                animal, profile, Math.min(radiusCap, profile.shelter().searchRadius()))
                : Optional.empty();
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
                exposed,
                foodAvailability,
                water,
                shelter,
                threat,
                herd,
                preyTarget,
                disturbance
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
            needs.begin(EcosystemBehaviorState.DRINKING, needs.waterPosition(), gameTime);
            return;
        }
        stopFailedOrTimedOut(gameTime);
    }

    private void tickDrinking(long gameTime) {
        BlockPos water = needs.waterPosition();
        if (water != null) {
            animal.getLookControl().setLookAt(
                    water.getX() + 0.5, water.getY() + 0.25, water.getZ() + 0.5, 20.0F, 20.0F);
        }
        if (gameTime - needs.actionStartedAt() >= activeProfile.drinking().durationTicks()) {
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
            needs.begin(EcosystemBehaviorState.SHELTERING, target, gameTime);
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
        boolean hazardous = weather.precipitationIntensity() >= activeProfile.shelter().precipitationThreshold()
                || weather.thunderIntensity() >= activeProfile.shelter().thunderThreshold()
                || weather.wind().magnitude() >= activeProfile.shelter().windThreshold();
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
        if (target == null
                || animal.distanceToSqr(target.getCenter()) <= 4.0
                || gameTime - needs.actionStartedAt() >= 120L
                || (animal.getNavigation().isDone() && gameTime - needs.actionStartedAt() > 20L)) {
            needs.idle();
            needs.scheduleEvaluation(gameTime + 5L + Math.floorMod(animal.getId(), 12));
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
            case SEEKING_WATER -> activeProfile.drinking().moveSpeed();
            case SEEKING_SHELTER -> activeProfile.shelter().moveSpeed();
            case FLEEING -> activeProfile.prey().fleeSpeed();
            case REGROUPING -> activeProfile.herd().moveSpeed();
            case HUNTING -> activeProfile.predator().moveSpeed();
            default -> 1.0;
        };
    }

    private long evaluationInterval(ServerLevel level) {
        long base = EcosystemConfig.BEHAVIOR_UPDATE_FREQUENCY.get();
        if (level.getNearestPlayer(animal, EcosystemConfig.FAR_ANIMAL_DISTANCE.get()) == null) {
            base *= EcosystemConfig.FAR_ANIMAL_UPDATE_MULTIPLIER.get();
        }
        long jitter = Math.floorMod(animal.getUUID().getLeastSignificantBits(), Math.max(1L, base / 3L));
        return Math.max(10L, base + jitter);
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
