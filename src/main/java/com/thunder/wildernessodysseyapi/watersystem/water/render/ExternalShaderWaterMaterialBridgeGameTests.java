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
        helper.assertTrue(ExternalShaderWaterMaterialBridge.aliasMissingWaterStates(stateIds) == 0,
                "Shader material alias must be idempotent across pack reload callbacks");
        helper.succeed();
    }

    /** Ensures an unusual pack without a vanilla-water ID is never assigned a guessed material. */
    @GameTest(template = "empty")
    public static void skipsAliasWhenShaderPackDoesNotMapVanillaWater(GameTestHelper helper) {
        Map<BlockState, Integer> stateIds = new HashMap<>();
        stateIds.put(Blocks.STONE.defaultBlockState(), 7);

        int aliases = ExternalShaderWaterMaterialBridge.aliasMissingWaterStates(stateIds);
        helper.assertTrue(aliases == 0,
                "Shader bridge invented a material ID without a vanilla-water mapping");
        for (BlockState state : WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()
                .getStateDefinition().getPossibleStates()) {
            helper.assertTrue(!stateIds.containsKey(state),
                    "Shader bridge mutated Wilderness water without a canonical pack mapping");
        }
        helper.succeed();
    }

    /** Ensures the supported public Iris accessor is resolved and applied. */
    @GameTest(template = "empty")
    public static void appliesAliasThroughPublicSettingsAccessor(GameTestHelper helper) {
        PublicGetterSettings settings = new PublicGetterSettings(vanillaWaterMap());

        int aliases = ExternalShaderWaterMaterialBridge.applyToSettings(settings);
        helper.assertTrue(aliases > 0, "Public shader settings accessor was not applied");
        helper.assertTrue(ExternalShaderWaterMaterialBridge.status()
                        == ExternalShaderWaterMaterialBridge.MaterialBridgeStatus.MAPPED,
                "Public shader settings accessor did not report mapped status");
        helper.succeed();
    }

    /** Ensures forked settings can inherit the narrow private-field fallback. */
    @GameTest(template = "empty")
    public static void appliesAliasThroughInheritedPrivateField(GameTestHelper helper) {
        InheritedPrivateFieldSettings settings =
                new InheritedPrivateFieldSettings(vanillaWaterMap());

        int aliases = ExternalShaderWaterMaterialBridge.applyToSettings(settings);
        helper.assertTrue(aliases > 0, "Inherited shader settings field was not applied");
        helper.assertTrue(ExternalShaderWaterMaterialBridge.status()
                        == ExternalShaderWaterMaterialBridge.MaterialBridgeStatus.MAPPED,
                "Inherited shader settings field did not report mapped status");
        helper.succeed();
    }

    private static Map<BlockState, Integer> vanillaWaterMap() {
        Map<BlockState, Integer> stateIds = new HashMap<>();
        for (BlockState state : Blocks.WATER.getStateDefinition().getPossibleStates()) {
            stateIds.put(state, 32_000);
        }
        return stateIds;
    }

    /** Minimal stand-in for the current Iris public API. */
    public static final class PublicGetterSettings {
        private final Map<BlockState, Integer> stateIds;

        private PublicGetterSettings(Map<BlockState, Integer> stateIds) {
            this.stateIds = stateIds;
        }

        public Map<BlockState, Integer> getBlockStateIds() {
            return stateIds;
        }
    }

    private static class PrivateFieldSettingsBase {
        @SuppressWarnings("unused")
        private final Map<BlockState, Integer> blockStateIds;

        private PrivateFieldSettingsBase(Map<BlockState, Integer> blockStateIds) {
            this.blockStateIds = blockStateIds;
        }
    }

    private static final class InheritedPrivateFieldSettings extends PrivateFieldSettingsBase {
        private InheritedPrivateFieldSettings(Map<BlockState, Integer> blockStateIds) {
            super(blockStateIds);
        }
    }
}
