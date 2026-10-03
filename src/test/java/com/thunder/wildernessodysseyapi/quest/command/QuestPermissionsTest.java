package com.thunder.wildernessodysseyapi.quest.command;

import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestPermissionsTest {
    @Test void ordinaryPlayersCanOnlyActOnTheirOwnPlayerOperations() {
        var settings = new QuestConfig.Settings(true, 2, 3, 10, 20, 8);
        assertTrue(QuestPermissions.allowed(0, QuestOperation.CLAIM, settings));
        assertFalse(QuestPermissions.allowed(1, QuestOperation.VALIDATE, settings));
        assertTrue(QuestPermissions.allowed(2, QuestOperation.VALIDATE, settings));
        assertFalse(QuestPermissions.allowed(2, QuestOperation.PUBLISH, settings));
        assertTrue(QuestPermissions.allowed(3, QuestOperation.PUBLISH, settings));
        assertFalse(QuestPermissions.allowed(3, QuestOperation.RECEIPTS, settings));
        assertTrue(QuestPermissions.allowed(4, QuestOperation.RECEIPTS, settings));
    }
    @Test void raisingEditPermissionAlsoRaisesPublishAndDisabledGameplayCannotMutate() {
        var settings = new QuestConfig.Settings(true, 4, 3, 10, 20, 8);
        assertFalse(QuestPermissions.allowed(3, QuestOperation.PUBLISH, settings));
        assertTrue(QuestPermissions.allowed(4, QuestOperation.PUBLISH, settings));
        var disabled = new QuestConfig.Settings(false, 2, 3, 10, 20, 8);
        assertFalse(QuestPermissions.allowed(0, QuestOperation.TRACK, disabled));
        assertTrue(QuestPermissions.allowed(0, QuestOperation.VIEW, disabled));
    }
}
