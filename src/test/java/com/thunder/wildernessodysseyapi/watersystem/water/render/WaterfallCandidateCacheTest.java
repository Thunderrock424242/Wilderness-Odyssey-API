package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterfallCandidateCacheTest {
    @Test
    void rediscoveryKeepsPublishedGeometryAndImpactCadenceUntilRefresh() {
        var candidate = new WaterfallSurfaceDetails.Candidate(4, 5, 60, 80);
        candidate.faces.add(new WaterfallSurfaceDetails.Face(1, 62, .5f, 123));
        candidate.lastImpact = 50;
        var renewed = WaterfallSurfaceDetails.renewCandidate(candidate, 4, 5, 60, 130);
        assertEquals(1, renewed.faces.size(), "Rediscovery discarded published sheet geometry");
        assertEquals(50, renewed.lastImpact, "Rediscovery reset the impact cooldown");
        assertEquals(130, renewed.expires);
        assertTrue(WaterfallSurfaceDetails.renewCandidate(renewed, 4, 5, 58, 140).faces.isEmpty(),
                "A changed receiving level retained stale geometry");
    }
}
