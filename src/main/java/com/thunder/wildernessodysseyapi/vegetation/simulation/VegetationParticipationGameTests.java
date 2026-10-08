package com.thunder.wildernessodysseyapi.vegetation.simulation;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.vegetation.api.PlantDisturbance;
import com.thunder.wildernessodysseyapi.vegetation.api.PlantDisturbanceType;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactivePlantDefinition;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactivePlantRegistry;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactivePlantTrait;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactiveVegetationServices;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.config.VegetationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/** Actual public mutation gates and loaded-world dimension participation. */
@GameTestHolder("wildernessodysseyapi_environment_tests")
@PrefixGameTestTemplate(false)
public final class VegetationParticipationGameTests {
    private VegetationParticipationGameTests() {
    }

    @GameTest(template = "empty")
    public static void masterToggleStopsPublicPlantUpdatesAndNewDamageRequests(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        var state = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        ReactivePlantRegistry.registerIfAbsent(Blocks.AZALEA_LEAVES, ReactivePlantDefinition.of(Set.of(ReactivePlantTrait.MOISTURE_REACTIVE),
                context -> context.state().setValue(BlockStateProperties.PERSISTENT, true)));
        level.setBlock(position, state, 2);
        level.getChunkAt(position).getData(ModAttachments.REACTIVE_VEGETATION)
                .applyClimate(VegetationClimateState.DEFAULT);
        boolean enabled = VegetationConfig.VEGETATION_UPDATES_ENABLED.get();
        try {
            VegetationConfig.VEGETATION_UPDATES_ENABLED.set(false);
            helper.assertTrue(!ReactiveVegetationServices.processRandomTick(level, position, state, 1L).registered(),
                    "Public random tick ignored the master toggle");
            helper.assertTrue(!ReactiveVegetationServices.processSelectedPlant(level, position, state,
                            VegetationClimateState.DEFAULT, 1L).registered(),
                    "Public selected-plant update ignored the master toggle");
            helper.assertTrue(!ReactiveVegetationServices.applyDisturbanceAt(level, position,
                            PlantDisturbanceType.WIND, 1.0, true).stateChanged(),
                    "Disabled vegetation accepted block damage");
            ReactiveVegetationServices.recordDisturbance(level, PlantDisturbance.lasting(
                    PlantDisturbanceType.WIND, position, 0, 1.0, level.getGameTime(), 100, true));
            VegetationConfig.VEGETATION_UPDATES_ENABLED.set(true);
            helper.assertTrue(!ReactiveVegetationServices.disturbanceAt(level, position).active(),
                    "A request published while disabled remained active after re-enabling");
            helper.assertTrue(level.getBlockState(position).equals(state), "Disabled update changed a plant");
            helper.succeed();
        } finally {
            VegetationConfig.VEGETATION_UPDATES_ENABLED.set(enabled);
        }
    }

}
