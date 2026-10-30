package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Reuses the exact finite dam ledger while requiring opt-in physical-column flow. */
@GameTestHolder("wildernessodysseyapi_reactive_water_tests")
@PrefixGameTestTemplate(false)
public final class ReactiveDamGameTests {

    private ReactiveDamGameTests() { }

    @GameTest(template = "water_flow", batch = "reactive_reservoir", timeoutTicks = 600)
    public static void physicalColumnsFillBreachAndDrainTheFiniteDam(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        CanonicalDamGameTests.runPhysicalFixture(helper);
    }
}
