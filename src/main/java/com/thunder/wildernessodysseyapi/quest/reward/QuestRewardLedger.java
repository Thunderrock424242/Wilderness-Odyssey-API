package com.thunder.wildernessodysseyapi.quest.reward;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestPublicationStore;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestStoreMetadata;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Ordered, checksummed and forced reward journal under the publication store's exclusive lease. */
public final class QuestRewardLedger {
    public enum State { EARNED, RESERVED, DELIVERED, NEEDS_REVIEW }
    public enum DeliveryOutcome { DELIVERED, NOT_APPLIED, UNKNOWN }
    public record Receipt(QuestRewardKey key, RewardDefinition originalValue, String definitionHash, State state,
                          UUID reservationId, UUID requestId) { }
    public record EarnResult(boolean accepted) { }
    public record ReservationResult(boolean accepted, boolean fresh, UUID reservationId, RewardDefinition originalValue) { }
    private static final Gson GSON = new Gson();
    private static final int MAX_RECORD_BYTES = 16 * 1024;
    private static final long MAX_JOURNAL_BYTES = 64L * 1024 * 1024;
    private final QuestPublicationStore store;
    private final Path path;
    private volatile Map<QuestRewardKey, Receipt> receipts = Map.of();
    private volatile Map<UUID, Map<QuestRewardKey, Receipt>> playerReceipts = Map.of();
    private volatile boolean locked;
    private long sequence;
    private String previous = "";

    private QuestRewardLedger(QuestPublicationStore store, Path path) { this.store = store; this.path = path; }

    public static CompletionStage<QuestRewardLedger> open(QuestPublicationStore store) {
        return store.io().submit(() -> {
            var ledger = new QuestRewardLedger(store, store.transactionPath("reward-ledger.jsonl"));
            ledger.restore();
            // A reservation surviving a process boundary has an unknowable Minecraft effect outcome.
            for (var receipt : new ArrayList<>(ledger.receipts.values())) {
                if (receipt.state() == State.RESERVED) ledger.append(new Receipt(receipt.key(), receipt.originalValue(),
                        receipt.definitionHash(), State.NEEDS_REVIEW, receipt.reservationId(), receipt.requestId()));
            }
            return ledger;
        });
    }

    public Map<QuestRewardKey, Receipt> receipts() { return receipts; }
    public boolean locked() { return locked; }

    public CompletionStage<EarnResult> recordEarned(QuestRewardKey key, RewardDefinition value, String hash) {
        return store.io().submit(() -> {
            requireWritable();
            if (!key.worldId().equals(store.metadata().worldId()) || !key.rewardId().equals(value.id()) || !hash.matches("[0-9a-f]{64}")) {
                return new EarnResult(false);
            }
            // Validate even trusted Java producers before persisting typed values.
            new QuestDefinitionCodec().reward(rewardJson(value));
            var old = receipts.get(key);
            if (old != null) return new EarnResult(old.originalValue().equals(value) && old.definitionHash().equals(hash));
            if (receipts.size() >= 100_000) throw new IOException("Quest reward identity capacity reached; existing receipts are preserved.");
            append(new Receipt(key, value, hash, State.EARNED, null, null));
            return new EarnResult(true);
        });
    }

    public CompletionStage<ReservationResult> reserve(QuestRewardKey key, UUID request) {
        return store.io().submit(() -> {
            requireWritable();
            var old = receipts.get(key);
            if (old == null || old.state() != State.EARNED) {
                boolean retry = old != null && old.state() == State.RESERVED && request.equals(old.requestId());
                return new ReservationResult(retry, false, retry ? old.reservationId() : null, old == null ? null : old.originalValue());
            }
            var reserved = new Receipt(key, old.originalValue(), old.definitionHash(), State.RESERVED, UUID.randomUUID(), request);
            append(reserved);
            return new ReservationResult(true, true, reserved.reservationId(), reserved.originalValue());
        });
    }

