package com.thunder.wildernessodysseyapi.AI_perf;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies approved performance mitigations on the main thread and rolls them back
 * when they expire.
 */
public final class PerformanceMitigationController {
    private static final int DEFAULT_DURATION_SECONDS = 30;
    private static final int MIN_SIM_DISTANCE = 2;
    private static final int MIN_SEVERITY_FOR_ACTION = 2;
    private static final int PENDING_TTL_SECONDS = 120;

    private static final PerformanceActionQueue ACTION_QUEUE = new PerformanceActionQueue();

    private static volatile int pathfindingThrottleInterval = 1;
    private static volatile long pathfindingThrottleUntil = 0L;

    private static volatile int entityTickInterval = 1;
    private static volatile long entityTickUntil = 0L;

    private static volatile int chunkDistanceDrop = 0;
    private static volatile long chunkDistanceUntil = 0L;
    private static volatile Integer originalSimulationDistance = null;

    private static final ConcurrentHashMap<UUID, Boolean> frozenEntities = new ConcurrentHashMap<>();

    private PerformanceMitigationController() {
    }

    public static PerformanceActionQueue getActionQueue() {
        return ACTION_QUEUE;
    }

    public static List<PerformanceAction> buildActionsFromRequest(PerformanceAdvisoryRequest request) {
        List<PerformanceAction> proposals = new ArrayList<>();
        List<PerformanceAction> rollbackProposals = new ArrayList<>();
        List<PerformanceAdvisoryRequest.SubsystemLoad> loads = request.subsystemLoads();
        for (PerformanceAdvisoryRequest.SubsystemLoad load : loads) {
            if (load.observedValue() < MIN_SEVERITY_FOR_ACTION) {
                continue;
            }
            String subsystem = normalizeSubsystemId(load.id());
            if (subsystem == null) {
                continue;
            }
            if (ACTION_QUEUE.hasPendingOrActiveMitigation(subsystem)) {
                continue;
            }
            switch (subsystem) {
                case "entity-pathfinding" -> proposals.add(new PerformanceAction(
                        ID_GENERATOR.nextBaseId(),
                        subsystem,
                        "Throttle pathfinding for dense clusters (every 3 ticks).",
                        load.evidence(),
                        DEFAULT_DURATION_SECONDS,
                        (int) load.observedValue()));
                case "entity-ticking" -> proposals.add(new PerformanceAction(
                        ID_GENERATOR.nextBaseId(),
                        subsystem,
                        "Lower idle mob tick rate to ease behavior costs.",
                        load.evidence(),
                        DEFAULT_DURATION_SECONDS,
                        (int) load.observedValue()));
                case "chunk-processing" -> proposals.add(new PerformanceAction(
                        ID_GENERATOR.nextBaseId(),
                        subsystem,
                        "Temporarily shrink simulation distance to ease chunk queues.",
                        load.evidence(),
                        DEFAULT_DURATION_SECONDS,
                        (int) load.observedValue()));
                default -> {
                }
            }
        }
        for (PerformanceAction active : ACTION_QUEUE.getActive()) {
            boolean subsystemStillHot = loads.stream()
                    .map(load -> normalizeSubsystemId(load.id()))
                    .anyMatch(normalized -> normalized != null && normalized.equals(active.getSubsystem())
                            && load.observedValue() >= MIN_SEVERITY_FOR_ACTION);
            if (!subsystemStillHot && !ACTION_QUEUE.hasPendingRollback(active.getSubsystem()) && !active.isRollback()) {
                String rollbackId = ID_GENERATOR.nextRollbackId(active.getId());
                rollbackProposals.add(new PerformanceAction(
                        rollbackId,
                        active.getSubsystem(),
                        "Undo mitigation for " + active.getSubsystem(),
                        "Subsystem load normalized; revert action " + active.getId(),
                        DEFAULT_DURATION_SECONDS,
                        active.getSeverity(),
                        PerformanceActionStatus.PENDING,
                        Instant.now(),
                        active.getId()));
            }
        }
        ACTION_QUEUE.replacePendingMitigations(proposals);
        ACTION_QUEUE.enqueueRollbacks(rollbackProposals);
        return proposals;
    }

    public static void approveAndApply(MinecraftServer server, PerformanceAction action) {
        action.markApproved();
        if (action.isRollback()) {
            applyRollback(server, action);
            action.markApplied();
            return;
        }
        switch (action.getSubsystem()) {
            case "entity-pathfinding" -> applyPathfindingThrottle(server, 3, action.getDurationSeconds());
            case "entity-ticking" -> applyEntityTickThrottle(server, 3, action.getDurationSeconds());
            case "chunk-processing" -> applyChunkMitigation(server, 1, action.getDurationSeconds());
            default -> {
                ModConstants.LOGGER.warn("Unknown subsystem {} in performance action {}", action.getSubsystem(), action.getId());
                return;
            }
        }
        action.markApplied();
    }

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        List<String> expired = new ArrayList<>();

