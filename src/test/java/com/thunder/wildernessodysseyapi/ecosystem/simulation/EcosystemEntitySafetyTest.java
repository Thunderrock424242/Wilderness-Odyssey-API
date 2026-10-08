package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies every persistence and player-interaction veto independently. */
class EcosystemEntitySafetyTest {

    @Test
    void onlyUnprotectedWildlifeMayBeAbstracted() {
        assertTrue(EcosystemEntitySafety.mayAbstract(facts(-1)));
        for (int protectedFlag = 0; protectedFlag < 16; protectedFlag++) {
            assertFalse(EcosystemEntitySafety.mayAbstract(facts(protectedFlag)),
                    "protection flag " + protectedFlag + " must veto abstraction");
        }
    }

    @Test
    void recentCombatProtectsTheWholeTransitionCooldown() {
        assertTrue(EcosystemEntitySafety.recent(250, 100));
        assertTrue(EcosystemEntitySafety.recent(500, 100));
        assertFalse(EcosystemEntitySafety.recent(501, 100));
        assertFalse(EcosystemEntitySafety.recent(50, 0));
    }

    private static EcosystemEntitySafety.ProtectionFacts facts(int selected) {
        return new EcosystemEntitySafety.ProtectionFacts(
                selected == 0,
                selected == 1,
                selected == 2,
                selected == 3,
                selected == 4,
                selected == 5,
                selected == 6,
                selected == 7,
                selected == 8,
                selected == 9,
                selected == 10,
                selected == 11,
                selected == 12,
                selected == 13,
                selected == 14,
                selected == 15
        );
    }
}
