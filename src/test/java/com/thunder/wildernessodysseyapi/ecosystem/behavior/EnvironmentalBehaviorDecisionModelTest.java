package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeWeatherResponse;
import com.thunder.wildernessodysseyapi.ecosystem.data.BehaviorTagProfileFactory;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies representative species routines without constructing entities or scanning a world. */
class EnvironmentalBehaviorDecisionModelTest {

    @Test
    void crepuscularHerbivoreForagesAtDawnRestsAtMiddayAndSleepsAtNight() {
        SpeciesBehaviorProfile deer = profile("deer", AnimalBehaviorTag.HERBIVORE);

        assertEquals(EcosystemBehaviorState.FORAGE, decide(
                deer, WildlifeSchedule.Period.ACTIVE, false, 0.55, 0.80, false).state());
        assertEquals(EcosystemBehaviorState.REST, decide(
                deer, WildlifeSchedule.Period.REST, true, 0.55, 0.80, false).state());
        assertEquals(EcosystemBehaviorState.SLEEP, decide(
                deer, WildlifeSchedule.Period.SLEEP, false, 0.55, 0.80, false).state());
    }

    @Test
    void nocturnalPredatorSleepsByDayAndForagesWhenActive() {
        SpeciesBehaviorProfile wolf = profile("wolf", AnimalBehaviorTag.WOLF);

        assertEquals(EcosystemBehaviorState.SLEEP, decide(
                wolf, WildlifeSchedule.Period.SLEEP, false, 0.80, 0.70, false).state());
        assertEquals(EcosystemBehaviorState.FORAGE, decide(
                wolf, WildlifeSchedule.Period.ACTIVE, false, 0.80, 0.70, false).state());
    }

    @Test
    void hotDryConditionsLowerDrinkThresholdWithoutForcingEverySpeciesState() {
        SpeciesBehaviorProfile herbivore = profile("hot_deer", AnimalBehaviorTag.HERBIVORE);
        EnvironmentalBehaviorDecisionModel.Signals hotDry = signals(
                WildlifeSchedule.Period.ACTIVE,
                false,
                WildlifeWeatherResponse.CLEAR,
                true,
                true,
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                false,
                0.50,
                0.10,
                0.10,
                0.80
        );

        assertEquals(EcosystemBehaviorState.DRINK,
                EnvironmentalBehaviorDecisionModel.decide(herbivore, hotDry).state());

        SpeciesBehaviorProfile aquatic = profile("fish", AnimalBehaviorTag.AQUATIC);
        assertEquals(EcosystemBehaviorState.IDLE,
                EnvironmentalBehaviorDecisionModel.decide(aquatic, hotDry).state());
    }

    @Test
    void coldConditionsUseThePreferredMinimumTemperatureWhenShelterIsSupported() {
        SpeciesBehaviorProfile herbivore = profile("cold_deer", AnimalBehaviorTag.HERBIVORE);
        EnvironmentalBehaviorDecisionModel.Signals cold = signals(
                WildlifeSchedule.Period.ACTIVE,
                false,
                WildlifeWeatherResponse.CLEAR,
                true,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                0.0,
                0.0,
                0.0,
                0.8
        );

        assertEquals(EcosystemBehaviorState.SEEK_SHELTER,
                EnvironmentalBehaviorDecisionModel.decide(herbivore, cold).state());
    }

    @Test
    void seriousWeatherAndThreatsOutrankRoutineActivity() {
        SpeciesBehaviorProfile bird = profile("bird", AnimalBehaviorTag.BIRD);
        EnvironmentalBehaviorDecisionModel.Signals storm = signals(
                WildlifeSchedule.Period.ACTIVE,
                false,
                WildlifeWeatherResponse.THUNDERSTORM,
                true,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                true,
                0.0,
                0.8,
                0.8,
                0.8
        );
        assertEquals(EcosystemBehaviorState.SEEK_SHELTER,
                EnvironmentalBehaviorDecisionModel.decide(bird, storm).state());

        EnvironmentalBehaviorDecisionModel.Signals threat = new EnvironmentalBehaviorDecisionModel.Signals(
                storm.schedulePeriod(), storm.midday(), storm.weatherResponse(), storm.exposedToSky(),
                storm.waterAvailable(), storm.shelterAvailable(), true, storm.regroupNeeded(),
                storm.groupLeader(), storm.cold(), storm.hotOrDry(), storm.disturbancePresent(),
                storm.routineActivityPulse(), storm.thirst(), storm.hunger(), storm.rest(),
                storm.foodAvailability());
        assertEquals(EcosystemBehaviorState.FLEE,
                EnvironmentalBehaviorDecisionModel.decide(bird, threat).state());
    }

    private static SpeciesBehaviorProfile profile(String path, AnimalBehaviorTag... tags) {
        return BehaviorTagProfileFactory.create(
                ResourceLocation.fromNamespaceAndPath("test", path), Set.of(tags));
    }

    private static EnvironmentalBehaviorDecisionModel.Decision decide(
            SpeciesBehaviorProfile profile,
            WildlifeSchedule.Period period,
            boolean midday,
            double hunger,
            double food,
            boolean hotDry
    ) {
        return EnvironmentalBehaviorDecisionModel.decide(
                profile,
                signals(
                        period, midday, WildlifeWeatherResponse.CLEAR,
                        true, false, false, false, false, true, false, hotDry,
                        false, false, 0.0, hunger, 0.0, food
                )
        );
    }

    private static EnvironmentalBehaviorDecisionModel.Signals signals(
            WildlifeSchedule.Period period,
            boolean midday,
            WildlifeWeatherResponse weather,
            boolean exposed,
            boolean water,
            boolean shelter,
            boolean threat,
            boolean regroup,
            boolean leader,
            boolean cold,
            boolean hotDry,
            boolean disturbance,
            boolean routinePulse,
            double thirst,
            double hunger,
            double rest,
            double food
    ) {
        return new EnvironmentalBehaviorDecisionModel.Signals(
                period, midday, weather, exposed, water, shelter, threat, regroup,
                leader, cold, hotDry, disturbance, routinePulse, thirst, hunger, rest, food);
    }
}
