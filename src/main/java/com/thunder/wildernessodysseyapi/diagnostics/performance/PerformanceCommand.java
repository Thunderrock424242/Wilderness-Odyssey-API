package com.thunder.wildernessodysseyapi.diagnostics.performance;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Operator-only diagnostics. Export paths are fixed; commands never schedule gameplay or alter saves. */
public final class PerformanceCommand {
    private PerformanceCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wo").then(Commands.literal("perf")
                .requires(source -> source.hasPermission(2))
                .executes(context -> show(context.getSource(), "status"))
                .then(Commands.literal("status").executes(context -> show(context.getSource(), "status")))
                .then(Commands.literal("save").executes(context -> show(context.getSource(), "save")))
                .then(Commands.literal("async").executes(context -> show(context.getSource(), "async")))
                .then(Commands.literal("worldgen").executes(context -> show(context.getSource(), "worldgen")))
                .then(Commands.literal("ownership").executes(context -> show(context.getSource(), "ownership")))
                .then(Commands.literal("record")
                        .then(Commands.literal("start").executes(context -> recording(context.getSource(), true)))
                        .then(Commands.literal("stop").executes(context -> recording(context.getSource(), false))))
                .then(Commands.literal("export").executes(context -> {
                    boolean accepted = PerformanceDiagnostics.export();
                    var status = PerformanceDiagnostics.writerStatus();
                    line(context.getSource(), accepted ? "Report queued for " + status.path()
                            : "Report was not queued: " + status.result());
                    return accepted ? 1 : 0;
                }))));
    }

    private static int recording(CommandSourceStack source, boolean enabled) {
        if (!PerformanceDiagnostics.setRecording(enabled)) {
            line(source, "Observation is OFF or no server session is active. Enable performance.coordinator.observation and restart.");
            return 0;
        }
        line(source, enabled ? "Bounded performance recording started. It exports when this server stops."
                : "Recording stopped. Existing samples remain available to /wo perf export; automatic export is now off.");
        return 1;
    }

    private static int show(CommandSourceStack source, String section) {
        PerformanceReport report = PerformanceDiagnostics.report();
        if (report == null) {
            line(source, "No performance session is available.");
            return 0;
        }
        line(source, "WO performance — Phase 0 observation | " + report.observation() + " | safe mode " + report.safeMode());
        switch (section) {
            case "status" -> {
                var ticks = report.performance();
                line(source, "MSPT EWMA " + millis(ticks.ewmaMillis()) + ", P50 " + millis(ticks.p50Millis())
                        + ", P95 " + millis(ticks.p95Millis()) + ", P99 " + millis(ticks.p99Millis())
                        + " (" + ticks.windowSamples() + " rolling ticks)");
                var jvm = ticks.jvm();
                line(source, "Heap used " + bytes(jvm.heapUsedBytes()) + ", peak " + bytes(ticks.heapPeakBytes())
                        + ", maximum " + bytes(jvm.heapMaximumBytes()) + "; processors " + jvm.processors());
                line(source, "Process CPU " + percent(jvm.processCpuLoad()) + "; GC collection-time pressure " + percent(jvm.gcTimeFraction()));
                line(source, "Existing Tick Engine optional budget: "
                        + millis(report.workQueues().getOrDefault("tickOptionalBudgetNanos", 0L) / 1_000_000.0));
                line(source, "Recording " + PerformanceDiagnostics.recording() + "; retained samples " + report.history().size());
                line(source, "Last report: " + PerformanceDiagnostics.writerStatus().result() + " " + PerformanceDiagnostics.writerStatus().path());
            }
            case "async" -> {
                report.workQueues().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                        .forEach(entry -> line(source, entry.getKey() + ": " + (entry.getValue() < 0 ? "unavailable" : entry.getValue())));
                line(source, "These are existing FIFO/shared and feature queues. Priority queue breakdown is unavailable.");
                line(source, "Data workers are included in shared CPU totals. Voice, structure-block and JDK HTTP lanes are not included.");
            }
            case "save" -> {
                line(source, "Dirty/hot/cold chunk backlog: " + report.saveBacklog() + ". Native persistence remains authoritative.");
                report.timings().phases().entrySet().stream().filter(entry -> entry.getKey().startsWith("native/")
                                || entry.getKey().startsWith("wo/") || entry.getKey().startsWith("shutdown/"))
                        .sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                            var phase = entry.getValue();
                            line(source, entry.getKey() + ": last " + millis(phase.lastNanos() / 1_000_000.0)
                                    + ", max " + millis(phase.maximumNanos() / 1_000_000.0)
                                    + ", calls " + phase.calls() + ", failures " + phase.failures());
                        });
                line(source, "Nested phases overlap. Missing phases may be unobserved or omitted by the retention bound: "
                        + report.timings().omittedPhaseMeasurements() + " measurements omitted.");
            }
            case "worldgen" -> {
                line(source, "Worldgen pressure/throughput: unavailable. Slow synchronous chunk blocking spans: "
                        + report.timings().slowChunkWaitSpans() + "; maximum " + millis(report.timings().maximumChunkWaitNanos() / 1_000_000.0));
                for (var wait : report.timings().waits()) line(source, wait.dimension() + " [" + wait.x() + ", " + wait.z()
                        + "] " + millis(wait.durationNanos() / 1_000_000.0) + " on " + wait.thread());
                line(source, "These are managedBlock/caller join spans, not whole getChunk durations. A request may appear on both threads.");
                line(source, "Spans do not identify a responsible mod or count generated chunks.");
            }
            case "ownership" -> {
                for (PerformanceArea area : PerformanceArea.values()) {
                    var owner = report.ownership().get(area);
                    String candidates = owner.candidates().stream().map(candidate -> candidate.modId() + " "
                            + candidate.version() + " (" + candidate.scope() + ")")
                            .collect(java.util.stream.Collectors.joining(", "));
                    line(source, area + ": " + (candidates.isEmpty() ? owner.baseline() : candidates)
                            + (owner.overlap() ? " — potential overlap; inspect configs" : ""));
                }
                line(source, "Presence is verified. Patch activity is unverified. WO takeover is disabled in every area.");
            }
            default -> { }
        }
        return 1;
    }

    private static void line(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static String millis(double value) { return String.format(Locale.ROOT, "%.2f ms", value); }
    private static String bytes(long value) { return value < 0 ? "unavailable" : String.format(Locale.ROOT, "%.1f MiB", value / 1048576.0); }
    private static String percent(double value) { return value < 0 ? "unavailable" : String.format(Locale.ROOT, "%.1f%%", value * 100); }
}
