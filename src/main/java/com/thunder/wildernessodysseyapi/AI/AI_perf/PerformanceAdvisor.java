package com.thunder.wildernessodysseyapi.AI.AI_perf;

import com.thunder.wildernessodysseyapi.chunk.ChunkStreamManager;
import com.thunder.wildernessodysseyapi.chunk.ChunkStreamStats;
import com.thunder.wildernessodysseyapi.chunk.ChunkStreamingConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

/**
 * Builds advisory prompts for performance-heavy systems so an AI helper can suggest
 * mitigations without ever executing game logic itself.
 */
public final class PerformanceAdvisor {
    /** Default server tick budget before we try to collect advice. */
    public static final long DEFAULT_TICK_BUDGET_MS = 45L;

    private PerformanceAdvisor() {
    }

    public static PerformanceAdvisoryRequest observe(MinecraftServer server, long worstTickMillis) {
        int playerCount = server.getPlayerCount();
        int dimensionCount = (int) StreamSupport.stream(server.getAllLevels().spliterator(), false).count();

        int heaviestMobCluster = 0;
        int maxLoadedChunks = 0;
        int totalLoadedChunks = 0;

        ChunkStreamingConfig.ChunkConfigValues chunkConfig = ChunkStreamingConfig.values();
        ChunkStreamStats chunkStats = ChunkStreamManager.snapshot();

        for (ServerLevel level : server.getAllLevels()) {
            heaviestMobCluster = Math.max(heaviestMobCluster, estimateMobCluster(level));
            int loadedChunks = level.getChunkSource().getLoadedChunksCount();
            maxLoadedChunks = Math.max(maxLoadedChunks, loadedChunks);
            totalLoadedChunks += loadedChunks;
        }

        List<PerformanceAdvisoryRequest.SubsystemLoad> subsystems = new ArrayList<>();
        subsystems.add(new PerformanceAdvisoryRequest.SubsystemLoad(
                "entity-pathfinding",
                "Entity pathfinding and goal selection may be colliding in crowded areas.",
                "Propose temporary throttling or waypointing to reduce per-tick AI cost.",
                heaviestMobCluster,
                heaviestMobCluster == 0
                        ? "No player-facing mob clusters detected during sampling."
                        : "Largest mob cluster near a player: " + heaviestMobCluster + " mobs within ~48 blocks."
        ));
        subsystems.add(new PerformanceAdvisoryRequest.SubsystemLoad(
                "entity-behavior",
                "Behavior trees for mobs are consuming too much time per tick.",
                "Suggest low-cost behavior fallbacks or reduced tick cadence for idle mobs.",
                heaviestMobCluster,
                heaviestMobCluster == 0
                        ? "Behavior load appears nominal near observed players."
                        : "Dense mob clusters increase goal-switching costs (peak: " + heaviestMobCluster + ")."
        ));
        subsystems.add(buildChunkStreamingLoad(chunkConfig, chunkStats, maxLoadedChunks, totalLoadedChunks));
        subsystems.add(new PerformanceAdvisoryRequest.SubsystemLoad(
                "world-ticking",
                "Overall world ticking exceeded the frame budget.",
                "Return guardrails that keep main-thread work under the tick budget.",
                worstTickMillis,
                "Worst tick observed: " + worstTickMillis + " ms (budget " + DEFAULT_TICK_BUDGET_MS + " ms)."
        ));

        return new PerformanceAdvisoryRequest(worstTickMillis, playerCount, dimensionCount, subsystems);
    }

    /**
     * Formats a compact prompt that can be handed to an AI helper. The AI is expected to
     * respond with suggestions only; all execution stays on the main thread.
     */
    public static String buildPrompt(PerformanceAdvisoryRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("Context: We observed a worst tick of ")
                .append(request.worstTickMillis())
                .append(" ms (target <= ")
                .append(DEFAULT_TICK_BUDGET_MS)
                .append(" ms). ");
        builder.append("Players online: ")
                .append(request.playerCount())
                .append(", dimensions loaded: ")
                .append(request.dimensionCount())
                .append(".\n");

        builder.append("Subsystems under load:\n");
        for (PerformanceAdvisoryRequest.SubsystemLoad load : request.subsystemLoads()) {
            builder.append("- ")
                    .append(load.id())
                    .append(": ")
                    .append(load.summary())
                    .append(" | Goal: ")
                    .append(load.mitigationGoal())
                    .append(" | Evidence: ")
                    .append(load.evidence())
                    .append('\n');
        }

        builder.append("Return concise JSON with mitigation ideas (throttling intervals, batching sizes, prioritization hints) that can be validated and applied on the main thread.");
        return builder.toString();
    }

