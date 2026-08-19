package com.thunder.wildernessodysseyapi.performance.background;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Opt-in aggregation channels for Wilderness Odyssey state payloads.
 *
 * <p>A channel's sender receives one immutable list per recipient, allowing the
 * owning subsystem to encode one combined custom payload. Important immediate
 * synchronization should continue to use its direct packet path.</p>
 */
public final class NetworkBatcher {
    private static final BooleanSupplier ALWAYS_HAS_TIME = () -> true;
    private static final int MAX_BATCHES_PER_PASS = 64;

    private final ConcurrentHashMap<String, Channel<?>> channels = new ConcurrentHashMap<>();
    private final AtomicInteger queuedUpdates = new AtomicInteger();
    private final BackgroundMetrics metrics;
    private volatile Settings settings = Settings.defaults();

    public NetworkBatcher(BackgroundMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Replaces global bounds used by every registered channel. */
    public void configure(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings").normalized();
    }

    /** Registers one typed subsystem channel exactly once. */
    public <T> Channel<T> registerChannel(String subsystem, String channelName, BatchSender<T> sender) {
        Objects.requireNonNull(sender, "sender");
        return registerServerChannel(subsystem, channelName,
                (server, recipientId, updates) -> sender.send(recipientId, updates));
    }

    /** Registers a typed channel whose sender needs the active server to resolve recipients. */
    public <T> Channel<T> registerServerChannel(
            String subsystem,
            String channelName,
            ServerBatchSender<T> sender
    ) {
        String id = normalize(subsystem) + ':' + normalize(channelName);
        Channel<T> channel = new Channel<>(id, Objects.requireNonNull(sender, "sender"));
        Channel<?> previous = channels.putIfAbsent(id, channel);
        if (previous != null) {
            throw new IllegalStateException("Network batch channel already registered: " + id);
        }
        return channel;
    }

    /** Queues or replaces an update with the same recipient-local deduplication key. */
    public <T> boolean queue(
            Channel<T> channel,
            UUID recipientId,
            String deduplicationKey,
            T immutableUpdate,
            long currentTick
    ) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(immutableUpdate, "immutableUpdate");
        verifyChannel(channel);
        Settings current = settings;
        if (!current.enabled()) {
            return false;
        }

        RecipientBatch<T> batch = channel.recipients.computeIfAbsent(recipientId, ignored -> new RecipientBatch<>());
        int delta = batch.put(normalizeKey(deduplicationKey), immutableUpdate, currentTick);
        if (delta == 0) {
            return true;
        }
        int total = queuedUpdates.incrementAndGet();
        if (total > current.maximumQueuedUpdates()) {
            batch.remove(normalizeKey(deduplicationKey));
            queuedUpdates.decrementAndGet();
            if (batch.isEmpty()) {
                channel.recipients.remove(recipientId, batch);
            }
            return false;
        }
        metrics.setQueuedNetworkUpdates(total);
        return true;
    }

    /** Queues the same immutable update for a caller-provided tracking-player collection. */
    public <T> int queueForPlayers(
            Channel<T> channel,
            Collection<? extends ServerPlayer> players,
            String deduplicationKey,
            T immutableUpdate,
            long currentTick
    ) {
        Objects.requireNonNull(players, "players");
        int accepted = 0;
        for (ServerPlayer player : players) {
            if (queue(channel, player.getUUID(), deduplicationKey, immutableUpdate, currentTick)) {
                accepted++;
            }
        }
        return accepted;
    }

    /** Flushes due channels on the logical server thread while capacity remains. */
    public int flushDue(MinecraftServer server, long currentTick, long deadlineNanos) {
        Objects.requireNonNull(server, "server");
        return flushDueInternal(server, currentTick, deadlineNanos, ALWAYS_HAS_TIME);
    }

    /** Flushes only while Minecraft's live spare-time allowance remains. */
    public int flushDue(
            MinecraftServer server,
            long currentTick,
            long deadlineNanos,
            BooleanSupplier serverHasTime
    ) {
        Objects.requireNonNull(server, "server");
        return flushDueInternal(server, currentTick, deadlineNanos, serverHasTime);
    }

    int flushDue(long currentTick, long deadlineNanos) {
        return flushDueInternal(null, currentTick, deadlineNanos, ALWAYS_HAS_TIME);
    }

    private int flushDueInternal(
            MinecraftServer server,
            long currentTick,
            long deadlineNanos,
            BooleanSupplier serverHasTime
    ) {
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        Settings current = settings;
        if (!current.enabled()) {
            return 0;
        }
        int flushed = 0;
        for (Channel<?> channel : channels.values()) {
            flushed += flushChannelUnchecked(channel, server, currentTick, deadlineNanos,
                    MAX_BATCHES_PER_PASS - flushed, current, serverHasTime);
            if (flushed >= MAX_BATCHES_PER_PASS
                    || !serverHasTime.getAsBoolean()
                    || System.nanoTime() >= deadlineNanos) {
                break;
            }
        }
        metrics.setQueuedNetworkUpdates(queuedUpdates.get());
        return flushed;
    }

    /** Drops pending updates when their owning server is no longer available. */
    public void clear() {
        channels.values().forEach(Channel::clear);
        queuedUpdates.set(0);
        metrics.setQueuedNetworkUpdates(0);
    }