        if (pathfindingThrottleInterval > 1 && gameTime > pathfindingThrottleUntil) {
            pathfindingThrottleInterval = 1;
            expired.addAll(expireMatching("entity-pathfinding"));
        }
        if (entityTickInterval > 1 && gameTime > entityTickUntil) {
            entityTickInterval = 1;
            expired.addAll(expireMatching("entity-ticking"));
        }
        if (chunkDistanceDrop > 0 && gameTime > chunkDistanceUntil) {
            rollbackChunkMitigation(server);
            expired.addAll(expireMatching("chunk-processing"));
        }

        List<PerformanceAction> stalePending = ACTION_QUEUE
                .expirePendingOlderThan(Instant.now().minusSeconds(PENDING_TTL_SECONDS));
        if (!stalePending.isEmpty()) {
            expired.addAll(stalePending.stream().map(PerformanceAction::getId).toList());
        }
        ACTION_QUEUE.markExpired(expired);
        ACTION_QUEUE.cleanupExpired();
    }

    private static void applyRollback(MinecraftServer server, PerformanceAction action) {
        switch (action.getSubsystem()) {
            case "entity-pathfinding" -> {
                pathfindingThrottleInterval = 1;
                pathfindingThrottleUntil = 0L;
            }
            case "entity-ticking" -> {
                entityTickInterval = 1;
                entityTickUntil = 0L;
            }
            case "chunk-processing" -> {
                rollbackChunkMitigation(server);
                chunkDistanceUntil = 0L;
            }
            default -> ModConstants.LOGGER.warn("Unknown rollback subsystem {} for action {}", action.getSubsystem(), action.getId());
        }
        action.getRollbackOfId()
                .flatMap(ACTION_QUEUE::findActive)
                .ifPresent(PerformanceAction::markExpired);
        action.markExpired();
        ACTION_QUEUE.cleanupExpired();
    }

    public static boolean isPathfindingThrottled(ServerLevel level) {
        return pathfindingThrottleInterval > 1 && level.getGameTime() <= pathfindingThrottleUntil;
    }

    public static int getPathfindingThrottleInterval() {
        return pathfindingThrottleInterval;
    }

    public static boolean isEntityTickThrottled(ServerLevel level) {
        return entityTickInterval > 1 && level.getGameTime() <= entityTickUntil;
    }

    public static int getEntityTickInterval() {
        return entityTickInterval;
    }

    public static void maybeFreezeEntity(Mob mob) {
        if (!mob.isNoAi()) {
            mob.setNoAi(true);
            frozenEntities.put(mob.getUUID(), Boolean.TRUE);
        }
    }

    public static void thawEntity(Mob mob) {
        if (frozenEntities.remove(mob.getUUID()) != null) {
            mob.setNoAi(false);
        }
    }

    private static void applyPathfindingThrottle(MinecraftServer server, int intervalTicks, int durationSeconds) {
        pathfindingThrottleInterval = Math.max(2, intervalTicks);
        pathfindingThrottleUntil = server.overworld().getGameTime() + durationSeconds * 20L;
    }

    private static void applyEntityTickThrottle(MinecraftServer server, int intervalTicks, int durationSeconds) {
        entityTickInterval = Math.max(2, intervalTicks);
        entityTickUntil = server.overworld().getGameTime() + durationSeconds * 20L;
    }

    private static void applyChunkMitigation(MinecraftServer server, int dropBy, int durationSeconds) {
        int desiredDrop = Math.max(0, dropBy);
        if (desiredDrop == 0) return;

        if (originalSimulationDistance == null) {
            originalSimulationDistance = server.getPlayerList().getSimulationDistance();
        }
        int newDistance = Math.max(MIN_SIM_DISTANCE, originalSimulationDistance - desiredDrop);
        if (newDistance < server.getPlayerList().getSimulationDistance()) {
            server.getPlayerList().setSimulationDistance(newDistance);
        }
        chunkDistanceDrop = desiredDrop;
        chunkDistanceUntil = server.overworld().getGameTime() + durationSeconds * 20L;
    }

    private static void rollbackChunkMitigation(MinecraftServer server) {
        if (originalSimulationDistance != null) {
            server.getPlayerList().setSimulationDistance(originalSimulationDistance);
        }
        originalSimulationDistance = null;
        chunkDistanceDrop = 0;
    }

    private static List<String> expireMatching(String subsystem) {
        return ACTION_QUEUE.getActive().stream()
                .filter(action -> action.getSubsystem().equals(subsystem))
                .peek(PerformanceAction::markExpired)
                .map(PerformanceAction::getId)
                .toList();
    }

    public static void thawNearbyPlayers(ServerLevel level) {
        for (ServerPlayer ignored : level.players()) {
            // Players visiting the level thaw nearby mobs by ticking them again
        }
    }

    private static String normalizeSubsystemId(String rawId) {
        return switch (rawId) {
            case "entity-pathfinding" -> "entity-pathfinding";
            case "entity-behavior" -> "entity-ticking";
            case "chunk-processing" -> "chunk-processing";
            default -> null;
        };
    }
}
