package com.thunder.wildernessodysseyapi.weather.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies stable cell mapping across negative block and packed coordinates. */
class AtmosphereCellKeyTest {

    @Test
    void negativeBlockCoordinatesUseFloorDivision() {
        assertEquals(new AtmosphereCellKey(-1, -1), AtmosphereCellKey.fromBlock(-1, -1, 256));
        assertEquals(new AtmosphereCellKey(-1, -2), AtmosphereCellKey.fromBlock(-256, -257, 256));
        assertEquals(new AtmosphereCellKey(0, 0), AtmosphereCellKey.fromBlock(0, 255, 256));
    }

    @Test
    void packedCoordinatesRoundTripAcrossTheSignedIntegerRange() {
        AtmosphereCellKey[] keys = {
                new AtmosphereCellKey(0, 0),
                new AtmosphereCellKey(-1, 1),
                new AtmosphereCellKey(1, -1),
                new AtmosphereCellKey(Integer.MIN_VALUE, Integer.MAX_VALUE),
                new AtmosphereCellKey(Integer.MAX_VALUE, Integer.MIN_VALUE)
        };

        for (AtmosphereCellKey key : keys) {
            assertEquals(key, AtmosphereCellKey.fromPacked(key.packed()));
        }
    }
}
