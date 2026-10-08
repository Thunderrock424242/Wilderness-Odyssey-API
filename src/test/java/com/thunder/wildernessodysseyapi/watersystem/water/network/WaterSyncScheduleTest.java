package com.thunder.wildernessodysseyapi.watersystem.water.network;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaterSyncScheduleTest {
    @Test
    void continuousChangesCannotStarveTheNinthChunk() {
        var schedule = new WaterSyncSchedule();
        List<WaterSyncSchedule.Candidate> candidates = new ArrayList<>();
        for (long key = 0; key < 9; key++) candidates.add(new WaterSyncSchedule.Candidate(key, (int) key, false));
        Set<Long> delivered = new HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (long key : schedule.order(candidates, pass * 20).subList(0, 8)) {
                delivered.add(key);
                schedule.visited(key);
            }
        }
        assertEquals(Set.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), delivered);
    }

    @Test
    void nearestMissingBaselineGetsPriorityAndPendingAgeSurvivesPartialDelivery() {
        var schedule = new WaterSyncSchedule();
        var candidates = List.of(new WaterSyncSchedule.Candidate(5, 20, true),
                new WaterSyncSchedule.Candidate(8, 1, true), new WaterSyncSchedule.Candidate(9, 0, false));
        assertEquals(8L, schedule.order(candidates, 100).getFirst());
        schedule.visited(8);
        schedule.order(candidates, 160);
        assertEquals(3, schedule.backlog());
        assertEquals(60, schedule.oldestPendingTicks(160));
        schedule.order(List.of(candidates.getLast()), 200);
        assertEquals(1, schedule.backlog());
        assertEquals(100, schedule.oldestPendingTicks(200));
        schedule.order(List.of(), 220);
        assertEquals(0, schedule.backlog());
        assertEquals(0, schedule.oldestPendingTicks(220));
    }
}
