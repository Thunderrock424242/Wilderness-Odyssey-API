package com.thunder.wildernessodysseyapi.performance.background;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Batches Wilderness Odyssey-owned analytics and non-save IO work.
 *
 * <p>This service must not be used for Minecraft world saves or other durability
 * operations whose timing is owned by the server. Sinks execute through the
 * bounded {@link AsyncComputeManager} using immutable batch snapshots.</p>
 */
public final class AnalyticsBatcher {
    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;
    private static final int MAX_CHANNELS_PER_PASS = 16;

    private final ConcurrentHashMap<String, Channel<?>> channels = new ConcurrentHashMap<>();
    private final AtomicInteger queuedEvents = new AtomicInteger();
    private final BackgroundMetrics metrics;
    private volatile Settings settings = Settings.defaults();

    public AnalyticsBatcher(BackgroundMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Replaces global queue and delay bounds. */
    public void configure(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings").normalized();
    }

    /** Registers one typed analytics channel exactly once. */
    public <T> Channel<T> registerChannel(String subsystem, String name, BatchSink<T> sink) {
        String id = normalize(subsystem) + ':' + normalize(name);
        Channel<T> channel = new Channel<>(id, Objects.requireNonNull(sink, "sink"));
        Channel<?> previous = channels.putIfAbsent(id, channel);
        if (previous != null) {
            throw new IllegalStateException("Analytics batch channel already registered: " + id);
        }
        return channel;
    }

    /** Adds one immutable data event without performing IO on the caller. */
    public <T> boolean queue(Channel<T> channel, T immutableEvent, long currentTick) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(immutableEvent, "immutableEvent");
        verifyChannel(channel);
        Settings current = settings;
        if (!current.enabled()) {
            return false;
        }
        int total = queuedEvents.incrementAndGet();
        if (total > current.maximumQueuedEvents()) {
            queuedEvents.decrementAndGet();
            return false;
        }
        channel.add(immutableEvent, currentTick);
        metrics.setQueuedAnalyticsEvents(total);
        return true;
    }

    /** Submits due immutable batches to the bounded worker pool. */
    public int flushDue(long currentTick, AsyncComputeManager asyncManager, long deadlineNanos) {
        return flushDue(currentTick, asyncManager, deadlineNanos, ALWAYS_HAS_TIME);
    }

    /** Submits batches only while Minecraft's live spare-time allowance remains. */
    public int flushDue(
            long currentTick,
            AsyncComputeManager asyncManager,
            long deadlineNanos,
            BooleanSupplier serverHasTime
    ) {
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        Settings current = settings;
        if (!current.enabled()) {
            return 0;
        }
        int submitted = 0;
        for (Channel<?> channel : channels.values()) {
            if (submitted >= MAX_CHANNELS_PER_PASS
                    || !serverHasTime.getAsBoolean()
                    || System.nanoTime() >= deadlineNanos) {
                break;
            }
            if (flushChannelUnchecked(channel, currentTick, asyncManager, current)) {
                submitted++;
            }
        }
        metrics.setQueuedAnalyticsEvents(queuedEvents.get());
        return submitted;
    }

    /** Discards queued events after their server owner has stopped. */
    public void clear() {
        channels.values().forEach(Channel::clear);
        queuedEvents.set(0);
        metrics.setQueuedAnalyticsEvents(0);
    }

    public int queuedEvents() {
        return queuedEvents.get();
    }

    @SuppressWarnings("unchecked")
    private <T> boolean flushChannelUnchecked(
            Channel<?> channel,
            long currentTick,
            AsyncComputeManager asyncManager,
            Settings settings
    ) {
        return flushChannel((Channel<T>) channel, currentTick, asyncManager, settings);
    }

    private <T> boolean flushChannel(
            Channel<T> channel,
            long currentTick,
            AsyncComputeManager asyncManager,
            Settings settings
    ) {
        List<T> batch = channel.drainIfDue(currentTick, settings.maximumBatchSize(), settings.maximumDelayTicks());
        if (batch.isEmpty()) {
            return false;
        }
        queuedEvents.addAndGet(-batch.size());
        boolean accepted = asyncManager.submitWithoutResult("analytics/" + channel.id, batch, channel.sink::process);
        if (!accepted) {
            channel.requeue(batch, currentTick);
            queuedEvents.addAndGet(batch.size());
        }
        return accepted;
    }

    private void verifyChannel(Channel<?> channel) {
        if (channels.get(channel.id) != channel) {
            throw new IllegalArgumentException("Analytics channel is not registered with this batcher: " + channel.id);
        }
    }

    private static String normalize(String value) {
        String normalized = Objects.requireNonNullElse(value, "unknown").trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    /** Token representing a type-safe registered analytics channel. */
    public static final class Channel<T> {
        private final String id;
        private final BatchSink<T> sink;
        private final ArrayDeque<T> events = new ArrayDeque<>();
        private long firstQueuedTick = Long.MIN_VALUE;

        private Channel(String id, BatchSink<T> sink) {
            this.id = id;
            this.sink = sink;
        }

        public String id() {
            return id;
        }

        private synchronized void add(T event, long tick) {
            if (events.isEmpty()) {
                firstQueuedTick = tick;
            }
            events.addLast(event);
        }

        private synchronized List<T> drainIfDue(long tick, int maximumBatchSize, int maximumDelayTicks) {
            if (events.isEmpty()) {
                return List.of();
            }
            long age = tick >= firstQueuedTick ? tick - firstQueuedTick : 0L;
            if (events.size() < maximumBatchSize && age < maximumDelayTicks) {
                return List.of();
            }
            int count = Math.min(maximumBatchSize, events.size());
            ArrayList<T> batch = new ArrayList<>(count);
            while (batch.size() < count) {
                batch.add(events.removeFirst());
            }
            firstQueuedTick = events.isEmpty() ? Long.MIN_VALUE : tick;
            return List.copyOf(batch);
        }

        private synchronized void requeue(List<T> batch, long tick) {
            for (int index = batch.size() - 1; index >= 0; index--) {
                events.addFirst(batch.get(index));
            }
            firstQueuedTick = tick;
        }

        private synchronized void clear() {
            events.clear();
            firstQueuedTick = Long.MIN_VALUE;
        }
    }

    /** Worker-side processing of one immutable analytics batch. */
    @FunctionalInterface
    public interface BatchSink<T> {
        void process(List<T> immutableEvents) throws Exception;
    }

    /** Global analytics batching limits. */
    public record Settings(
            boolean enabled,
            int maximumBatchSize,
            int maximumDelayTicks,
            int maximumQueuedEvents
    ) {
        public static Settings defaults() {
            return new Settings(true, 64, 100, 4096);
        }

        private Settings normalized() {
            return new Settings(enabled, Math.max(1, maximumBatchSize), Math.max(1, maximumDelayTicks),
                    Math.max(1, maximumQueuedEvents));
        }
    }
}
