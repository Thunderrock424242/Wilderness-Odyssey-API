package com.thunder.wildernessodysseyapi.opc.core;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.opc.tickengine.TickPressure;
import com.thunder.wildernessodysseyapi.opc.tickengine.TickPriority;
import com.thunder.wildernessodysseyapi.opc.tickengine.TickTask;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * One bounded, cooperative queue implementation for OPC's deferred APIs. Compatibility lanes
 * retain independent bounds/enablement and historical critical-priority semantics. Submission
 * and lifecycle mutations hold only a short lane lock; callbacks run outside it on the server.
 * No executor, polling loop, world reference or per-empty-tick queue allocation is introduced.
 */
public final class DeferredWorkScheduler {
    private static final TickPriority[] PRIORITIES = TickPriority.values();
    private final LongSupplier clock;

    public DeferredWorkScheduler() { this(System::nanoTime); }
    public DeferredWorkScheduler(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    public Lane lane(Listener listener, Gate gate, boolean gameplayBypassesBudget) {
        return new Lane(listener, gate, gameplayBypassesBudget);
    }

    public interface Listener {
        void executed(String subsystem, long nanos, long tick);
        void deferred(String subsystem);
        void throttled(String subsystem);
        void failed(String subsystem);
        void queued(int count);
    }

    @FunctionalInterface
    public interface Gate { boolean permitted(TickPriority priority, TickPressure pressure, long tick); }

    public record Limits(boolean enabled, int maximumPerPass, int maximumQueued, int maximumPerSubsystem) {
        public Limits {
            maximumPerPass = Math.max(1, maximumPerPass);
            maximumQueued = Math.max(1, maximumQueued);
            maximumPerSubsystem = Math.max(1, Math.min(maximumQueued, maximumPerSubsystem));
        }
    }

    public record Report(int processed, int deferred, int stale, int remaining, long elapsedNanos) { }

    /** A compatibility admission lane; empty lanes do not allocate priority deques. */
    public final class Lane {
        private final Listener listener;
        private final Gate gate;
        private final boolean gameplayBypassesBudget;
        private final EnumMap<TickPriority, ArrayDeque<TickTask>> queues = new EnumMap<>(TickPriority.class);
        private final Map<String, Integer> subsystemCounts = new HashMap<>();
        private final Set<String> coalescingKeys = new HashSet<>();
        private final Map<String, Long> lastFailureLogs = new HashMap<>();
        private Limits limits = new Limits(false, 1, 1, 1);
        private int size;
        private long generation;
        private long selections;
        private int fairnessPriority = PRIORITIES.length - 1;

        private Lane(Listener listener, Gate gate, boolean gameplayBypassesBudget) {
            this.listener = Objects.requireNonNull(listener);
            this.gate = Objects.requireNonNull(gate);
            this.gameplayBypassesBudget = gameplayBypassesBudget;
        }

        public synchronized void configure(Limits limits) { this.limits = Objects.requireNonNull(limits); }

        public synchronized boolean submit(TickTask task) {
            Objects.requireNonNull(task);
            if (!limits.enabled()) return false;
            String key = identity(task);
            if (!key.isEmpty() && coalescingKeys.contains(key)) return true;
            int subsystemSize = subsystemCounts.getOrDefault(task.subsystem(), 0);
            if (size >= limits.maximumQueued() || subsystemSize >= limits.maximumPerSubsystem()) return false;
            if (!key.isEmpty()) coalescingKeys.add(key);
            subsystemCounts.put(task.subsystem(), subsystemSize + 1);
            queues.computeIfAbsent(task.priority(), ignored -> new ArrayDeque<>()).addLast(task);
            listener.queued(++size);
            return true;
        }

        public Report process(long tick, long deadline, TickPressure pressure, BooleanSupplier hasTime, boolean report) {
            Objects.requireNonNull(hasTime);
            int passLimit;
            long owner;
            synchronized (this) {
                passLimit = limits.enabled() ? Math.min(size, limits.maximumPerPass()) : 0;
                owner = generation;
            }
            if (passLimit == 0) return report ? new Report(0, 0, 0, size(), 0) : null;
            long start = clock.getAsLong();
            int processed = 0;
            int deferred = 0;
            int stale = 0;
            for (int attempted = 0; attempted < passLimit && hasTime.getAsBoolean(); attempted++) {
                TickTask task;
                synchronized (this) {
                    if (owner != generation) break;
                    task = poll(pressure, tick);
                }
                if (task == null) break;
                if (task.isStale(tick)) {
                    release(task, owner);
                    stale++;
                    continue;
                }
                boolean bypass = task.priority() == TickPriority.CRITICAL
                        || gameplayBypassesBudget && task.priority() == TickPriority.GAMEPLAY;
                if (!hasTime.getAsBoolean() || !bypass && clock.getAsLong() >= deadline) {
                    requeue(task, owner, true);
                    listener.deferred(task.subsystem());
                    deferred++;
                    break;
                }
                long before = clock.getAsLong();
                TickTask.Result result = TickTask.Result.COMPLETE;
                try { result = Objects.requireNonNullElse(task.work().run(), TickTask.Result.COMPLETE); }
                catch (Exception failure) {
                    listener.failed(task.subsystem());
                    log(task, failure);
                }
                listener.executed(task.subsystem(), Math.max(0, clock.getAsLong() - before), tick);
                processed++;
                if (result == TickTask.Result.DEFER) {
                    requeue(task, owner, false);
                    listener.deferred(task.subsystem());
                    deferred++;
                } else release(task, owner);
            }
            return report ? new Report(processed, deferred, stale, size(), Math.max(0, clock.getAsLong() - start)) : null;
        }

        public synchronized int size() { return size; }
        public synchronized double pressure() { return Math.min(1, size / (double) limits.maximumQueued()); }

        public synchronized void clear() {
            generation++;
            queues.clear();
            subsystemCounts.clear();
            coalescingKeys.clear();
            lastFailureLogs.clear();
            selections = 0;
            fairnessPriority = PRIORITIES.length - 1;
            size = 0;
            listener.queued(0);
        }

        private TickTask poll(TickPressure pressure, long tick) {
            // The cursor spans passes so maximumPerPass=1 cannot starve lower priorities.
            if (++selections % 8 == 0) {
                for (int i = 0; i < PRIORITIES.length; i++) {
                    int index = fairnessPriority;
                    fairnessPriority = Math.floorMod(fairnessPriority - 1, PRIORITIES.length);
                    TickTask task = pollPriority(PRIORITIES[index], pressure, tick);
                    if (task != null) return task;
                }
            }
            for (TickPriority priority : PRIORITIES) {
                TickTask task = pollPriority(priority, pressure, tick);
                if (task != null) return task;
            }
            return null;
        }

        private TickTask pollPriority(TickPriority priority, TickPressure pressure, long tick) {
            ArrayDeque<TickTask> queue = queues.get(priority);
            if (queue == null || queue.isEmpty()) return null;
            if (!gate.permitted(priority, pressure, tick)) {
                listener.throttled(queue.peekFirst().subsystem());
                return null;
            }
            return queue.pollFirst();
        }

        private synchronized void requeue(TickTask task, long owner, boolean first) {
            if (owner != generation) return;
            ArrayDeque<TickTask> queue = queues.computeIfAbsent(task.priority(), ignored -> new ArrayDeque<>());
            if (first) queue.addFirst(task); else queue.addLast(task);
        }

        private synchronized void release(TickTask task, long owner) {
            if (owner != generation) return;
            size--;
            subsystemCounts.computeIfPresent(task.subsystem(), (ignored, count) -> count <= 1 ? null : count - 1);
            coalescingKeys.remove(identity(task));
            listener.queued(size);
        }

        private synchronized void log(TickTask task, Exception failure) {
            String signature = task.subsystem() + ':' + failure.getClass().getName();
            long now = clock.getAsLong();
            Long prior = lastFailureLogs.get(signature);
            if (prior == null || now - prior >= TimeUnit.SECONDS.toNanos(10)) {
                if (lastFailureLogs.size() >= 128) lastFailureLogs.clear();
                lastFailureLogs.put(signature, now);
                ModConstants.LOGGER.error("[OPC] Deferred task failed (subsystem {}, priority {}, context '{}')",
                        task.subsystem(), task.priority(), task.context(), failure);
            }
        }
    }

    private static String identity(TickTask task) { return task.coalescingKey().isEmpty() ? "" : task.subsystem() + ':' + task.coalescingKey(); }
}
