package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Verifies the optional Iris/Oculus material alias without requiring either mod. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExternalShaderWaterMaterialBridgeGameTests {

    private ExternalShaderWaterMaterialBridgeGameTests() {
    }

    /** Ensures unmapped custom states inherit vanilla water while explicit pack mappings win. */
    @GameTest(template = "empty")
    public static void aliasesOnlyUnmappedWildernessWaterStates(GameTestHelper helper) {
        Map<BlockState, Integer> stateIds = new HashMap<>();
        for (BlockState vanillaWater : Blocks.WATER.getStateDefinition().getPossibleStates()) {
            stateIds.put(vanillaWater, 32_000);
        }

        List<BlockState> wildernessStates = WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()
                .getStateDefinition().getPossibleStates();
        BlockState explicitlyMapped = wildernessStates.getFirst();
        stateIds.put(explicitlyMapped, 31_337);

        int aliases = ExternalShaderWaterMaterialBridge.aliasMissingWaterStates(stateIds);
        helper.assertTrue(aliases == wildernessStates.size() - 1,
                "Shader bridge did not alias every unmapped Wilderness water state");
        helper.assertTrue(stateIds.get(explicitlyMapped) == 31_337,
                "Shader bridge overwrote an explicit shader-pack material mapping");
        for (BlockState state : wildernessStates) {
            if (state != explicitlyMapped) {
                helper.assertTrue(stateIds.get(state) == 32_000,
                        "Wilderness water state did not inherit vanilla water material ID");
            }
        }
        helper.succeed();
    }
}
