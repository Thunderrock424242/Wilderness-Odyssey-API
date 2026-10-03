package com.thunder.wildernessodysseyapi.quest.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoreQuestBridgeTest {
    @Test void legacyIdsMapWithoutChangingTheirPersistedIdentity() {
        assertEquals("wildernessodysseyapi:lore_001", LoreQuestBridge.map("lore_001").orElseThrow().toString());
        assertTrue(LoreQuestBridge.map("other:lore_001").isEmpty());
        assertTrue(LoreQuestBridge.map("../lore_001").isEmpty());
        assertTrue(LoreQuestBridge.map(null).isEmpty());
    }
    @Test void trustedProducerSchemasRejectUnregisteredAndOutOfRangeEvidence() {
        var id = net.minecraft.resources.ResourceLocation.parse("test:trusted_quest_event");
        QuestCustomEventRegistry.register(id, new QuestCustomEventRegistry.EventSchema(2));
        assertTrue(QuestCustomEventRegistry.valid(id, 2));
        assertFalse(QuestCustomEventRegistry.valid(id, 3));
        assertFalse(QuestCustomEventRegistry.valid(id, -1));
        assertFalse(QuestCustomEventRegistry.valid(net.minecraft.resources.ResourceLocation.parse("test:unregistered"), 1));
        assertThrows(IllegalArgumentException.class, () -> QuestCustomEventRegistry.register(id, new QuestCustomEventRegistry.EventSchema(3)));
    }
}
