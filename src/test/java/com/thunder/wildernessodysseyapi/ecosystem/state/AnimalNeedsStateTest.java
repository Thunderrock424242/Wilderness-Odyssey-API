package com.thunder.wildernessodysseyapi.ecosystem.state;

import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies compact persistence keeps major needs while dropping transient AI targets. */
class AnimalNeedsStateTest {

    @Test
    void serializationPersistsNeedsAndCooldownButNotWorldDecisions() {
        AnimalNeedsState original = new AnimalNeedsState();
        original.setNeeds(0.7, 0.8, 0.4, 0.3, 1.0);
        original.setNextHuntAllowedAt(12_345L);
        original.setWaterPosition(new BlockPos(1, 2, 3));
        original.setShelterPosition(new BlockPos(4, 5, 6));
        original.rememberThreat(new BlockPos(7, 8, 9), UUID.randomUUID(), 500L);
        original.begin(EcosystemBehaviorState.FLEEING, new BlockPos(10, 11, 12), 100L);
        original.markControllerInstalled();

        CompoundTag serialized = original.serializeNBT(null);
        AnimalNeedsState restored = new AnimalNeedsState();
        restored.deserializeNBT(null, serialized);

        assertEquals(0.7F, restored.thirst(), 1.0E-6F);
        assertEquals(0.8F, restored.hunger(), 1.0E-6F);
        assertEquals(0.4F, restored.rest(), 1.0E-6F);
        assertEquals(0.3F, restored.social(), 1.0E-6F);
        assertEquals(12_345L, restored.nextHuntAllowedAt());
        assertEquals(EcosystemBehaviorState.IDLE, restored.behavior());
        assertNull(restored.behaviorTarget());
        assertNull(restored.waterPosition());
        assertNull(restored.shelterPosition());
        assertNull(restored.threatPosition());
        assertFalse(restored.controllerInstalled());
        assertFalse(serialized.contains("threat"));
    }

    @Test
    void simulationOwnedNoAiMarkerSurvivesRestartAndRestoresOriginalValue() {
        AnimalNeedsState original = new AnimalNeedsState();
        original.suspendAiForSimulation(false);

        AnimalNeedsState restored = new AnimalNeedsState();
        restored.deserializeNBT(null, original.serializeNBT(null));

        assertTrue(restored.simulationAiSuspended());
        assertFalse(restored.resumeAiFromSimulation());
        assertFalse(restored.simulationAiSuspended());
    }
}
