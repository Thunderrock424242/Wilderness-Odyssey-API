package com.thunder.wildernessodysseyapi.performance.background;

import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded cooperative scheduler for Wilderness Odyssey background work.
 *
 * <p>Submission is thread-safe, while task callbacks are invoked only by the
 * logical server thread. The scheduler never blocks waiting for work.</p>
 */
public final class BackgroundWorkScheduler implements BackgroundSchedulerControl {
    private static final long ERROR_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10L);
    private static final WorkPriority[] PRIORITIES = WorkPriority.values();

    private final EnumMap<WorkPriority, ConcurrentLinkedDeque<BackgroundTask>> queues =
            new EnumMap<>(WorkPriority.class);
    private final ConcurrentHashMap<String, AtomicInteger> subsystemQueueSizes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastErrorLogs = new ConcurrentHashMap<>();
    private final AtomicInteger queuedTasks = new AtomicInteger();
    private final BackgroundMetrics metrics;
    private final NanoClock clock;

    private volatile Settings settings = Settings.defaults();
    private volatile BackgroundBudgetControl externalControl = BackgroundBudgetControl.UNRESTRICTED;

    public BackgroundWorkScheduler(BackgroundMetrics metrics) {
        this(metrics, System::nanoTime);
    }

    BackgroundWorkScheduler(BackgroundMetrics metrics, NanoClock clock) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
        for (WorkPriority priority : WorkPriority.values()) {
            queues.put(priority, new ConcurrentLinkedDeque<>());
        }
    }

    /** Replaces runtime limits; existing queued work remains bounded and ordered. */
    public void configure(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings").normalized();
    }

    /** Submits a task without executing it on the calling thread. */
    public boolean submit(BackgroundTask task) {
        Objects.requireNonNull(task, "task");
        Settings current = settings;
        if (!current.enabled()) {
            metrics.recordRejected(task.subsystem());
            return false;
        }
        if (!reserveCapacity(task.subsystem(), current)) {
            metrics.recordRejected(task.subsystem());
            return false;
        }

        queues.get(task.priority()).offerLast(task);
        metrics.setQueuedTasks(queuedTasks.get());
        return true;
    }

    /**
     * Processes a bounded pass using the smaller of local, caller, and external budgets.
     */
    public ProcessingReport process(long currentTick, long callerBudgetNanos) {
        return processInternal(currentTick, callerBudgetNanos, true);
    }

    /** Allocation-free processing entry used by the end-of-tick lifecycle. */
    void processTick(long currentTick, long callerBudgetNanos) {
        processInternal(currentTick, callerBudgetNanos, false);
    }

    private ProcessingReport processInternal(long currentTick, long callerBudgetNanos, boolean createReport) {
        Settings current = settings;
        if (!current.enabled()) {
            return createReport ? new ProcessingReport(0, 0, queuedTasks.get(), 0L) : null;
        }

        BackgroundBudgetControl control = externalControl;
        long scaledBudget = (long) (current.maximumTimeNanos() * control.budgetMultiplier());
        long budgetNanos = Math.min(Math.max(0L, callerBudgetNanos),
                Math.min(scaledBudget, control.maximumBudgetNanos()));
        long startedNanos = clock.nanoTime();
        int processed = 0;
        int deferred = 0;
        int passLimit = Math.min(current.maximumTasksPerPass(), queuedTasks.get());

        while (processed < passLimit) {
            BackgroundTask task = pollNext(control);
            if (task == null) {
                break;
            }

            long beforeTask = clock.nanoTime();
            if (task.priority() != WorkPriority.CRITICAL
                    && beforeTask - startedNanos >= budgetNanos) {
                queues.get(task.priority()).offerFirst(task);
                metrics.recordDeferred(task.subsystem());
                deferred++;
                break;
            }

            BackgroundTask.Result result = BackgroundTask.Result.COMPLETE;
            try {
                result = Objects.requireNonNullElse(task.work().run(), BackgroundTask.Result.COMPLETE);
            } catch (Exception exception) {
                metrics.recordFailed(task.subsystem());
                logTaskFailure(task, exception);
            }
            long elapsedNanos = Math.max(0L, clock.nanoTime() - beforeTask);
            metrics.recordProcessed(task.subsystem(), elapsedNanos);
            processed++;

            if (result == BackgroundTask.Result.DEFER) {
                queues.get(task.priority()).offerLast(task);
                metrics.recordDeferred(task.subsystem());
                deferred++;
            } else {
                releaseCapacity(task.subsystem());
            }
        }

        metrics.setQueuedTasks(queuedTasks.get());
        return createReport
                ? new ProcessingReport(processed, deferred, queuedTasks.get(),
                Math.max(0L, clock.nanoTime() - startedNanos))
                : null;
    }

    /** Clears pending optional work during server shutdown. */
    public void clear() {
        for (ConcurrentLinkedDeque<BackgroundTask> queue : queues.values()) {
            queue.clear();
        }
        subsystemQueueSizes.clear();
        lastErrorLogs.clear();
        queuedTasks.set(0);
        metrics.setQueuedTasks(0);
        externalControl = BackgroundBudgetControl.UNRESTRICTED;
    }

    @Override
    public void setExternalControl(BackgroundBudgetControl control) {
        externalControl = Objects.requireNonNullElse(control, BackgroundBudgetControl.UNRESTRICTED);
    }

    @Override
    public double queuePressure() {
        int capacity = Math.max(1, settings.maximumQueuedTasks());
        return Math.min(1.0D, queuedTasks.get() / (double) capacity);
    }

    @Override
    public int queuedTasks() {
        return queuedTasks.get();
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

        AtomicInteger subsystemSize = subsystemQueueSizes.computeIfAbsent(subsystem, ignored -> new AtomicInteger());
        int subsystemTotal = subsystemSize.incrementAndGet();
        if (subsystemTotal <= current.maximumTasksPerSubsystem()) {
            return true;
        }
        subsystemSize.decrementAndGet();
        queuedTasks.decrementAndGet();
        return false;
    }

    private void releaseCapacity(String subsystem) {
        queuedTasks.updateAndGet(value -> Math.max(0, value - 1));
        AtomicInteger subsystemSize = subsystemQueueSizes.get(subsystem);
        if (subsystemSize != null && subsystemSize.decrementAndGet() <= 0) {
            subsystemQueueSizes.remove(subsystem, subsystemSize);
        }
    }

    private BackgroundTask pollNext(BackgroundBudgetControl control) {
        for (WorkPriority priority : PRIORITIES) {
            if (priority == WorkPriority.BACKGROUND && !control.backgroundAllowed()) {
                continue;
            }
            if (priority == WorkPriority.IDLE && !control.idleAllowed()) {
                continue;
            }
            BackgroundTask task = queues.get(priority).pollFirst();
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    private void logTaskFailure(BackgroundTask task, Exception exception) {
        String signature = task.subsystem() + ':' + exception.getClass().getName();
        AtomicLong lastLog = lastErrorLogs.computeIfAbsent(signature, ignored -> new AtomicLong());
        long now = clock.nanoTime();
        long previous = lastLog.get();
        if ((previous == 0L || now - previous >= ERROR_LOG_INTERVAL_NANOS)
                && lastLog.compareAndSet(previous, now)) {
            ModConstants.LOGGER.error(
                    "[Background] Task failed (subsystem: {}, priority: {}, context: '{}')",
                    task.subsystem(), task.priority(), task.context(), exception
            );
        }
    }

    /** Runtime scheduler limits independent of NeoForge's config holder. */
    public record Settings(
            boolean enabled,
            int maximumTasksPerPass,
            long maximumTimeNanos,
            int maximumQueuedTasks,
            int maximumTasksPerSubsystem
    ) {
        public static Settings defaults() {
            return new Settings(true, 64, TimeUnit.MILLISECONDS.toNanos(2L), 2048, 256);
        }

        private Settings normalized() {
            return new Settings(
                    enabled,
                    Math.max(1, maximumTasksPerPass),
                    Math.max(0L, maximumTimeNanos),
                    Math.max(1, maximumQueuedTasks),
                    Math.max(1, Math.min(maximumTasksPerSubsystem, Math.max(1, maximumQueuedTasks)))
            );
        }
    }

    /** Summary of one processing pass. */
    public record ProcessingReport(int processed, int deferred, int remaining, long elapsedNanos) {
    }

    @FunctionalInterface
    interface NanoClock {
        long nanoTime();
    }
}
