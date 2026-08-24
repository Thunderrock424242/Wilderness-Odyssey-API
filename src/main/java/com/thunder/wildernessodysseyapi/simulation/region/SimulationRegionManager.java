package com.thunder.wildernessodysseyapi.simulation.region;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded, transient request deduplicator for regional orchestration work.
 *
 * <p>The manager stores only explicitly requested or player-occupied cells. It
 * never enumerates the world, discovers unloaded chunks, or owns subsystem
 * simulation state.</p>
 */
public final class SimulationRegionManager {
    public static final int DEFAULT_MAXIMUM_PENDING = 2_048;
    public static final int DEFAULT_MAXIMUM_STATES = 4_096;

    private final int maximumPending;
    private final int maximumStates;
    private final Deque<RegionKey> queue = new ArrayDeque<>();
    private final Map<RegionKey, PendingRegion> pending = new LinkedHashMap<>();
    private final LinkedHashMap<RegionKey, RegionalSimulationState> states =
            new LinkedHashMap<>(64, 0.75F, true);

    private long acceptedRequests;
    private long coalescedRequests;
    private long rejectedRequests;

    /** Creates the production-bounded region manager. */
    public SimulationRegionManager() {
        this(DEFAULT_MAXIMUM_PENDING, DEFAULT_MAXIMUM_STATES);
    }

    /** Creates explicit bounds for focused tests and specialized hosts. */
    public SimulationRegionManager(int maximumPending, int maximumStates) {
        if (maximumPending < 1 || maximumStates < 1) {
            throw new IllegalArgumentException("Simulation region bounds must be positive");
        }
        this.maximumPending = maximumPending;
        this.maximumStates = maximumStates;
    }

    /** Adds or coalesces one optional regional request. */
    public RequestResult request(SimulationRegion region, SimulationTrigger trigger, long requestedTick) {
        Objects.requireNonNull(region, "Simulation region is required");
        Objects.requireNonNull(trigger, "Simulation trigger is required");
        RegionKey key = RegionKey.from(region);
        PendingRegion existing = pending.get(key);
        if (existing != null) {
            pending.put(key, new PendingRegion(
                    region,
                    SimulationTrigger.moreUrgent(existing.trigger(), trigger),
                    Math.max(existing.requestedTick(), Math.max(0L, requestedTick))
            ));
            coalescedRequests++;
            return RequestResult.COALESCED;
        }
        if (pending.size() >= maximumPending) {
            rejectedRequests++;
            return RequestResult.REJECTED_AT_CAPACITY;
        }
        pending.put(key, new PendingRegion(region, trigger, Math.max(0L, requestedTick)));
        queue.addLast(key);
        acceptedRequests++;
        return RequestResult.ACCEPTED;
    }

    /** Removes the next request without performing any world lookup. */
    public Optional<PendingRegion> poll() {
        while (!queue.isEmpty()) {
            RegionKey key = queue.removeFirst();
            PendingRegion request = pending.remove(key);
            if (request != null) {
                return Optional.of(request);
            }
        }
        return Optional.empty();
    }

    /** Records the latest classification and elapsed-time anchor after processing succeeds. */
    public void complete(PendingRegion request, ActivityLevel activity, long processedTick) {
        Objects.requireNonNull(request, "Pending region is required");
        Objects.requireNonNull(activity, "Activity level is required");
        RegionKey key = RegionKey.from(request.region());
        RegionalSimulationState previous = states.get(key);
        states.put(key, new RegionalSimulationState(
                request.region(),
                activity,
                request.trigger(),
                request.requestedTick(),
                Math.max(0L, processedTick),
                previous == null ? 1L : previous.updateCount() + 1L
        ));
        trimStates();
    }

    /** Returns recent transient state for elapsed-time calculation and diagnostics. */
    public Optional<RegionalSimulationState> state(SimulationRegion region) {
        return Optional.ofNullable(states.get(RegionKey.from(region)));
    }

    /** Releases pending and recent state for one unloading dimension. */
    public void clearDimension(ResourceLocation dimension) {
        Objects.requireNonNull(dimension, "Dimension is required");
        pending.keySet().removeIf(key -> key.dimension().equals(dimension));
        states.keySet().removeIf(key -> key.dimension().equals(dimension));
        queue.removeIf(key -> key.dimension().equals(dimension));
    }

    /** Releases every server-lifecycle-derived request and state entry. */
    public void clearAll() {
        queue.clear();
        pending.clear();
        states.clear();
        acceptedRequests = 0L;
        coalescedRequests = 0L;
        rejectedRequests = 0L;
    }

    /** Builds a cheap immutable diagnostic summary outside the hot path. */
    public Diagnostics diagnostics() {
        int active = 0;
        int nearby = 0;
        int background = 0;
        int dormant = 0;
        for (RegionalSimulationState state : states.values()) {
            switch (state.activity()) {
                case ACTIVE -> active++;
                case NEARBY -> nearby++;
                case BACKGROUND -> background++;
                case DORMANT -> dormant++;
            }
        }
        return new Diagnostics(
                pending.size(),
                states.size(),
                active,
                nearby,
                background,
                dormant,
                acceptedRequests,
                coalescedRequests,
                rejectedRequests
        );
    }

    private void trimStates() {
        Iterator<RegionKey> iterator = states.keySet().iterator();
        while (states.size() > maximumStates && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /** Outcome allowing callers to account for coalescing and backpressure. */
    public enum RequestResult {
        ACCEPTED,
        COALESCED,
        REJECTED_AT_CAPACITY;

        public boolean accepted() {
            return this != REJECTED_AT_CAPACITY;
        }
    }

    /** One request removed from the bounded queue. */
    public record PendingRegion(
            SimulationRegion region,
            SimulationTrigger trigger,
            long requestedTick
    ) {
    }

    /** Bounded regional queue/state counters for commands and the paged F3 UI. */
    public record Diagnostics(
            int pendingRegions,
            int trackedRegions,
            int activeRegions,
            int nearbyRegions,
            int backgroundRegions,
            int dormantRegions,
            long acceptedRequests,
            long coalescedRequests,
            long rejectedRequests
    ) {
    }

    private record RegionKey(
            ResourceLocation dimension,
            int cellX,
            int cellZ,
            int cellSize
    ) {
        private static RegionKey from(SimulationRegion region) {
            return new RegionKey(region.dimension(), region.cellX(), region.cellZ(), region.cellSize());
        }
    }
}
