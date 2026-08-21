package com.thunder.wildernessodysseyapi.worldgen.secretvillage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers deterministic, world-specific Secret Order village rarity decisions. */
class SecretOrderVillagePlacerTest {

    @Test
    void honorsClosedChanceBounds() {
        assertFalse(SecretOrderVillagePlacer.passesRarityRoll(42L, 128L, 0.0D));
        assertTrue(SecretOrderVillagePlacer.passesRarityRoll(42L, 128L, 1.0D));
    }

    @Test
    void rollIsDeterministicAndIncludesWorldSeed() {
        boolean first = SecretOrderVillagePlacer.passesRarityRoll(42L, 128L, 0.5D);
        boolean repeated = SecretOrderVillagePlacer.passesRarityRoll(42L, 128L, 0.5D);

        assertTrue(first == repeated);
        assertNotEquals(SecretOrderVillagePlacer.raritySeed(42L, 128L),
                SecretOrderVillagePlacer.raritySeed(43L, 128L));
    }
}
