package com.thunder.wildernessodysseyapi.weather.simulation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherAuthorityAsyncValidationTest {

    @Test
    void exactCapturedRevisionsRemainCurrent() {
        Map<Long, Long> revisions = Map.of(10L, 2L, 20L, 7L);

        assertTrue(WeatherAuthority.revisionsMatch(
                revisions,
                key -> revisions.get(key)
        ));
    }

    @Test
    void changedOrRemovedCellsRejectTheWholeBatch() {
        Map<Long, Long> revisions = Map.of(10L, 2L, 20L, 7L);

        assertFalse(WeatherAuthority.revisionsMatch(
                revisions,
                key -> key == 20L ? 8L : revisions.get(key)
        ));
        assertFalse(WeatherAuthority.revisionsMatch(
                revisions,
                key -> key == 20L ? null : revisions.get(key)
        ));
    }
}
