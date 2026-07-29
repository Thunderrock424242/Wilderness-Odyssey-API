package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterUnits;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Integration coverage for machine transactions and direct projection writes.
 *
 * <p>Tests inject an enabled compatibility supplier so an existing developer
 * config that intentionally disables machine compatibility does not weaken the
 * transaction proof.</p>
 */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterFluidCompatibilityGameTests {

    private WaterFluidCompatibilityGameTests() {
    }

    /** Verifies simulation is read-only and execution updates authority plus projection once. */
    @GameTest(template = "empty")
    public static void fluidHandlerSimulatesAndExecutesAtomically(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        WaterAccess access = WaterServices.access();
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);

        long blockUnits = WaterUnits.UNITS_PER_BLOCK;
        helper.assertTrue(
                access.addWater(level, position, blockUnits, false).transferredUnits() == blockUnits,
                "Could not prepare one authority-owned water block"
        );
        AuthorityWaterFluidHandler handler =
                new AuthorityWaterFluidHandler(level, position, access, () -> true);

        FluidStack simulatedDrain = handler.drain(
                WaterUnitConversions.MILLIBUCKETS_PER_BLOCK,
                IFluidHandler.FluidAction.SIMULATE
        );
        helper.assertTrue(
                simulatedDrain.getAmount() == WaterUnitConversions.MILLIBUCKETS_PER_BLOCK,
                "Simulated machine drain did not expose one full block"
        );
        helper.assertTrue(
                access.getWaterUnits(level, position) == blockUnits,
                "Simulated machine drain changed authority"
        );

        FluidStack executedDrain = handler.drain(
                WaterUnitConversions.MILLIBUCKETS_PER_BLOCK,
                IFluidHandler.FluidAction.EXECUTE
        );
        helper.assertTrue(
                executedDrain.getAmount() == WaterUnitConversions.MILLIBUCKETS_PER_BLOCK,
                "Executed machine drain did not return one full block"
        );
        helper.assertTrue(
                access.getWaterUnits(level, position) == 0L
                        && !level.getBlockState(position).is(
                        WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()),
                "Executed machine drain left canonical or projected water behind"
        );

        FluidStack vanillaWater = new FluidStack(
                Fluids.WATER,
                WaterUnitConversions.MILLIBUCKETS_PER_BLOCK
        );
        int simulatedFill = handler.fill(vanillaWater, IFluidHandler.FluidAction.SIMULATE);
        helper.assertTrue(
                simulatedFill == WaterUnitConversions.MILLIBUCKETS_PER_BLOCK
                        && access.getWaterUnits(level, position) == 0L,
                "Simulated tagged-water fill changed authority or reported the wrong amount"
        );

        int executedFill = handler.fill(vanillaWater, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(
                executedFill == WaterUnitConversions.MILLIBUCKETS_PER_BLOCK,
                "Executed tagged-water fill did not accept one full block"
        );
        helper.assertTrue(
                access.getWaterUnits(level, position) == blockUnits
                        && level.getBlockState(position).is(
                        WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()),
                "Executed tagged-water fill did not atomically restore authority and projection"
        );
        helper.succeed();
    }

    /** Verifies raw open-pipe-style block writes cannot leave authority or projection behind. */
    @GameTest(template = "empty")
    public static void directProjectionWritesReconcileAuthority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(3, 2, 3));
        WaterAccess access = WaterServices.access();
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);

        long blockUnits = WaterUnits.UNITS_PER_BLOCK;
        access.addWater(level, position, blockUnits, false);

        long residualUnits = blockUnits - 513L;
        helper.assertTrue(
                access.removeWater(level, position, 513L, false).transferredUnits() == 513L
                        && access.getWaterUnits(level, position) == residualUnits,
                "Canonical projection rounded hidden fixed-point water during a level change"
        );
        helper.assertTrue(
                access.addWater(level, position, 513L, false).transferredUnits() == 513L
                        && access.getWaterUnits(level, position) == blockUnits,
                "Could not restore the residual-unit projection test fixture"
        );

        WorldFluidMutationReconciler.MutationDecision noDelta =
                WorldFluidMutationReconciler.reconcile(
                        level,
                        position,
                        WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get().defaultBlockState(),
                        access,
                        () -> true
                );
        helper.assertTrue(
                noDelta == WorldFluidMutationReconciler.MutationDecision.CONTINUE,
                "A no-delta projection write should continue through the original method"
        );

        WorldFluidMutationReconciler.MutationDecision solidReplacement =
                WorldFluidMutationReconciler.reconcile(
                        level,
                        position,
                        Blocks.STONE.defaultBlockState(),
                        access,
                        () -> true
                );
        helper.assertTrue(
                solidReplacement == WorldFluidMutationReconciler.MutationDecision.CONTINUE
                        && access.getWaterUnits(level, position) == blockUnits,
                "Projection reconciliation swallowed a solid placement or pre-drained its water"
        );

        WorldFluidMutationReconciler.MutationDecision removal =
                WorldFluidMutationReconciler.reconcile(
                        level,
                        position,
                        Blocks.AIR.defaultBlockState(),
                        access,
                        () -> true
                );
        helper.assertTrue(
                removal == WorldFluidMutationReconciler.MutationDecision.COMMITTED,
                "Reconciler did not intercept a valid full-block extraction as committed"
        );
        helper.assertTrue(
                access.getWaterUnits(level, position) == 0L
                        && level.getBlockState(position).isAir(),
                "Intercepted projection extraction left phantom authority or projection"
        );

        var wildernessSource = WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()
                .defaultBlockState();
        WorldFluidMutationReconciler.MutationDecision placement =
                WorldFluidMutationReconciler.reconcile(
                        level,
                        position,
                        wildernessSource,
                        access,
                        () -> true
                );
        helper.assertTrue(
                placement == WorldFluidMutationReconciler.MutationDecision.COMMITTED,
                "Reconciler did not intercept a valid full-block placement as committed"
        );
        helper.assertTrue(
                access.getWaterUnits(level, position) == blockUnits
                        && level.getBlockState(position).is(
                        WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()),
                "Direct projection placement did not create matching authority"
        );
        helper.succeed();
    }
}
