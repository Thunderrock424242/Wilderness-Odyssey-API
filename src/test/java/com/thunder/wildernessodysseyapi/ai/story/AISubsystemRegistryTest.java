package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that only configured Aether personalities can be selected. */
class AISubsystemRegistryTest {

    @Test
    void explicitSpecialistAndAliasTakePriorityOverCentralMention() {
        AISubsystemRegistry registry = registry();

        assertEquals("Eclipse", registry.findExplicitSpeaker("Aether, ask Eclipse about this rift").orElseThrow());
        assertEquals("Aegis", registry.findExplicitSpeaker("health ai, can you help?").orElseThrow());
        assertEquals("Aether", registry.findExplicitSpeaker("hello Aether").orElseThrow());
        assertTrue(registry.findExplicitSpeaker("hello there").isEmpty());
    }

    @Test
    void canonicalizesConfiguredNamesAndRejectsInventedSpeakers() {
        AISubsystemRegistry registry = registry();

        assertEquals("Eclipse", registry.canonicalSpeaker("eCLipse").orElseThrow());
        assertEquals("Aether", registry.canonicalOrCentral("unregistered overseer"));
        assertEquals(List.of("Aether", "Aegis", "Eclipse"), registry.allowedSpeakers());
    }

    private static AISubsystemRegistry registry() {
        AIConfig.Subsystem aegis = subsystem("Aegis", "health ai", "protection and survival safety");
        AIConfig.Subsystem eclipse = subsystem("Eclipse", "rift ai", "rifts and anomalies");
        return new AISubsystemRegistry("Aether", List.of(aegis, eclipse));
    }

    private static AIConfig.Subsystem subsystem(String name, String alias, String role) {
        AIConfig.Subsystem subsystem = new AIConfig.Subsystem();
        subsystem.setName(name);
        subsystem.setRole(role);
        subsystem.setPersonality("measured");
        subsystem.getAliases().add(alias);
        return subsystem;
    }
}
