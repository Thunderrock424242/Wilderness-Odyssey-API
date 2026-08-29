package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies safe knowledge inheritance for live configs created by older builds. */
class AIConfigLoaderTest {

    @Test
    void bundledConfigDefinesAllSixFirstClassSubsystemProfiles() throws Exception {
        try (InputStream stream = AIConfigLoaderTest.class.getClassLoader().getResourceAsStream("ai_config.yaml")) {
            assertNotNull(stream);
            AIConfig config = AIConfigLoader.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));

            assertEquals(
                    List.of("Aegis", "Eclipse", "Terra", "Helios", "Enforcer", "Requiem"),
                    config.getSubsystems().stream().map(AIConfig.Subsystem::getName).toList()
            );
            assertTrue(config.getSubsystems().get(1).getRole().startsWith("Rifts, anomalies"));
            assertEquals(2, config.getSubsystems().get(5).getBoundaries().size());
            assertEquals(true, config.getPlayerMemory().getEnabled());
            assertEquals(true, config.getPlayerMemory().getNaturalLearningEnabled());
            assertEquals(12, config.getPlayerMemory().getMaxMemoriesPerPlayer());
            assertEquals(true, config.getSettings().getOllamaAutostart());
            assertEquals(20, config.getSettings().getOllamaStartupTimeoutSeconds());
            assertEquals("", config.getSettings().getOllamaExecutable());
        }
    }

    @Test
    void olderConfigInheritsNewBundledKnowledgeWithoutReplacingPlayerContent() {
        AIConfig live = new AIConfig();
        live.getStory().add("Player-edited story");
        AIConfig bundled = new AIConfig();
        bundled.getStory().add("Bundled story");
        bundled.getAuthoritativeKnowledge().add("Aether is the central expedition intelligence.");
        bundled.getKnowledgeBoundaries().add("A biome tag is only a location.");
        bundled.getSubsystems().add(subsystem("Eclipse"));

        AIConfigLoader.applyBundledKnowledgeDefaults(live, bundled);

        assertEquals(List.of("Player-edited story"), live.getStory());
        assertEquals(List.of("Aether is the central expedition intelligence."), live.getAuthoritativeKnowledge());
        assertEquals(List.of("A biome tag is only a location."), live.getKnowledgeBoundaries());
        assertEquals("Eclipse", live.getSubsystems().get(0).getName());
    }

    @Test
    void explicitLiveKnowledgeIsNotOverwrittenByBundledDefaults() {
        AIConfig live = new AIConfig();
        live.getAuthoritativeKnowledge().add("Player-authored canon");
        live.getKnowledgeBoundaries().add("Player-authored boundary");
        live.getSubsystems().add(subsystem("Custom"));
        AIConfig bundled = new AIConfig();
        bundled.getAuthoritativeKnowledge().add("Bundled canon");
        bundled.getKnowledgeBoundaries().add("Bundled boundary");
        bundled.getSubsystems().add(subsystem("Eclipse"));

        AIConfigLoader.applyBundledKnowledgeDefaults(live, bundled);

        assertEquals(List.of("Player-authored canon"), live.getAuthoritativeKnowledge());
        assertEquals(List.of("Player-authored boundary"), live.getKnowledgeBoundaries());
        assertEquals("Custom", live.getSubsystems().get(0).getName());
    }

    private static AIConfig.Subsystem subsystem(String name) {
        AIConfig.Subsystem subsystem = new AIConfig.Subsystem();
        subsystem.setName(name);
        subsystem.setRole("test role");
        return subsystem;
    }
}