    /** NOT_APPLIED is legal only when the game-thread sink proves no effect was attempted. */
    public CompletionStage<Void> recordDelivery(UUID reservation, DeliveryOutcome outcome) {
        return store.io().submit(() -> {
            requireWritable();
            var old = receipts.values().stream().filter(receipt -> reservation.equals(receipt.reservationId())).findFirst()
                    .orElseThrow(() -> new IOException("Unknown reward reservation."));
            State target = switch (outcome) {
                case DELIVERED -> State.DELIVERED;
                case NOT_APPLIED -> State.EARNED;
                case UNKNOWN -> State.NEEDS_REVIEW;
            };
            if (old.state() == target) return null;
            if (old.state() != State.RESERVED) throw new IOException("Terminal reward receipts cannot be rewritten.");
            append(new Receipt(old.key(), old.originalValue(), old.definitionHash(), target,
                    target == State.EARNED ? null : reservation, target == State.EARNED ? null : old.requestId()));
            return null;
        });
    }

    /** Explicit bounded compaction retains every permanent identity, including delivered and uncertain claims. */
    public CompletionStage<Void> compact() {
        return store.io().submit(() -> {
            requireWritable();
            var output = new ByteArrayOutputStream();
            output.write(store.metadata().encode()); output.write('\n');
            long next = 0; String parent = "";
            for (var receipt : receipts.values()) {
                byte[] line = encode(receipt, ++next, parent);
                parent = checksum(line);
                output.write(line); output.write('\n');
                if (output.size() > MAX_JOURNAL_BYTES) throw new IOException("Compacted reward journal exceeds its size limit.");
            }
            try {
                QuestPublicationStore.atomicWrite(path.getParent(), path, output.toByteArray());
                sequence = next; previous = parent;
            } catch (IOException exception) { locked = true; throw exception; }
            return null;
        });
    }

    /** Restores completion and immutable entitlements when player autosave is older than the forced journal. */
    public QuestPlayerProgress reconcile(QuestPlayerProgress player) {
        return reconcileHistory(player, playerReceipts.getOrDefault(player.playerId(), Map.of()).values()).progress();
    }

    record Reconciliation(QuestPlayerProgress progress, long entitlementVisits, long runVisits) { }
    private record EntitlementKey(ResourceLocation quest, long run, ResourceLocation reward) { }
    static Reconciliation reconcileHistory(QuestPlayerProgress player, java.util.Collection<Receipt> history) {
        long entitlementVisits = 0; long runVisits = 0;
        var earned = new ArrayList<>(player.earned());
        var identities = new HashMap<EntitlementKey, QuestPlayerProgress.EarnedReward>();
        for (var value : earned) {
            entitlementVisits++;
            identities.put(new EntitlementKey(value.quest(), value.runNumber(), value.originalValue().id()), value);
        }
        var latest = new HashMap<ResourceLocation, Receipt>();
        for (var receipt : history) {
            if (!receipt.key().playerId().equals(player.playerId())) continue;
            entitlementVisits++;
            var key = receipt.key();
            var entitlement = new QuestPlayerProgress.EarnedReward(key.questId(), key.runNumber(), receipt.originalValue(), receipt.definitionHash());
            var old = identities.putIfAbsent(new EntitlementKey(key.questId(), key.runNumber(), key.rewardId()), entitlement);
            if (old != null && !old.equals(entitlement)) throw new IllegalStateException("Player and durable reward values disagree; progress is preserved.");
            if (old == null) {
                if (earned.size() >= 100_000) throw new IllegalStateException("Recovered entitlements exceed the progress history limit; state is preserved.");
                earned.add(entitlement);
            }
            latest.merge(key.questId(), receipt, (left, right) -> left.key().runNumber() >= right.key().runNumber() ? left : right);
        }
        long ranges = latest.values().stream().mapToLong(receipt -> receipt.key().runNumber() + 1).sum();
        if (ranges > 100_000) throw new IllegalStateException("Recovered runs exceed the bounded history limit; state is preserved.");
        var runs = new HashMap<>(player.runs()); var active = new HashMap<>(player.activeRuns());
        for (var receipt : latest.values()) {
            var key = receipt.key();
            if (key.runNumber() > player.currentRun(key.questId())) active.put(key.questId(), key.runNumber());
            for (long number = 0; number <= key.runNumber(); number++) {
                runVisits++;
                var runKey = new QuestPlayerProgress.RunKey(key.questId(), number); var run = runs.get(runKey);
                if (run == null && runs.size() >= 100_000) throw new IllegalStateException("Recovered runs exceed the progress history limit; state is preserved.");
                if (run == null || !run.completed()) runs.put(runKey, new QuestPlayerProgress.Run(run == null ? 1 : run.definitionVersion(),
                        run == null ? receipt.definitionHash() : run.definitionHash(), run == null ? Map.of() : run.counts(), true,
                        run == null ? 0 : run.completedTick(), run != null && run.archived()));
            }
        }
        if (earned.equals(player.earned()) && runs.equals(player.runs()) && active.equals(player.activeRuns())) return new Reconciliation(player, entitlementVisits, runVisits);
        return new Reconciliation(new QuestPlayerProgress(player.playerId(), Math.addExact(player.revision(), 1), runs, active, earned,
                player.tracking(), player.recentOccurrences(), player.observed()), entitlementVisits, runVisits);
    }