    public int queuedUpdates() {
        return queuedUpdates.get();
    }

    @SuppressWarnings("unchecked")
    private <T> int flushChannelUnchecked(
            Channel<?> channel,
            MinecraftServer server,
            long currentTick,
            long deadlineNanos,
            int remainingBatches,
            Settings settings,
            BooleanSupplier serverHasTime
    ) {
        return flushChannel(
                (Channel<T>) channel,
                server,
                currentTick,
                deadlineNanos,
                remainingBatches,
                settings,
                serverHasTime
        );
    }

    private <T> int flushChannel(
            Channel<T> channel,
            MinecraftServer server,
            long currentTick,
            long deadlineNanos,
            int remainingBatches,
            Settings settings,
            BooleanSupplier serverHasTime
    ) {
        int flushed = 0;
        for (Map.Entry<UUID, RecipientBatch<T>> entry : channel.recipients.entrySet()) {
            if (flushed >= remainingBatches
                    || !serverHasTime.getAsBoolean()
                    || System.nanoTime() >= deadlineNanos) {
                break;
            }
            List<T> updates = entry.getValue().drainIfDue(
                    currentTick,
                    settings.maximumBatchSize(),
                    settings.maximumDelayTicks()
            );
            if (updates.isEmpty()) {
                continue;
            }
            queuedUpdates.addAndGet(-updates.size());
            if (entry.getValue().isEmpty()) {
                channel.recipients.remove(entry.getKey(), entry.getValue());
            }
            try {
                channel.sender.send(server, entry.getKey(), updates);
            } catch (Exception exception) {
                ModConstants.LOGGER.error("[Background Network] Batch dispatch failed for channel '{}'", channel.id,
                        exception);
            }
            flushed++;
        }
        return flushed;
    }

    private void verifyChannel(Channel<?> channel) {
        if (channels.get(channel.id) != channel) {
            throw new IllegalArgumentException("Network channel is not registered with this batcher: " + channel.id);
        }
    }

    private static String normalize(String value) {
        String normalized = Objects.requireNonNullElse(value, "unknown").trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private static String normalizeKey(String value) {
        return Objects.requireNonNullElse(value, "default");
    }

    /** Token representing a type-safe registered batch channel. */
    public static final class Channel<T> {
        private final String id;
        private final ServerBatchSender<T> sender;
        private final ConcurrentHashMap<UUID, RecipientBatch<T>> recipients = new ConcurrentHashMap<>();

        private Channel(String id, ServerBatchSender<T> sender) {
            this.id = id;
            this.sender = sender;
        }

        public String id() {
            return id;
        }

        private void clear() {
            recipients.clear();
        }
    }

    /** Sends one caller-defined combined payload without requiring server lookup. */
    @FunctionalInterface
    public interface BatchSender<T> {
        void send(UUID recipientId, List<T> immutableUpdates) throws Exception;
    }

    /** Sends one caller-defined combined payload with the active server and player UUID. */
    @FunctionalInterface
    public interface ServerBatchSender<T> {
        void send(MinecraftServer server, UUID recipientId, List<T> immutableUpdates) throws Exception;
    }

    /** Global network batching limits. */
    public record Settings(
            boolean enabled,
            int maximumBatchSize,
            int maximumDelayTicks,
            int maximumQueuedUpdates
    ) {
        public static Settings defaults() {
            return new Settings(true, 32, 5, 4096);
        }

        private Settings normalized() {
            return new Settings(enabled, Math.max(1, maximumBatchSize), Math.max(1, maximumDelayTicks),
                    Math.max(1, maximumQueuedUpdates));
        }
    }

    private static final class RecipientBatch<T> {
        private final LinkedHashMap<String, T> updates = new LinkedHashMap<>();
        private long firstQueuedTick = Long.MIN_VALUE;

        private synchronized int put(String key, T update, long tick) {
            T previous = updates.put(key, update);
            if (previous == null) {
                if (updates.size() == 1) {
                    firstQueuedTick = tick;
                }
                return 1;
            }
            return 0;
        }

        private synchronized void remove(String key) {
            updates.remove(key);
            if (updates.isEmpty()) {
                firstQueuedTick = Long.MIN_VALUE;
            }
        }

        private synchronized List<T> drainIfDue(long currentTick, int maximumBatchSize, int maximumDelayTicks) {
            if (updates.isEmpty()) {
                return List.of();
            }
            long age = currentTick >= firstQueuedTick ? currentTick - firstQueuedTick : 0L;
            if (updates.size() < maximumBatchSize && age < maximumDelayTicks) {
                return List.of();
            }

            int count = Math.min(maximumBatchSize, updates.size());
            ArrayList<T> drained = new ArrayList<>(count);
            var iterator = updates.entrySet().iterator();
            while (iterator.hasNext() && drained.size() < count) {
                drained.add(iterator.next().getValue());
                iterator.remove();
            }
            firstQueuedTick = updates.isEmpty() ? Long.MIN_VALUE : currentTick;
            return List.copyOf(drained);
        }

        private synchronized boolean isEmpty() {
            return updates.isEmpty();
        }
    }
}
