package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.ActivityTime;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies deterministic schedule offsets and large-population decision staggering. */
class WildlifeScheduleAndLodTest {

    @Test
    void activityTypesUseExpectedBroadWindows() {
        assertEquals(WildlifeSchedule.Period.ACTIVE,
                WildlifeSchedule.period(ActivityTime.DIURNAL, 6_000L, 0));
        assertEquals(WildlifeSchedule.Period.SLEEP,
                WildlifeSchedule.period(ActivityTime.DIURNAL, 18_000L, 0));
        assertEquals(WildlifeSchedule.Period.SLEEP,
                WildlifeSchedule.period(ActivityTime.NOCTURNAL, 6_000L, 0));
        assertEquals(WildlifeSchedule.Period.ACTIVE,
                WildlifeSchedule.period(ActivityTime.NOCTURNAL, 18_000L, 0));
        assertEquals(WildlifeSchedule.Period.REST,
                WildlifeSchedule.period(ActivityTime.CREPUSCULAR, 6_000L, 0));
        assertEquals(WildlifeSchedule.Period.ACTIVE,
                WildlifeSchedule.period(ActivityTime.FLEXIBLE, 18_000L, 0));
    }

    @Test
    void tenThousandAnimalsSpreadMajorDecisionsInsteadOfSynchronizing() {
        Map<Long, Integer> decisionsPerInterval = new HashMap<>();
        for (int index = 0; index < 10_000; index++) {
            UUID id = new UUID(0x1357_9BDFL * index, 0x2468_ACE1L * (index + 1L));
            long interval = WildlifeSimulationLodPolicy.staggeredInterval(
                    80L, WildlifeSimulationLod.ACTIVE, 6, 30, 120, id);
            decisionsPerInterval.merge(interval, 1, Integer::sum);
        }

        assertTrue(decisionsPerInterval.size() >= 20,
                "deterministic jitter should distribute a large population across many ticks");
        assertTrue(decisionsPerInterval.values().stream().mapToInt(Integer::intValue).max().orElseThrow() < 650,
                "no single decision tick should receive most of the population");
        assertTrue(WildlifeSimulationLodPolicy.staggeredInterval(
                80L, WildlifeSimulationLod.DORMANT, 6, 30, 120, UUID.randomUUID()) >= 9_600L);
    }
}
