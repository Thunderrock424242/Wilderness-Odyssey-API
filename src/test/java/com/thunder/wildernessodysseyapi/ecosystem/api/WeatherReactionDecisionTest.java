package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.weather.api.WeatherThreat;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherReactionDecisionTest {

    @Test
    void followerRetainsLeaderForecastResponseAndTarget() {
        UUID leader = UUID.randomUUID();
        EnvironmentalContext.ShelterTarget shelter = new EnvironmentalContext.ShelterTarget(
                new net.minecraft.core.BlockPos(4, 65, 8),
                3
        );
        WeatherReactionDecision leaderDecision = new WeatherReactionDecision(
                new WeatherThreatForecast(
                        WeatherThreat.SEVERE_STORM, 0.84, 450.0, 2_000L, 0.9,
                        4L, WeatherSystemType.STORM, WeatherSystemStage.MATURE),
                StormSensitivity.HERD,
                StormReaction.SEEK_SHELTER,
                Optional.of(shelter),
                leader,
                20,
                false
        );

        WeatherReactionDecision follower = leaderDecision.asInherited();

        assertFalse(leaderDecision.inherited());
        assertTrue(follower.inherited());
        assertEquals(leader, follower.decisionMakerId());
        assertEquals(20, follower.groupSize());
        assertEquals(leaderDecision.forecast(), follower.forecast());
        assertEquals(Optional.of(shelter), follower.shelter());
    }
}
