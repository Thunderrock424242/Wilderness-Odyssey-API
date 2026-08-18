package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorController;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.api.StormReaction;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeWeatherResponse;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemoryManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/** Priority-ordered decision policy for the initial ecosystem behaviors. */
public final class DefaultEcosystemBehaviorController implements EcosystemBehaviorController {

    @Override
    public boolean evaluate(EnvironmentalContext context, AnimalNeedsState needs) {
        SpeciesBehaviorProfile profile = context.profile();
        WildlifeWeatherResponse weatherResponse = EnvironmentalBehaviorDecisionModel.classifyWeather(
                context.weather(), context.watershed(), profile.shelter());
        String groupState = groupState(context);

        // Immediate danger owns movement before slower physiological needs.
        if (profile.prey().enabled() && context.threat().isPresent()) {
            EnvironmentalContext.Threat threat = context.threat().get();
            Vec3 away = DefaultRandomPos.getPosAway(
                    context.animal(),
                    Math.max(8, profile.prey().threatRadius()),
                    7,
                    threat.position().getCenter()
            );
            BlockPos target = away == null
                    ? fallbackAway(context.animal().blockPosition(), threat.position(), 10)
                    : BlockPos.containing(away);
            select(context, needs, EcosystemBehaviorState.FLEE, target,
                    "nearby or remembered threat", weatherResponse, groupState);
            EcosystemServices.threats().warnHerd(
                    context.animal(), profile, threat, profile.prey().propagationRadius());
            return true;
        }

        // Persistent high disturbance makes wild profiled animals leave the cell,
        // while tame companions remain under their owner's normal vanilla AI.
        if (!(context.animal() instanceof TamableAnimal tamable && tamable.isTame())
                && context.disturbance().isPresent()
                && WildlifeDisturbancePolicy.stronglyAvoided(context.disturbance().get().intensity())) {
            EnvironmentalContext.Disturbance disturbance = context.disturbance().get();
            Vec3 away = DefaultRandomPos.getPosAway(
                    context.animal(),
                    Math.max(8, profile.prey().threatRadius()),
                    7,
                    disturbance.position().getCenter()
            );
            BlockPos target = away == null
                    ? fallbackAway(context.animal().blockPosition(), disturbance.position(), 10)
                    : BlockPos.containing(away);
            select(context, needs, EcosystemBehaviorState.FLEE, target,
                    "strong regional disturbance", weatherResponse, groupState);
            return true;
        }

        // The shared pre-storm service has already made this decision once for
        // a herd/flock leader and supplied the same bounded shelter target to
        // followers. Animals already under cover hold their current position.
        if (EcosystemConfig.PRE_STORM_REACTIONS_ENABLED.get()
                && profile.shelter().enabled()
                && context.weatherReaction().response() == StormReaction.SEEK_SHELTER
                && context.weatherReaction().shelter().isPresent()) {
            EnvironmentalContext.ShelterTarget shelter = context.exposedToSky()
                    ? context.weatherReaction().shelter().get()
                    : new EnvironmentalContext.ShelterTarget(context.animal().blockPosition(), 0);
            if (!context.exposedToSky() || destinationAccepted(context, shelter.position())) {
                needs.setShelterPosition(shelter.position());
                select(
                        context,
                        needs,
                        context.exposedToSky()
                                ? EcosystemBehaviorState.SEEK_SHELTER
                                : EcosystemBehaviorState.REST,
                        shelter.position(),
                        "approaching " + context.weatherReaction().forecast().type().name().toLowerCase(java.util.Locale.ROOT),
                        weatherResponse,
                        groupState
                );
                return true;
            }
        }

        if (EcosystemConfig.WEATHER_SHELTER_ENABLED.get()
                && profile.shelter().enabled()
                && hazardousWeather(context, profile.shelter())
                && context.exposedToSky()
                && context.shelter().isPresent()
                && destinationAccepted(context, context.shelter().get().position())) {
            EnvironmentalContext.ShelterTarget shelter = context.shelter().get();
            needs.setShelterPosition(shelter.position());
            select(context, needs, EcosystemBehaviorState.SEEK_SHELTER, shelter.position(),
                    "localized " + weatherResponse.serializedName(), weatherResponse, groupState);
            return true;
        }

        if (EcosystemConfig.PRE_STORM_REACTIONS_ENABLED.get()
                && context.weatherReaction().response().active()) {
            select(context, needs, EcosystemBehaviorState.ALERT, null,
                    "approaching weather alert", weatherResponse, groupState);
            return true;
        }

        boolean wildOrAllowed = !(context.animal() instanceof TamableAnimal tamable)
                || !profile.predator().wildOnly()
                || !tamable.isTame();
        if (profile.predator().enabled() && context.preyTarget().isPresent()) {
            EnvironmentalContext.PreyTarget prey = context.preyTarget().get();
            if (PredatorHuntingPolicy.mayHunt(
                    EcosystemConfig.PREDATOR_HUNTING_ENABLED.get(),
                    wildOrAllowed,
                    context.animal().getTarget() != null,
                    needs.hunger(),
                    profile.predator().hungerThreshold(),
                    context.gameTime(),
                    needs.nextHuntAllowedAt(),
                    prey.adultPopulation(),
                    profile.predator().minimumNearbyPrey())) {
                needs.setHuntTargetId(prey.entityId());
                select(context, needs, EcosystemBehaviorState.FORAGE, prey.position(),
                        "hunger-gated prey forage", weatherResponse, groupState);
                return true;
            }
        }

        int scheduleOffset = WildlifeSchedule.deterministicOffset(
                context.animal().getUUID(), profile.environment().scheduleJitterTicks());
        WildlifeSchedule.Period schedule = WildlifeSchedule.period(
                profile.environment().activeTime(), context.dayTime(), scheduleOffset);
        boolean regroupNeeded = EcosystemConfig.HERD_BEHAVIOR_ENABLED.get()
                && profile.herd().enabled()
                && needs.social() >= profile.herd().motivationThreshold()
                && context.herd().isPresent()
                && context.herd().get().distanceSquared()
                > profile.herd().preferredDistance() * profile.herd().preferredDistance();
        boolean cold = context.weather().temperature()
                < profile.environment().preferredMinimumTemperatureCelsius();
        boolean hotOrDry = context.weather().temperature()
                > profile.environment().preferredMaximumTemperatureCelsius()
                || (context.weather().humidity() < 0.30 && context.watershed().soilSaturation() < 0.25f);
        boolean routinePulse = Math.floorMod(
                context.gameTime() / Math.max(40L, EcosystemConfig.BEHAVIOR_UPDATE_FREQUENCY.get())
                        + context.animal().getUUID().getLeastSignificantBits(),
                3L
        ) == 0L && context.regionalEnvironment().influence().wildlifeActivity() >= 0.22;
        EnvironmentalBehaviorDecisionModel.Decision decision = EnvironmentalBehaviorDecisionModel.decide(
                profile,
                new EnvironmentalBehaviorDecisionModel.Signals(
                        schedule,
                        WildlifeSchedule.isMidday(context.dayTime(), scheduleOffset),
                        weatherResponse,
                        context.exposedToSky(),
                        context.water().isPresent(),
                        context.shelter().isPresent(),
                        context.threat().isPresent(),
                        regroupNeeded,
                        context.herd().map(EnvironmentalContext.HerdCenter::leader).orElse(true),
                        cold,
                        hotOrDry,
                        context.disturbance().isPresent(),
                        context.regionalEnvironment().influence().migrationPressure(),
                        routinePulse,
                        needs.thirst(),
                        needs.hunger(),
                        needs.rest(),
                        context.foodAvailability()
                )
        );

        if (decision.state() == EcosystemBehaviorState.DRINK && context.water().isPresent()) {
            EnvironmentalContext.WaterTarget water = context.water().get();
            if (destinationAccepted(context, water.approachPosition())) {
                needs.setWaterPosition(water.waterPosition());
                select(context, needs, EcosystemBehaviorState.DRINK, water.approachPosition(),
                        decision.reason(), weatherResponse, groupState);
                return true;
            }
        }
        if (decision.state() == EcosystemBehaviorState.REST
                || decision.state() == EcosystemBehaviorState.SLEEP) {
            select(context, needs, decision.state(), null,
                    decision.reason(), weatherResponse, groupState);
            return true;
        }
        if (decision.state() == EcosystemBehaviorState.FORAGE
                || decision.state() == EcosystemBehaviorState.TRAVEL
                || decision.state() == EcosystemBehaviorState.MIGRATE) {
            BlockPos target = routineDestination(context, decision.state(), regroupNeeded);
            if (target != null && destinationAccepted(context, target)) {
                select(context, needs, decision.state(), target,
                        decision.reason(), weatherResponse, groupState);
                return true;
            }
        }

        needs.idle();
        needs.setDiagnostics(decision.reason(), weatherResponse, groupState, needs.simulationLod());
        return false;
    }

