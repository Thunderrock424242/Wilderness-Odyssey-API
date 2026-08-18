package com.thunder.wildernessodysseyapi.vegetation.api;

import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactivePlantRegistryTest {

    @Test
    void compatibilityApiRequiresOnlyBlockAndDefinition() throws ReflectiveOperationException {
        Method register = ReactivePlantRegistry.class.getMethod(
                "register",
                Block.class,
                ReactivePlantDefinition.class
        );
        ReactivePlantDefinition definition = ReactivePlantDefinition.observe(Set.of(
                ReactivePlantTrait.MOISTURE_REACTIVE,
                ReactivePlantTrait.SEASON_REACTIVE
        ));

        assertEquals(void.class, register.getReturnType());
        assertEquals(List.of(Block.class, ReactivePlantDefinition.class), List.of(register.getParameterTypes()));
        assertTrue(definition.traits().contains(ReactivePlantTrait.MOISTURE_REACTIVE));
    }

    @Test
    void nullPlantOwnerIsRejectedBeforeRegistryMutation() {
        ReactivePlantDefinition definition = ReactivePlantDefinition.observe(
                Set.of(ReactivePlantTrait.FLOWER)
        );

        assertThrows(
                NullPointerException.class,
                () -> ReactivePlantRegistry.register(null, definition)
        );
    }

    @Test
    void flowerPolicyClosesAtNightAndInSevereStorms() {
        VegetationClimateState storm = new VegetationClimateState(
                0.7,
                0.6,
                0.0,
                0.9,
                VegetationSeasonState.GROWING,
                0L,
                0L,
                0,
                0.0
        );

        assertTrue(ReactivePlantPolicy.flowerShouldOpen(storm, true, true, false));
        assertTrue(!ReactivePlantPolicy.flowerShouldOpen(storm, false, true, false));
        assertTrue(!ReactivePlantPolicy.flowerShouldOpen(storm, true, true, true));
    }
}
