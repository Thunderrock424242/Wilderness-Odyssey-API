package com.thunder.aether.server.model;

import com.thunder.aether.server.api.GenerateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EchoPromptBoundaryTest {
    @Test
    void dimensionAloneDoesNotGrantFieldDiscoveriesInEitherPrompt() throws Exception {
        for (String prompt : prompts(List.of("dimension:the_echo"))) {
            assertTrue(prompt.contains("Its history and cause remain unknown"));
            assertFalse(prompt.contains("The player has observed familiar-looking geology"));
            assertFalse(prompt.contains("Observed divergent infrastructure is evidence"));
            assertFalse(prompt.contains("The player witnessed a distorted trace"));
            assertFalse(prompt.contains("Eclipse may hypothesize that the fracture"));
        }
    }

    @Test
    void materialEvidenceDoesNotInventEarlierHistoryOrAlignment() throws Exception {
        for (String prompt : prompts(List.of("echo:discovery:material_synchronization"))) {
            assertTrue(prompt.contains("The player witnessed a distorted trace"));
            assertFalse(prompt.contains("Observed divergent infrastructure is evidence"));
            assertFalse(prompt.contains("Eclipse may hypothesize that the fracture"));
        }
    }

    @Test
    void alignmentStaysAHypothesisWithoutRecoveringExperimentRecords() throws Exception {
        for (String prompt : prompts(List.of("echo:discovery:alignment_hypothesis"))) {
            assertTrue(prompt.contains("Eclipse may hypothesize that the fracture"));
            assertTrue(prompt.contains("this is not a proven cause"));
            assertTrue(prompt.contains("No original experiment records are recovered"));
            assertFalse(prompt.contains("Observed divergent infrastructure is evidence"));
        }
    }

    private static List<String> prompts(List<String> tags) throws Exception {
        PromptCatalog catalog = new PromptCatalog(null);
        String dimension = "wildernessodysseyapi:the_echo";
        var context = new GenerateRequest.Context(dimension, "minecraft:plains", true,
                "helmet", tags, "");
        var request = new GenerateRequest("request", "server", dimension, "player", "Explorer",
                "Eclipse", "What is Echo Earth?", context, List.of());
        return List.of(catalog.generation(request), catalog.verification(request, "{}"));
    }
}
