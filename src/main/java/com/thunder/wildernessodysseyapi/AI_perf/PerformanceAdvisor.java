package com.thunder.wildernessodysseyapi.AI_perf;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        int dimensionCount = server.getAllLevels().size();

        int heaviestMobCluster = 0;
        int maxLoadedChunks = 0;
        int totalLoadedChunks = 0;

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
        subsystems.add(new PerformanceAdvisoryRequest.SubsystemLoad(
                "chunk-processing",
                "Chunk building or saving queues are backing up while players move.",
                "Offer batching or prioritization guidance for chunk I/O and lighting.",
                maxLoadedChunks,
                "Heaviest dimension chunk load: " + maxLoadedChunks + ", total loaded across server: " + totalLoadedChunks + "."
        ));
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
            case "chunk-processing" -> load.observedValue() >= 800
                    ? "Batch chunk saves in groups of 4-8 and deprioritize far-player chunks until the queue drains."
                    : "Chunk load is moderate; keep normal batch sizes.";
            case "world-ticking" -> load.observedValue() > DEFAULT_TICK_BUDGET_MS
                    ? "Apply short-term throttles (simulation distance -1, mob cap easing) until ticks return under budget."
                    : "Tick time within budget; no global throttle needed.";
            default -> "No recommendation available.";
        };
    }

    private static int estimateMobCluster(ServerLevel level) {
        return level.players().stream()
                .map(player -> level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0D)).size())
                .max(Comparator.naturalOrder())
                .orElse(0);
    }
}