    private void restore() throws IOException {
        if (Files.size(path) > MAX_JOURNAL_BYTES) throw new IOException("Quest reward journal exceeds its bounded recovery limit.");
        var recovered = new LinkedHashMap<QuestRewardKey, Receipt>();
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] header = line(input);
            if (header == null || !store.metadata().equals(QuestStoreMetadata.decode(header))) throw new IOException("Reward journal identity mismatch.");
            byte[] bytes;
            while ((bytes = line(input)) != null) {
                var json = new QuestDefinitionCodec().readBounded(new ByteArrayInputStream(bytes));
                Receipt receipt;
                try {
                    if (!json.keySet().equals(Set.of("version", "sequence", "previous", "world", "player", "quest", "run", "reward", "hash", "state", "reservation", "request", "checksum"))
                            || json.get("version").getAsBigDecimal().intValueExact() != 1
                            || json.get("sequence").getAsBigDecimal().longValueExact() != sequence + 1
                            || !json.get("previous").getAsString().equals(previous)) throw new IllegalArgumentException("Invalid journal sequence.");
                    var key = new QuestRewardKey(UUID.fromString(json.get("world").getAsString()), UUID.fromString(json.get("player").getAsString()),
                            id(json.get("quest").getAsString()), json.get("run").getAsBigDecimal().longValueExact(), id(json.getAsJsonObject("reward").get("id").getAsString()));
                    receipt = new Receipt(key, new QuestDefinitionCodec().reward(json.getAsJsonObject("reward")), json.get("hash").getAsString(),
                            State.valueOf(json.get("state").getAsString()), nullableUuid(json.get("reservation").getAsString()), nullableUuid(json.get("request").getAsString()));
                    if (!key.worldId().equals(store.metadata().worldId()) || !receipt.definitionHash().matches("[0-9a-f]{64}")
                            || (receipt.state() == State.EARNED) != (receipt.reservationId() == null && receipt.requestId() == null)
                            || receipt.state() != State.EARNED && (receipt.reservationId() == null || receipt.requestId() == null)
                            || !java.util.Arrays.equals(encode(receipt, sequence + 1, previous), bytes)) throw new IllegalArgumentException("Invalid journal value/checksum.");
                    var old = recovered.get(key);
                    if (old != null && (!old.originalValue().equals(receipt.originalValue()) || !old.definitionHash().equals(receipt.definitionHash())
                            || old.state() == State.DELIVERED || old.state() == State.NEEDS_REVIEW
                            || old.state() == State.EARNED && receipt.state() != State.RESERVED
                            || old.state() == State.RESERVED && receipt.state() == State.RESERVED)) throw new IllegalArgumentException("Invalid reward transition.");
                    if (recovered.size() >= 100_000 && old == null) throw new IllegalArgumentException("Too many reward identities.");
                    recovered.put(key, receipt);
                    previous = checksum(bytes); sequence++;
                } catch (RuntimeException exception) { throw new IOException("Reward journal is corrupt or unsupported; claims remain locked.", exception); }
            }
        }
        receipts = Map.copyOf(recovered);
        var players = new HashMap<UUID, Map<QuestRewardKey, Receipt>>();
        for (var receipt : recovered.values()) players.computeIfAbsent(receipt.key().playerId(), ignored -> new HashMap<>()).put(receipt.key(), receipt);
        players.replaceAll((id, values) -> Map.copyOf(values)); playerReceipts = Map.copyOf(players);
    }

    private void append(Receipt receipt) throws IOException {
        byte[] bytes = encode(receipt, sequence + 1, previous);
        if (bytes.length > MAX_RECORD_BYTES || Files.size(path) + bytes.length + 1 > MAX_JOURNAL_BYTES) throw new IOException("Reward journal capacity reached; compact it before more claims.");
        try (var channel = FileChannel.open(store.transactionPath("reward-ledger.jsonl"), StandardOpenOption.WRITE,
                StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS)) {
            var buffer = ByteBuffer.allocate(bytes.length + 1).put(bytes).put((byte) '\n'); buffer.flip();
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        } catch (IOException exception) { locked = true; throw exception; }
        previous = checksum(bytes); sequence++;
        var copy = new LinkedHashMap<>(receipts); copy.put(receipt.key(), receipt); receipts = Map.copyOf(copy);
        var player = new HashMap<>(playerReceipts.getOrDefault(receipt.key().playerId(), Map.of())); player.put(receipt.key(), receipt);
        var players = new HashMap<>(playerReceipts); players.put(receipt.key().playerId(), Map.copyOf(player)); playerReceipts = Map.copyOf(players);
    }

    private void requireWritable() throws IOException { if (locked) throw new IOException("Reward journal is locked for review."); }
    private static ResourceLocation id(String value) { if (!value.contains(":")) throw new IllegalArgumentException("Unqualified ID."); return ResourceLocation.parse(value); }
    private static UUID nullableUuid(String value) { return value.isEmpty() ? null : UUID.fromString(value); }
    private static byte[] line(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') return output.toByteArray();
            if (output.size() >= MAX_RECORD_BYTES) throw new IOException("Reward journal record exceeds its size limit.");
            output.write(value);
        }
        if (output.size() != 0) throw new IOException("Reward journal has a truncated tail; evidence is preserved.");
        return null;
    }

    private static String checksum(byte[] bytes) throws IOException {
        return new QuestDefinitionCodec().readBounded(new ByteArrayInputStream(bytes)).get("checksum").getAsString();
    }

    private static byte[] encode(Receipt receipt, long sequence, String previous) {
        var json = new JsonObject();
        json.addProperty("version", 1); json.addProperty("sequence", sequence); json.addProperty("previous", previous);
        json.addProperty("world", receipt.key().worldId().toString()); json.addProperty("player", receipt.key().playerId().toString());
        json.addProperty("quest", receipt.key().questId().toString()); json.addProperty("run", receipt.key().runNumber());
        json.add("reward", rewardJson(receipt.originalValue())); json.addProperty("hash", receipt.definitionHash()); json.addProperty("state", receipt.state().name());
        json.addProperty("reservation", receipt.reservationId() == null ? "" : receipt.reservationId().toString());
        json.addProperty("request", receipt.requestId() == null ? "" : receipt.requestId().toString());
        json.addProperty("checksum", QuestDefinitionCodec.hash(GSON.toJson(json).getBytes(StandardCharsets.UTF_8)));
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }

    public static JsonObject rewardJson(RewardDefinition reward) {
        var json = new JsonObject(); json.addProperty("id", reward.id().toString());
        switch (reward.value()) {
            case RewardDefinition.Item item -> { json.addProperty("type", "item"); json.addProperty("item", item.item().toString()); json.addProperty("count", item.count()); }
            case RewardDefinition.Experience xp -> { json.addProperty("type", "xp"); json.addProperty("amount", xp.amount()); }
            case RewardDefinition.Lore lore -> { json.addProperty("type", "lore"); json.addProperty("lore", lore.lore().toString()); }
        }
        return json;
    }
}
