package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies nearest-player multiplayer classification and coarse cell edges. */
class EcosystemZoneClassifierTest {

    private static final EcosystemSimulationSettings SETTINGS =
            new EcosystemSimulationSettings(true, 64, 96, 224, 512, 40, 16, 2);

    @Test
    void usesNearestPlayerWithoutDuplicatingTheCell() {
        List<EcosystemZoneClassifier.PlayerPoint> players = List.of(
                new EcosystemZoneClassifier.PlayerPoint(0.0, 0.0),
                new EcosystemZoneClassifier.PlayerPoint(480.0, 0.0)
        );

        assertEquals(WildlifeSimulationLod.ACTIVE,
                EcosystemZoneClassifier.classifyPosition(500.0, 0.0, players, SETTINGS));
        assertEquals(WildlifeSimulationLod.NEAR,
                EcosystemZoneClassifier.classifyPosition(300.0, 0.0, players, SETTINGS));
        assertEquals(WildlifeSimulationLod.DISTANT,
                EcosystemZoneClassifier.classifyPosition(240.0, 400.0, players, SETTINGS));
    }

    @Test
    void emptyPlayerSetIsDormantAndTouchingCellPromotesConservatively() {
        assertEquals(WildlifeSimulationLod.DORMANT,
                EcosystemZoneClassifier.classifyPosition(0.0, 0.0, List.of(), SETTINGS));
        assertEquals(WildlifeSimulationLod.ACTIVE,
                EcosystemZoneClassifier.classifyCell(
                        new EcosystemCellKey(1, 0),
                        List.of(new EcosystemZoneClassifier.PlayerPoint(0.0, 0.0)),
                        SETTINGS
                ));
    }

    @Test
    void negativeCoordinatesUseStableCellIdentity() {
        assertEquals(new EcosystemCellKey(-1, -2),
                EcosystemCellKey.fromPacked(new EcosystemCellKey(-1, -2).packed()));
    }
}
