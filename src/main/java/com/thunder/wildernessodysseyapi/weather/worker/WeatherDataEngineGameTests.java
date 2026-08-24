package com.thunder.wildernessodysseyapi.weather.worker;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.metrics.DataEngineMetricsSnapshot;
import com.thunder.wildernessodysseyapi.dataengine.metrics.DataSystemMetricsSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.integration.WaterPerformanceIntegration;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-server proof for production Data Engine scheduling and worker boundaries. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WeatherDataEngineGameTests {

    private WeatherDataEngineGameTests() {
    }

    /**
     * Creates real level-owned cells and waits for the normal scheduler,
     * shared worker calculation, stale check, and server-thread apply path.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void immutableWeatherBatchCompletesThroughSharedWorker(GameTestHelper helper) {
        DataEngine dataEngine = DataEngine.get();
        helper.assertTrue(dataEngine.isRunning(), "Data Engine was not running in the GameTest server lifecycle");
        helper.assertTrue(dataEngine.isEnabled(), "Data Engine must be enabled for the worker integration proof");

        ServerLevel level = helper.getLevel();
        WeatherAuthority authority = WeatherAuthority.get();
        BlockPos position = helper.absolutePos(new BlockPos(8, 2, 8));
        DataEngineMetricsSnapshot baseline = dataEngine.metricsSnapshot();
        DataSystemMetricsSnapshot waterBaseline = baseline.systems().get(WaterPerformanceIntegration.SYSTEM_ID);
        helper.assertTrue(waterBaseline != null, "Water Phase 3 was not registered with Data Engine");
        int changed = authority.clearLocalWeather(level, position);
        helper.assertTrue(changed == 9, "Expected nine initialized atmosphere cells but changed " + changed);

        AtmosphereView captured = authority.cellAt(level, position);
        helper.assertTrue(captured != null, "Weather initialization did not create its authoritative center cell");
        long capturedRevision = captured.revision();
        long capturedSimulationTick = captured.lastSimulatedTick();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    AtmosphereView applied = authority.cellAt(level, position);
                    helper.assertTrue(applied != null, "The authoritative center cell disappeared before apply");
                    helper.assertTrue(
                            applied.revision() > capturedRevision,
                            "The worker result has not advanced the authoritative cell revision"
                    );
                    helper.assertTrue(
                            applied.lastSimulatedTick() > capturedSimulationTick,
                            "The worker result has not advanced the authoritative simulation tick"
                    );

                    DataEngineMetricsSnapshot current = dataEngine.metricsSnapshot();
                    helper.assertTrue(
                            current.asyncTasksSubmitted() > baseline.asyncTasksSubmitted(),
                            "No immutable weather calculation reached the shared worker pool"
                    );
                    helper.assertTrue(
                            current.asyncTasksCompleted() > baseline.asyncTasksCompleted(),
                            "No immutable weather calculation completed through the shared worker pool"
                    );
                    helper.assertTrue(
                            current.totalWorkerProcessingNanos() > baseline.totalWorkerProcessingNanos(),
                            "The completed weather calculation recorded no worker processing time"
                    );
                    helper.assertTrue(
                            current.asyncTasksRejected() == baseline.asyncTasksRejected(),
                            "The Phase 2 weather calculation was unexpectedly rejected"
                    );
                    helper.assertTrue(
                            current.updateFailures() == baseline.updateFailures(),
                            "The Data Engine recorded a failure while applying Phase 2 weather"
                    );
                    DataSystemMetricsSnapshot currentWater = current.systems().get(
                            WaterPerformanceIntegration.SYSTEM_ID
                    );
                    helper.assertTrue(currentWater != null, "Water Phase 3 metrics disappeared during the test");
                    helper.assertTrue(
                            currentWater.updatesProcessed() > waterBaseline.updatesProcessed(),
                            "No Water Phase 3 maintenance passed through the central scheduler"
                    );
                    helper.assertTrue(
                            currentWater.updateFailures() == waterBaseline.updateFailures(),
                            "The Water Phase 3 scheduler recorded a maintenance failure"
                    );
                })
                .thenExecute(() -> {
                    AtmosphereView applied = authority.cellAt(level, position);
                    DataEngineMetricsSnapshot current = dataEngine.metricsSnapshot();
                    DataSystemMetricsSnapshot currentWater = current.systems().get(
                            WaterPerformanceIntegration.SYSTEM_ID
                    );
                    ModConstants.LOGGER.info(
                            "[Weather Phase 2 GameTest] shared worker path passed: revision {} -> {}, "
                                    + "async submitted +{}, completed +{}, rejected +{}, worker {} us, "
                                    + "Water Phase 3 updates +{}",
                            capturedRevision,
                            applied == null ? -1L : applied.revision(),
                            current.asyncTasksSubmitted() - baseline.asyncTasksSubmitted(),
                            current.asyncTasksCompleted() - baseline.asyncTasksCompleted(),
                            current.asyncTasksRejected() - baseline.asyncTasksRejected(),
                            (current.totalWorkerProcessingNanos() - baseline.totalWorkerProcessingNanos()) / 1_000L,
                            currentWater == null
                                    ? -1L
                                    : currentWater.updatesProcessed() - waterBaseline.updatesProcessed()
                    );
                    authority.clearLocalWeather(level, position);
                })
                .thenSucceed();
    }
}