    /**
     * Uses local heuristics (no external calls) to generate immediate, actionable advice.
     */
    public static String buildLocalAdvice(PerformanceAdvisoryRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("Performance advisory (local heuristics)\n");
        builder.append("- Worst tick: ")
                .append(request.worstTickMillis())
                .append(" ms; target <= ")
                .append(DEFAULT_TICK_BUDGET_MS)
                .append(" ms. Players: ")
                .append(request.playerCount())
                .append(", dimensions: ")
                .append(request.dimensionCount())
                .append('\n');

        for (PerformanceAdvisoryRequest.SubsystemLoad load : request.subsystemLoads()) {
            builder.append("  * ")
                    .append(load.id())
                    .append(" -> ")
                    .append(load.evidence())
                    .append('\n');
            builder.append("    Suggestion: ")
                    .append(suggestionFor(load))
                    .append('\n');
        }

        builder.append("These mitigations are advisory only and must be validated before applying on-thread.");
        return builder.toString();
    }

    public static String formatDurationNanos(long nanos) {
        return String.format("%.2f ms", nanos / (double) TimeUnit.MILLISECONDS.toNanos(1));
    }

    private static String suggestionFor(PerformanceAdvisoryRequest.SubsystemLoad load) {
        return switch (load.id()) {
            case "entity-pathfinding" -> load.observedValue() >= 48
                    ? "Throttle ambient mobs to every 2-3 ticks near dense clusters and cap concurrent path recalculations."
                    : "Keep standard cadence; cluster density looks manageable.";
            case "entity-behavior" -> load.observedValue() >= 48
                    ? "Prefer fallback/idle goals for non-aggressive mobs for the next few seconds to reduce goal churn."
                    : "No behavior adjustments required based on current samples.";
            case "chunk-processing" -> {
                ChunkStreamingConfig.ChunkConfigValues chunkConfig = ChunkStreamingConfig.values();
                ChunkTuningSuggestion tuning = recommendTuning(chunkConfig, saturation((int) load.observedValue(), 1000), ChunkStreamManager.snapshot());
                if (!chunkConfig.enabled()) {
                    yield "Chunk streaming is disabled; enable it or temporarily lower simulation distance to ease chunk churn.";
                }
                if (load.observedValue() >= 800) {
                    yield "I/O and caches are saturated: shrink simulation distance briefly, bump maxParallelIo/ioThreads a notch, and lower the writeFlushInterval (currently "
                            + chunkConfig.writeFlushIntervalTicks() + " ticks; try " + tuning.flushIntervalTicks() + ") until queues drain.";
                }
                if (load.observedValue() >= 500) {
                    yield "Queues are warming up: keep deltaChangeBudget high (e.g., " + tuning.deltaChangeBudget()
                            + ") and consider shorter flush intervals (" + tuning.flushIntervalTicks()
                            + ") or extra I/O threads (" + tuning.ioThreads() + ").";
                }
                yield "Chunk load is within configured headroom; keep hot/warm caches at "
                        + chunkConfig.hotCacheLimit() + "/" + chunkConfig.warmCacheLimit() + " and monitor pending saves.";
            }
            case "world-ticking" -> load.observedValue() > DEFAULT_TICK_BUDGET_MS
                    ? "Apply short-term throttles (simulation distance -1, mob cap easing) until ticks return under budget."
                    : "Tick time within budget; no global throttle needed.";
            default -> "No recommendation available.";
        };
    }

