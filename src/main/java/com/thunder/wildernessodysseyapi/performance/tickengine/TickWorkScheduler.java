package com.thunder.wildernessodysseyapi.performance.tickengine;

import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Bounded deferred queue for tick-aware Wilderness Odyssey work.
 *
 * <p>The queue is additive and opt-in. It never intercepts vanilla or another
 * mod's entities, block entities, events, random ticks, or scheduled ticks.</p>
 */
public final class TickWorkScheduler {
    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;
    private static final int FAIRNESS_INTERVAL = 8;
    private static final long ERROR_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10L);
    private static final TickPriority[] PRIORITIES = TickPriority.values();

    private final EnumMap<TickPriority, ConcurrentLinkedDeque<TickTask>> queues =
            new EnumMap<>(TickPriority.class);
    private final ConcurrentHashMap<String, AtomicInteger> subsystemQueueSizes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> coalescingKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastErrorLogs = new ConcurrentHashMap<>();
    private final AtomicInteger queuedTasks = new AtomicInteger();
    private final TickEngineMetrics metrics;
    private volatile Settings settings = Settings.defaults();

    public TickWorkScheduler(TickEngineMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        for (TickPriority priority : TickPriority.values()) {
            queues.put(priority, new ConcurrentLinkedDeque<>());
        }
    }

    /** Replaces queue bounds while retaining already accepted tasks. */
    public void configure(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings").normalized();
    }

    /** Submits work or coalesces a duplicate key without growing the queue. */
    public boolean submit(TickTask task) {
        Objects.requireNonNull(task, "task");
        Settings current = settings;
        if (!current.enabled()) {
            return false;
        }
        String coalescingIdentity = task.coalescingIdentity();
        if (!coalescingIdentity.isEmpty() && coalescingKeys.putIfAbsent(coalescingIdentity, Boolean.TRUE) != null) {
            return true;
        }
        if (!reserveCapacity(task.subsystem(), current)) {
            if (!coalescingIdentity.isEmpty()) {
                coalescingKeys.remove(coalescingIdentity);
            }
            return false;
        }
        queues.get(task.priority()).offerLast(task);
        metrics.setDeferredTasks(queuedTasks.get());
        return true;
    }

    /** Processes permitted work until task or TickBudget limits are reached. */
    public ProcessingReport process(long currentTick, long deadlineNanos, TickPressure pressure) {
        return processInternal(currentTick, deadlineNanos, pressure, ALWAYS_HAS_TIME, true);
    }

    /** Testable entry that also observes Minecraft's live spare-time allowance. */
    ProcessingReport process(
            long currentTick,
            long deadlineNanos,
            TickPressure pressure,
            BooleanSupplier serverHasTime
    ) {
        return processInternal(currentTick, deadlineNanos, pressure, serverHasTime, true);
    }

    /** Allocation-free processing entry used by the Tick Engine lifecycle. */
    void processTick(
            long currentTick,
            long deadlineNanos,
            TickPressure pressure,
            BooleanSupplier serverHasTime
    ) {
        processInternal(currentTick, deadlineNanos, pressure, serverHasTime, false);
    }

    private ProcessingReport processInternal(
            long currentTick,
            long deadlineNanos,
            TickPressure pressure,
            BooleanSupplier serverHasTime,
            boolean createReport
    ) {
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        Settings current = settings;
        if (!current.enabled()) {
            return createReport ? new ProcessingReport(0, 0, 0, queuedTasks.get()) : null;
        }
        int processed = 0;
        int deferred = 0;
        int stale = 0;
        int attempted = 0;
        int passLimit = Math.min(current.maximumTasksPerTick(), queuedTasks.get());

        while (attempted < passLimit && serverHasTime.getAsBoolean()) {
            TickTask task = pollNext(pressure, currentTick, attempted);
            if (task == null) {
                break;
            }
            attempted++;
            if (!serverHasTime.getAsBoolean()) {
                queues.get(task.priority()).offerFirst(task);
                metrics.recordDeferred(task.subsystem());
                deferred++;
                break;
            }
            if (task.isStale(currentTick)) {
                release(task);
                stale++;
                continue;
            }
            if (!bypassesBudget(task.priority()) && System.nanoTime() >= deadlineNanos) {
                queues.get(task.priority()).offerFirst(task);
                metrics.recordDeferred(task.subsystem());
                deferred++;
                break;
            }

            long started = System.nanoTime();
            TickTask.Result result = TickTask.Result.COMPLETE;
            try {
                result = Objects.requireNonNullElse(task.work().run(), TickTask.Result.COMPLETE);
            } catch (Exception exception) {
                metrics.recordFailed(task.subsystem());
                logTaskFailure(task, exception);
            }
            metrics.recordExecution(task.subsystem(), Math.max(0L, System.nanoTime() - started), currentTick);
            processed++;

            if (result == TickTask.Result.DEFER) {
                queues.get(task.priority()).offerLast(task);
                metrics.recordDeferred(task.subsystem());
                deferred++;
            } else {
                release(task);
            }
        }

        metrics.setDeferredTasks(queuedTasks.get());
        return createReport ? new ProcessingReport(processed, deferred, stale, queuedTasks.get()) : null;
    }

    /** Clears deferred work and deduplication ownership during server shutdown. */
    public void clear() {
        queues.values().forEach(ConcurrentLinkedDeque::clear);
        subsystemQueueSizes.clear();
        coalescingKeys.clear();
        lastErrorLogs.clear();
        queuedTasks.set(0);
        metrics.setDeferredTasks(0);
    }

    public int queuedTasks() {
        return queuedTasks.get();
    }

    public double queuePressure() {
        return Math.min(1.0D, queuedTasks.get() / (double) Math.max(1, settings.maximumQueuedTasks()));
    }

    private TickTask pollNext(TickPressure pressure, long currentTick, int processed) {
        // Reserve one in eight relaxed/busy slots for the oldest permitted lower-priority work.
        if (processed > 0 && processed % FAIRNESS_INTERVAL == 0) {
            TickTask fairTask = pollLowPriority(pressure, currentTick);
            if (fairTask != null) {
                return fairTask;
            }
        }
        for (TickPriority priority : PRIORITIES) {
            ConcurrentLinkedDeque<TickTask> queue = queues.get(priority);
            if (!permitted(priority, pressure, currentTick)) {
                TickTask waiting = queue.peekFirst();
                if (waiting != null) {
                    metrics.recordThrottled(waiting.subsystem());
                }
                continue;
            }
            TickTask task = queue.pollFirst();
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    private TickTask pollLowPriority(TickPressure pressure, long currentTick) {
        for (int index = PRIORITIES.length - 1; index >= 0; index--) {
            TickPriority priority = PRIORITIES[index];
            if (permitted(priority, pressure, currentTick)) {
                TickTask task = queues.get(priority).pollFirst();
                if (task != null) {
                    return task;
                }
            }
        }
        return null;
    }

    private static boolean permitted(TickPriority priority, TickPressure pressure, long currentTick) {
        return switch (pressure) {
            case RELAXED -> true;
            case BUSY -> priority != TickPriority.IDLE || Math.floorMod(currentTick, 4L) == 0L;
            case HIGH -> priority != TickPriority.IDLE;
            case CRITICAL -> priority == TickPriority.CRITICAL
                    || priority == TickPriority.GAMEPLAY
                    || priority == TickPriority.NORMAL;
            case OVERLOADED -> priority == TickPriority.CRITICAL || priority == TickPriority.GAMEPLAY;
        };
    }

    private static boolean bypassesBudget(TickPriority priority) {
        return priority == TickPriority.CRITICAL || priority == TickPriority.GAMEPLAY;
    }

    private boolean reserveCapacity(String subsystem, Settings current) {
        while (true) {
            int total = queuedTasks.get();
            if (total >= current.maximumQueuedTasks()) {
                return false;
            }
            if (queuedTasks.compareAndSet(total, total + 1)) {
                break;
            }
        }
        AtomicInteger subsystemTotal = subsystemQueueSizes.computeIfAbsent(subsystem, ignored -> new AtomicInteger());
        if (subsystemTotal.incrementAndGet() <= current.maximumTasksPerSubsystem()) {
            return true;
        }
        subsystemTotal.decrementAndGet();
        queuedTasks.decrementAndGet();
        return false;
    }

    private void release(TickTask task) {
        queuedTasks.updateAndGet(value -> Math.max(0, value - 1));
        AtomicInteger subsystemTotal = subsystemQueueSizes.get(task.subsystem());
        if (subsystemTotal != null && subsystemTotal.decrementAndGet() <= 0) {
            subsystemQueueSizes.remove(task.subsystem(), subsystemTotal);
        }
        String coalescingIdentity = task.coalescingIdentity();
        if (!coalescingIdentity.isEmpty()) {
            coalescingKeys.remove(coalescingIdentity);
        }
    }

    private void logTaskFailure(TickTask task, Exception exception) {
        String signature = task.subsystem() + ':' + exception.getClass().getName();
        AtomicLong lastLog = lastErrorLogs.computeIfAbsent(signature, ignored -> new AtomicLong());
        long now = System.nanoTime();
        long previous = lastLog.get();
        if ((previous == 0L || now - previous >= ERROR_LOG_INTERVAL_NANOS)
                && lastLog.compareAndSet(previous, now)) {
            ModConstants.LOGGER.error(
                    "[WO TickEngine] Deferred task failed (subsystem: {}, priority: {}, context: '{}')",
                    task.subsystem(), task.priority(), task.context(), exception
            );
        }
    }

    /** Runtime queue bounds. */
    public record Settings(
            boolean enabled,
            int maximumTasksPerTick,
            int maximumQueuedTasks,
            int maximumTasksPerSubsystem
    ) {
        public static Settings defaults() {
            return new Settings(true, 64, 2048, 256);
        }

        private Settings normalized() {
            int queued = Math.max(1, maximumQueuedTasks);
            return new Settings(enabled, Math.max(1, maximumTasksPerTick), queued,
                    Math.max(1, Math.min(queued, maximumTasksPerSubsystem)));
        }
    }

    /** One pass's outcomes. */
    public record ProcessingReport(int processed, int deferred, int stale, int remaining) {
    }
}
