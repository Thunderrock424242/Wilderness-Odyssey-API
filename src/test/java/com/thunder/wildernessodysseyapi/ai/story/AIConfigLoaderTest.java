package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies safe knowledge inheritance for live configs created by older builds. */
class AIConfigLoaderTest {

    @Test
    void olderConfigInheritsNewBundledKnowledgeWithoutReplacingPlayerContent() {
        AIConfig live = new AIConfig();
        live.getStory().add("Player-edited story");
        AIConfig bundled = new AIConfig();
        bundled.getStory().add("Bundled story");
        bundled.getAuthoritativeKnowledge().add("Aether is the central expedition intelligence.");
        bundled.getKnowledgeBoundaries().add("A biome tag is only a location.");

        AIConfigLoader.applyBundledKnowledgeDefaults(live, bundled);

        assertEquals(List.of("Player-edited story"), live.getStory());
        assertEquals(List.of("Aether is the central expedition intelligence."), live.getAuthoritativeKnowledge());
        assertEquals(List.of("A biome tag is only a location."), live.getKnowledgeBoundaries());
    }

    @Test
    void explicitLiveKnowledgeIsNotOverwrittenByBundledDefaults() {
        AIConfig live = new AIConfig();
        live.getAuthoritativeKnowledge().add("Player-authored canon");
        live.getKnowledgeBoundaries().add("Player-authored boundary");
        AIConfig bundled = new AIConfig();
        bundled.getAuthoritativeKnowledge().add("Bundled canon");
        bundled.getKnowledgeBoundaries().add("Bundled boundary");

        AIConfigLoader.applyBundledKnowledgeDefaults(live, bundled);

        assertEquals(List.of("Player-authored canon"), live.getAuthoritativeKnowledge());
        assertEquals(List.of("Player-authored boundary"), live.getKnowledgeBoundaries());
    }
}