    private static PerformanceAdvisoryRequest.SubsystemLoad buildChunkStreamingLoad(
            ChunkStreamingConfig.ChunkConfigValues chunkConfig,
            ChunkStreamStats chunkStats,
            int maxLoadedChunks,
            int totalLoadedChunks) {
        boolean streamingEnabled = chunkConfig.enabled() && chunkStats.enabled();
        int ioCapacity = Math.max(1, chunkConfig.ioQueueSize() * Math.max(1, chunkConfig.maxParallelIo()));
        double hotSaturation = saturation(chunkStats.hotCached(), chunkConfig.hotCacheLimit());
        double warmSaturation = saturation(chunkStats.warmCached(), chunkConfig.warmCacheLimit());
        double trackedSaturation = saturation(chunkStats.trackedChunks(), chunkConfig.hotCacheLimit() + chunkConfig.warmCacheLimit());
        double ioSaturation = saturation(chunkStats.pendingSaves() + chunkStats.inFlightIo(), ioCapacity);
        double queueSaturation = saturation(chunkStats.ioQueueDepth(), ioCapacity);
        double pressure = Math.max(Math.max(hotSaturation, warmSaturation), Math.max(trackedSaturation, Math.max(ioSaturation, queueSaturation)));
        ChunkTuningSuggestion tuning = recommendTuning(chunkConfig, pressure, chunkStats);

        int observed = streamingEnabled
                ? (int) Math.round(pressure * 1000)
                : (int) Math.round(saturation(maxLoadedChunks, chunkConfig.hotCacheLimit() + chunkConfig.warmCacheLimit()) * 500);

        String evidence = streamingEnabled
                ? String.format(
                "Chunk streaming enabled. Hot cache %d/%d, warm cache %d/%d, tracked %d, queue %d/%d (pending %d, in-flight %d). Flush interval %d ticks, delta budget %d. Warm cache hit rate %.0f%%. Loaded chunks (vanilla): max %d, total %d.",
                chunkStats.hotCached(),
                chunkConfig.hotCacheLimit(),
                chunkStats.warmCached(),
                chunkConfig.warmCacheLimit(),
                chunkStats.trackedChunks(),
                chunkStats.ioQueueDepth(),
                ioCapacity,
                chunkStats.pendingSaves(),
                chunkStats.inFlightIo(),
                chunkConfig.writeFlushIntervalTicks(),
                chunkConfig.deltaChangeBudget(),
                chunkStats.warmCacheHitRate() * 100.0D,
                tuning.flushIntervalTicks(),
                tuning.maxParallelIo(),
                tuning.ioThreads(),
                tuning.deltaChangeBudget(),
                maxLoadedChunks,
                totalLoadedChunks)
                : String.format(
                "Chunk streaming disabled; relying on vanilla chunk I/O. Heaviest dimension load: %d, total loaded chunks: %d. Streaming config caches/queue: hot %d, warm %d, queue size %d.",
                maxLoadedChunks,
                totalLoadedChunks,
                chunkConfig.hotCacheLimit(),
                chunkConfig.warmCacheLimit(),
                chunkConfig.ioQueueSize());

        String summary = streamingEnabled
                ? "Chunk streaming queues are approaching configured cache or I/O limits."
                : "Chunk load is rising without chunk streaming enabled; vanilla I/O may stall.";
        String mitigationGoal = "Offer batching, prioritization, or config tweaks that honor the chunk streaming settings.";

        return new PerformanceAdvisoryRequest.SubsystemLoad(
                "chunk-processing",
                summary,
                mitigationGoal,
                observed,
                evidence
        );
    }

    private static double saturation(int used, int capacity) {
        if (capacity <= 0) {
            return 0.0D;
        }
        return Math.min(2.0D, used / (double) capacity);
    }

    private static ChunkTuningSuggestion recommendTuning(ChunkStreamingConfig.ChunkConfigValues config,
                                                         double pressure,
                                                         ChunkStreamStats stats) {
        int currentFlush = config.writeFlushIntervalTicks();
        int recommendedFlush = pressure >= 1.0D ? Math.max(5, currentFlush / 2) : currentFlush;
        if (pressure >= 0.8D && recommendedFlush == currentFlush) {
            recommendedFlush = Math.max(5, currentFlush - 5);
        }

        int recommendedParallelIo = config.maxParallelIo();
        if (pressure >= 0.9D) {
            recommendedParallelIo = Math.min(64, Math.max(1, config.maxParallelIo() + 1));
        }

        int recommendedIoThreads = config.ioThreads();
        if (pressure >= 0.9D && config.ioThreads() < 32) {
            recommendedIoThreads = Math.min(32, config.ioThreads() + 1);
        }

        int recommendedDeltaBudget = config.deltaChangeBudget();
        if (pressure >= 0.85D && stats.warmCacheHitRate() < 0.6D) {
            recommendedDeltaBudget = Math.max(config.deltaChangeBudget(), (int) (config.deltaChangeBudget() * 1.25));
        }

        return new ChunkTuningSuggestion(recommendedFlush, recommendedParallelIo, recommendedIoThreads, recommendedDeltaBudget);
    }

    private record ChunkTuningSuggestion(int flushIntervalTicks, int maxParallelIo, int ioThreads, int deltaChangeBudget) {
    }

    private static int estimateMobCluster(ServerLevel level) {
        return level.players().stream()
                .map(player -> level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0D)).size())
                .max(Comparator.naturalOrder())
                .orElse(0);
    }
}
