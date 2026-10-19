package com.thunder.wildernessodysseyapi.opc.dataengine;

import com.thunder.wildernessodysseyapi.opc.diagnostics.PerformanceDiagnosticsConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Transformed server-thread proof of OPC optional admission without coupling Data Engine to native chunks. */
@GameTestHolder("wildernessodysseyapi_coordinator_tests")
@PrefixGameTestTemplate(false)
public final class OpcDataGameTests {
    private OpcDataGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void opcEntryDefersOptionalDataWithoutLosingCriticalCallbacks(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var engine = com.thunder.wildernessodysseyapi.opc.dataengine.DataEngine.get();
        helper.assertTrue(com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore.state().equals("DISABLED"),
                "The isolated fixture requires OPC controls disabled before installing its temporary session");
        helper.assertTrue(engine.isRunning() && engine.isEnabled(), "The fixture requires the default running Data Engine");
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "opc_contract_test");
        var critical = new java.util.concurrent.atomic.AtomicInteger();
        var optional = new java.util.concurrent.atomic.AtomicInteger();
        var mode = PerformanceDiagnosticsConfig.Mode.AUTO;
        com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore.start(server,
                new com.thunder.wildernessodysseyapi.opc.coordinator.CoordinatorConfig.Values(mode,
                        PerformanceDiagnosticsConfig.Mode.OFF, 2, .1, 100, 200, 600, 100, 4096), true);
        try {
            com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore.beginTick(server, server.getTickCount(), System.nanoTime());
            helper.assertTrue(engine.submit(com.thunder.wildernessodysseyapi.opc.dataengine.queue.QueuedUpdate.event(id, 1,
                    com.thunder.wildernessodysseyapi.opc.dataengine.queue.UpdatePriority.CRITICAL, server.getTickCount(), critical::incrementAndGet)), "Critical fixture must be accepted");
            helper.assertTrue(engine.submit(com.thunder.wildernessodysseyapi.opc.dataengine.queue.QueuedUpdate.event(id, 2,
                    com.thunder.wildernessodysseyapi.opc.dataengine.queue.UpdatePriority.HIGH, server.getTickCount(), optional::incrementAndGet)), "Optional fixture must be accepted");
            engine.tick(server, () -> true);
            helper.assertTrue(critical.get() == 1, "Critical callbacks must run while OPC ENTRY has zero optional allowance");
            helper.assertTrue(optional.get() == 0, "Optional callbacks must defer under OPC ENTRY");
        } finally {
            com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore.clear();
        }
        for (int pass = 0; pass < 20 && optional.get() == 0; pass++) engine.tick(server, () -> true);
        helper.assertTrue(optional.get() == 1, "Deferred callback must remain available after OPC is disabled");
        helper.succeed();
    }

}