    /** Returns whether the localized sample exceeds any profile shelter threshold. */
    public static boolean hazardousWeather(
            EnvironmentalContext context,
            SpeciesBehaviorProfile.Shelter shelter
    ) {
        return context.weather().precipitationIntensity() >= shelter.precipitationThreshold()
                || context.weather().thunderIntensity() >= shelter.thunderThreshold()
                || context.weather().wind().magnitude() >= shelter.windThreshold()
                || context.watershed().flooding()
                || context.watershed().floodRisk() >= 0.82f
                || context.regionalEnvironment().influence().shelterPressure() >= 0.72;
    }

    private static BlockPos fallbackAway(BlockPos origin, BlockPos threat, int distance) {
        int dx = Integer.compare(origin.getX(), threat.getX());
        int dz = Integer.compare(origin.getZ(), threat.getZ());
        if (dx == 0 && dz == 0) {
            dx = 1;
        }
        return origin.offset(dx * distance, 0, dz * distance);
    }

    private static boolean destinationAccepted(EnvironmentalContext context, BlockPos destination) {
        return !WildlifeDisturbancePolicy.stronglyAvoided(
                EnvironmentalMemoryManager.getDisturbance(context.level(), destination));
    }

    private static BlockPos routineDestination(
            EnvironmentalContext context,
            EcosystemBehaviorState state,
            boolean regroupNeeded
    ) {
        if (regroupNeeded && context.herd().isPresent()) {
            return context.herd().get().position();
        }
        int radius = state == EcosystemBehaviorState.MIGRATE
                ? context.profile().environment().migrationRadius()
                : context.profile().environment().localTravelRadius();
        if (context.disturbance().isPresent()) {
            Vec3 away = DefaultRandomPos.getPosAway(
                    context.animal(), radius, 7, context.disturbance().get().position().getCenter());
            if (away != null) {
                return BlockPos.containing(away);
            }
        }
        Vec3 random = DefaultRandomPos.getPos(context.animal(), radius, 7);
        return random == null ? null : BlockPos.containing(random);
    }

    private static void select(
            EnvironmentalContext context,
            AnimalNeedsState needs,
            EcosystemBehaviorState state,
            BlockPos target,
            String reason,
            WildlifeWeatherResponse weatherResponse,
            String groupState
    ) {
        needs.begin(
                state,
                target,
                context.gameTime(),
                reason,
                weatherResponse,
                groupState,
                needs.simulationLod()
        );
    }

    private static String groupState(EnvironmentalContext context) {
        return context.herd()
                .map(group -> (group.leader() ? "leader" : "follower") + " members=" + group.members())
                .orElse("individual");
    }
}
