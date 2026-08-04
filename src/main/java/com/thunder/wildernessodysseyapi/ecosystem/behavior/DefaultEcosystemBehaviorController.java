package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorController;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
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
            needs.begin(EcosystemBehaviorState.FLEEING, target, context.gameTime());
            EcosystemServices.threats().warnHerd(
                    context.animal(), profile, threat, profile.prey().propagationRadius());
            return true;
        }

        if (EcosystemConfig.WEATHER_SHELTER_ENABLED.get()
                && profile.shelter().enabled()
                && hazardousWeather(context, profile.shelter())
                && context.exposedToSky()
                && context.shelter().isPresent()) {
            EnvironmentalContext.ShelterTarget shelter = context.shelter().get();
            needs.setShelterPosition(shelter.position());
            needs.begin(EcosystemBehaviorState.SEEKING_SHELTER, shelter.position(), context.gameTime());
            return true;
        }

        if (profile.drinking().enabled()
                && needs.thirst() >= profile.drinking().thirstThreshold()
                && context.water().isPresent()) {
            EnvironmentalContext.WaterTarget water = context.water().get();
            needs.setWaterPosition(water.waterPosition());
            needs.begin(EcosystemBehaviorState.SEEKING_WATER, water.approachPosition(), context.gameTime());
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
                needs.begin(EcosystemBehaviorState.HUNTING, prey.position(), context.gameTime());
                return true;
            }
        }

        if (EcosystemConfig.HERD_BEHAVIOR_ENABLED.get()
                && profile.herd().enabled()
                && needs.social() >= profile.herd().motivationThreshold()
                && context.herd().isPresent()
                && context.herd().get().distanceSquared()
                > profile.herd().preferredDistance() * profile.herd().preferredDistance()) {
            BlockPos center = context.herd().get().position();
            needs.begin(EcosystemBehaviorState.REGROUPING, center, context.gameTime());
            return true;
        }

        needs.idle();
        return false;
    }

    /** Returns whether the localized sample exceeds any profile shelter threshold. */
    public static boolean hazardousWeather(
            EnvironmentalContext context,
            SpeciesBehaviorProfile.Shelter shelter
    ) {
        return context.weather().precipitationIntensity() >= shelter.precipitationThreshold()
                || context.weather().thunderIntensity() >= shelter.thunderThreshold()
                || context.weather().wind().magnitude() >= shelter.windThreshold();
    }

    private static BlockPos fallbackAway(BlockPos origin, BlockPos threat, int distance) {
        int dx = Integer.compare(origin.getX(), threat.getX());
        int dz = Integer.compare(origin.getZ(), threat.getZ());
        if (dx == 0 && dz == 0) {
            dx = 1;
        }
        return origin.offset(dx * distance, 0, dz * distance);
    }
}
