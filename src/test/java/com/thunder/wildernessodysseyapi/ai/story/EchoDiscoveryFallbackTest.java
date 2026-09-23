package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.temporalrift.echo.EchoDiscoveryStage;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EchoDiscoveryFallbackTest {
    @Test
    void dimensionAloneDoesNotRevealSynchronizationOrExperimentHistory() {
        var responder = new AIFallbackResponder();
        responder.configure(null, "Aether", "aether");
        var reply = responder.buildReply("Eclipse, what is Echo Earth?",
                new AIFallbackResponder.ResponseContext(Set.of("dimension:the_echo"))).orElseThrow();
        assertTrue(reply.text().contains("no recorded field observations"));
    }

    @Test
    void materialDiscoveryDoesNotInventMissingStructuralHistory() {
        var tags = EchoDiscoveryStage.contextTags(EchoDiscoveryStage.MATERIAL_SYNCHRONIZATION.evidenceBit());
        assertFalse(tags.contains(EchoDiscoveryStage.INDEPENDENT_HISTORY.contextTag()));
        assertEquals(EchoDiscoveryStage.MATERIAL_SYNCHRONIZATION, EchoDiscoveryStage.fromContext(tags));
    }

    @Test
    void alignmentRequiresActualTransferAndFractureEvidence() {
        assertEquals(EchoDiscoveryStage.UNEXPLORED, EchoDiscoveryStage.fromEvidence(
                EchoDiscoveryStage.withAlignmentHypothesis(0, true)));
        int material = EchoDiscoveryStage.MATERIAL_SYNCHRONIZATION.evidenceBit();
        assertEquals(EchoDiscoveryStage.MATERIAL_SYNCHRONIZATION, EchoDiscoveryStage.fromEvidence(
                EchoDiscoveryStage.withAlignmentHypothesis(material, false)));
        assertEquals(EchoDiscoveryStage.ALIGNMENT_HYPOTHESIS, EchoDiscoveryStage.fromEvidence(
                EchoDiscoveryStage.withAlignmentHypothesis(material, true)));
    }
}
