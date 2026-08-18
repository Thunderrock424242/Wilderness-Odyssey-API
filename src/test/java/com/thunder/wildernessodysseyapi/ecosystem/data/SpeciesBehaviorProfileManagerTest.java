package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.ecosystem.api.ActivityTime;
import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies data-pack profiles receive safe defaults and bounded values. */
class SpeciesBehaviorProfileManagerTest {

    @Test
    void profileReloadParsesSelectorsAndClampsUnsafeBalanceValues() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "deer");
        SpeciesBehaviorProfileManager.apply(Map.of(id, JsonParser.parseString("""
                {
                  "entities": ["test:deer"],
                  "entity_tags": ["test:ecosystem/deer"],
                  "needs": {"thirst_per_minute": 5.0},
                  "drinking": {"search_radius": 500, "duration_ticks": 1},
                  "prey": {"enabled": true, "threat_tags": ["test:ecosystem/predators"]}
                }
                """)));

        assertEquals(1, SpeciesBehaviorProfileManager.profiles().size());
        SpeciesBehaviorProfile profile = SpeciesBehaviorProfileManager.profiles().getFirst();
        assertEquals(id, profile.id());
        assertTrue(profile.entities().contains(ResourceLocation.fromNamespaceAndPath("test", "deer")));
        assertTrue(profile.entityTags().contains(ResourceLocation.fromNamespaceAndPath("test", "ecosystem/deer")));
        assertEquals(1.0, profile.needs().thirstPerMinute());
        assertEquals(64, profile.drinking().searchRadius());
        assertEquals(20, profile.drinking().durationTicks());
        assertTrue(profile.prey().enabled());
    }

    @Test
    void profileReloadParsesEnvironmentalRoutineAndStateCapabilities() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "environmental_deer");
        SpeciesBehaviorProfileManager.apply(Map.of(id, JsonParser.parseString("""
                {
                  "entities": ["test:deer"],
                  "environment": {
                    "active_time": "crepuscular",
                    "preferred_temperature_min_celsius": -8.0,
                    "preferred_temperature_max_celsius": 26.0,
                    "schedule_jitter_ticks": 1400,
                    "supported_states": ["forage", "travel", "drink", "rest", "sleep", "seek_shelter", "flee", "migrate"]
                  }
                }
                """)));

        SpeciesBehaviorProfile profile = SpeciesBehaviorProfileManager.profiles().getFirst();
        assertEquals(ActivityTime.CREPUSCULAR, profile.environment().activeTime());
        assertEquals(-8.0, profile.environment().preferredMinimumTemperatureCelsius());
        assertEquals(26.0, profile.environment().preferredMaximumTemperatureCelsius());
        assertEquals(1_400, profile.environment().scheduleJitterTicks());
        assertTrue(profile.environment().supports(EcosystemBehaviorState.MIGRATE));
        assertTrue(profile.environment().supports(EcosystemBehaviorState.IDLE));
        assertFalse(profile.environment().supports(EcosystemBehaviorState.ALERT));
    }

    @Test
    void compatibilityModulesCanRegisterAnExplicitSpeciesProfile() {
        ResourceLocation entityId = ResourceLocation.fromNamespaceAndPath("test", "compatibility_deer");
        SpeciesBehaviorProfile profile = BehaviorTagProfileFactory.create(
                entityId,
                Set.of(AnimalBehaviorTag.HERBIVORE, AnimalBehaviorTag.CREPUSCULAR)
        );

        SpeciesBehaviorProfileManager.registerCompatibilityProfile(profile);

        assertTrue(SpeciesBehaviorProfileManager.compatibilityProfiles().contains(profile));
        assertThrows(IllegalArgumentException.class,
                () -> SpeciesBehaviorProfileManager.registerCompatibilityProfile(profile));
    }
}
