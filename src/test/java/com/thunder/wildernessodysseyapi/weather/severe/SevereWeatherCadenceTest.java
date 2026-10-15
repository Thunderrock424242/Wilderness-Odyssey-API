package com.thunder.wildernessodysseyapi.weather.severe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SevereWeatherCadenceTest {

    @Test
    void everySystemReceivesOneOpportunityOnActualSchedulerSlots() {
        for (long id = 1; id <= 10; id++) {
            int opportunities = 0;
            for (long tick = 0; tick < 1_200; tick += 10) {
                if (SevereWeatherScheduler.disturbanceDue(tick, id)) opportunities++;
            }
            assertEquals(1, opportunities, "Missing or repeated disturbance opportunity for system " + id);
        }
    }

    @Test
    void nonSchedulerTicksCannotPublishDisturbances() {
        for (long tick = 0; tick < 1_200; tick++) {
            if (tick % 10 != 0) assertFalse(SevereWeatherScheduler.disturbanceDue(tick, 1));
        }
    }
}
