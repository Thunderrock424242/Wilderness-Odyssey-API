package com.thunder.wildernessodysseyapi.anomaly;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallStage;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the intended topology and native-versus-invasion rift rules. */
class AnomalyDimensionRulesTest {

    @Test
    void gatewaysLinkOverworldAndBeforeButNotArbitraryDimensions() {
        assertTrue(AnomalyDimensionRules.isGatewaySource(Level.OVERWORLD));
        assertTrue(AnomalyDimensionRules.isGatewaySource(TemporalRiftDimensions.THE_BEFORE_KEY));
        assertFalse(AnomalyDimensionRules.isGatewaySource(Level.NETHER));
        assertFalse(AnomalyDimensionRules.isGatewaySource(AnomalyDimensions.ANOMALY_DIMENSION_KEY));
    }

    @Test
    void anomalyKeepsRiftCreaturesWhileOtherDimensionsNeedActiveRiftfall() {
        assertTrue(AnomalyDimensionRules.permitsRiftPresence(
                AnomalyDimensions.ANOMALY_DIMENSION_KEY,
                RiftfallStage.CLEAR
        ));
        assertFalse(AnomalyDimensionRules.permitsRiftPresence(Level.OVERWORLD, RiftfallStage.CLEAR));
        assertTrue(AnomalyDimensionRules.permitsRiftPresence(Level.OVERWORLD, RiftfallStage.ACTIVE));
        assertTrue(AnomalyDimensionRules.permitsRiftPresence(Level.OVERWORLD, RiftfallStage.METEOR_SURGE));
        assertFalse(AnomalyDimensionRules.permitsRiftPresence(
                TemporalRiftDimensions.THE_BEFORE_KEY,
                RiftfallStage.CLEAR
        ));
    }
}
