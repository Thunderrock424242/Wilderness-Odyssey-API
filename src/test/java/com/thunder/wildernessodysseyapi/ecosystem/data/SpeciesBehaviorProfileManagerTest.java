package com.thunder.wildernessodysseyapi.ecosystem.data;

import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
