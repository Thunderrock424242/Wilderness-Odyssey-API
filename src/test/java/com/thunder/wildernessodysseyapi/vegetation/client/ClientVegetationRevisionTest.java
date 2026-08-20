package com.thunder.wildernessodysseyapi.vegetation.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientVegetationRevisionTest {

    @Test
    void acceptsOnlyStrictlyNewerNonnegativeRevisions() {
        assertTrue(ClientVegetationClimateStore.isNewerRevision(null, 0L));
        assertTrue(ClientVegetationClimateStore.isNewerRevision(9L, 10L));
        assertFalse(ClientVegetationClimateStore.isNewerRevision(10L, 10L));
        assertFalse(ClientVegetationClimateStore.isNewerRevision(11L, 10L));
        assertFalse(ClientVegetationClimateStore.isNewerRevision(null, -1L));
    }
}
