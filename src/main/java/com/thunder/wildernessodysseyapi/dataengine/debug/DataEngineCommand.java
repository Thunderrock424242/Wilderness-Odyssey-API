package com.thunder.wildernessodysseyapi.dataengine.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncTaskStats;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.metrics.DataEngineMetricsSnapshot;
import com.thunder.wildernessodysseyapi.dataengine.metrics.DataSystemMetricsSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Permission-gated server diagnostics for measured Data Engine state. */
public final class DataEngineCommand {
    private DataEngineCommand() {
    }

    /** Adds {@code /wo dataengine stats|resetstats|benchmark}. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wo")
                .then(Commands.literal("dataengine")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("stats").executes(context -> showStats(context.getSource())))
                        .then(Commands.literal("resetstats").executes(context -> resetStats(context.getSource())))
                        .then(Commands.literal("benchmark").executes(context -> benchmark(context.getSource())))));
    }

    private static int showStats(CommandSourceStack source) {
        DataEngine engine = DataEngine.get();
        if (!engine.isRunning()) {
            source.sendFailure(Component.literal("Data Engine is not attached to a running server."));
            return 0;
        }
        DataEngineMetricsSnapshot metrics = engine.metricsSnapshot();
        AsyncTaskStats workers = AsyncTaskManager.snapshot();
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Data Engine: %s | main %.3f/%.3f ms | dirty %d | queued %d | backpressure %s",
                engine.isEnabled() ? "enabled" : "disabled",
                metrics.lastMainThreadProcessingNanos() / 1_000_000.0D,
                engine.config().tickBudgetNanos() / 1_000_000.0D,
                metrics.dirtyEntries(),
                metrics.queuedWork(),
                metrics.backpressureActive() ? "ACTIVE" : "inactive"
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Updates: submitted %d | processed %d (%d/s) | coalesced %d (%d/s) | failures %d",
                metrics.updatesSubmitted(),
                metrics.updatesProcessed(),
                metrics.processedPerSecond(),
                metrics.updatesCoalesced(),
                metrics.coalescedPerSecond(),
                metrics.updateFailures()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Network: %d batches | %d entries | %.1f KiB estimated | %d interest-filtered",
                metrics.networkBatches(),
                metrics.networkEntries(),
                metrics.estimatedBytesSent() / 1024.0D,
                metrics.interestFilteredUpdates()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Cache: %d entries | %d hits / %d misses (%.1f%% hit) | Workers: %d configured, %d queued",
                metrics.cacheEntries(),
                metrics.cacheHits(),
                metrics.cacheMisses(),
                metrics.cacheHitRate() * 100.0D,
                workers.configuredThreads(),
                workers.queuedWorkerTasks()
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Async: %d submitted | %d completed | %d rejected | %d waiting to apply | %.3f ms worker total",
                metrics.asyncTasksSubmitted(),
                metrics.asyncTasksCompleted(),
                metrics.asyncTasksRejected(),
                metrics.asyncQueueLength(),
                metrics.totalWorkerProcessingNanos() / 1_000_000.0D
        )), false);
        for (DataSystemMetricsSnapshot system : metrics.systems().values()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    Locale.ROOT,
                    "  %s: %d processed | %.3f ms total | %d failures",
                    system.systemId(),
                    system.updatesProcessed(),
                    system.processingNanos() / 1_000_000.0D,
                    system.updateFailures()
            )), false);
        }
        return 1;
    }

    private static int resetStats(CommandSourceStack source) {
        DataEngine engine = DataEngine.get();
        if (!engine.isRunning()) {
            source.sendFailure(Component.literal("Data Engine is not attached to a running server."));
            return 0;
        }
        engine.resetMetrics();
        source.sendSuccess(() -> Component.literal("Data Engine metric totals reset; pending work was preserved."), true);
        return 1;
    }

    private static int benchmark(CommandSourceStack source) {
        DataEngineBenchmark.Result result = DataEngineBenchmark.run();
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Bounded Data Engine benchmark: %d submitted, %d coalesced, %d processed in %.3f ms; cache %.1f%% hit (%d/%d); %d synthetic batches.",
                result.submitted(),
                result.coalesced(),
                result.processed(),
                result.elapsedNanos() / 1_000_000.0D,
                result.cacheHitRate() * 100.0D,
                result.cacheHits(),
                result.cacheHits() + result.cacheMisses(),
                result.batchCount()
        )), false);
        return 1;
    }
}
