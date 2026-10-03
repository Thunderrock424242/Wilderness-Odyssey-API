package com.thunder.wildernessodysseyapi.quest.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinition;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardKey;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardLedger;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Authorized active-definition projection and event-driven synchronization. No draft/source objects cross this seam. */
public final class QuestViewService {
    private record Sent(String hash, long revision) { }
    private final QuestRuntime runtime;
    private final QuestRewardLedger ledger;
    private final Map<UUID, Sent> sent = new HashMap<>();
    private final QuestViewTransfers transfers;
    public QuestViewService(QuestRuntime runtime, QuestRewardLedger ledger, java.util.function.Function<UUID, ServerPlayer> players) {
        this.runtime = runtime; this.ledger = ledger;
        this.transfers = new QuestViewTransfers(runtime.session(), id -> {
            var player = players.apply(id); var active = runtime.activeSnapshot();
            if (player == null || !runtime.isCurrent(player) || active.isEmpty()) return java.util.Optional.empty();
            var progress = runtime.progress(player);
            return java.util.Optional.of(new QuestViewTransfers.Projection(active.get().hash(), runtime.publicationRevision(), progress.revision(), bytes(progress)));
        }, (id, payload) -> {
            var player = players.apply(id);
            if (player != null && runtime.isCurrent(player)) PacketDistributor.sendToPlayer(player, payload);
        }, (id, projection) -> sent.put(id, new Sent(projection.hash(), projection.revision())));
    }

    public void sendInitial(ServerPlayer player) {
        var active = runtime.activeSnapshot(); if (active.isEmpty() || !runtime.isCurrent(player)) return;
        if (!transfers.request(player.getUUID())) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Quest view synchronization is busy. Wait briefly, then request your view again."));
    }

    public void sendDirty(ServerPlayer player) {
        var active = runtime.activeSnapshot(); if (active.isEmpty() || !runtime.isCurrent(player)) return;
        var progress = runtime.progress(player); var previous = sent.get(player.getUUID());
        if (transfers.pending(player.getUUID())) { transfers.request(player.getUUID()); return; }
        if (previous == null || !previous.hash().equals(active.get().hash())) { sendInitial(player); return; }
        if (previous.revision() == progress.revision()) return;
        byte[] bytes = bytes(progress);
        if (bytes.length > QuestWire.CHUNK_BYTES) { sendInitial(player); return; }
        PacketDistributor.sendToPlayer(player, new QuestProgressDeltaPayload(runtime.session(), active.get().hash(), runtime.publicationRevision(),
                previous.revision(), progress.revision(), UUID.randomUUID(), bytes));
        sent.put(player.getUUID(), new Sent(active.get().hash(), progress.revision()));
    }

    /** Sends at most four 32 KiB chunks per tick across at most eight active transfers. */
    public void pump() {
        transfers.pump();
    }
    public void clear(ServerPlayer player) { sent.remove(player.getUUID()); transfers.clear(player.getUUID()); }
    public void clear() { sent.clear(); transfers.clear(); }

    private byte[] bytes(QuestPlayerProgress progress) {
        byte[] bytes = new Gson().toJson(project(runtime.availableSnapshot(), progress, ledger.receipts())).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > QuestWire.MAX_VIEW_BYTES) throw new IllegalStateException("Player quest view exceeds its synchronization limit.");
        return bytes;
    }

    public static boolean disclosed(QuestCampaignSnapshot snapshot, QuestPlayerProgress progress, ResourceLocation id) {
        var quest = snapshot.quests().get(id);
        if (quest == null || snapshot.chapters().get(quest.chapter()).disabled()) return false;
        if (!quest.hidden() || progress.run(id) != null) return true;
        return quest.prerequisiteMode() == QuestDefinition.PrerequisiteMode.ALL
                ? quest.prerequisites().stream().allMatch(progress::completedEver) : quest.prerequisites().stream().anyMatch(progress::completedEver);
    }

    public static JsonObject project(QuestCampaignSnapshot snapshot, QuestPlayerProgress progress, Map<QuestRewardKey, QuestRewardLedger.Receipt> receipts) {
        return project(java.util.Optional.of(snapshot), progress, receipts);
    }
    public static JsonObject project(java.util.Optional<QuestCampaignSnapshot> available, QuestPlayerProgress progress, Map<QuestRewardKey, QuestRewardLedger.Receipt> receipts) {
        var root = new JsonObject(); var quests = new JsonArray();
        available.ifPresent(snapshot -> snapshot.quests().values().stream().sorted(java.util.Comparator.comparing(value -> value.id().toString())).forEach(quest -> {
            if (!disclosed(snapshot, progress, quest.id())) return;
            var json = new JsonObject(); json.addProperty("id", quest.id().toString()); json.addProperty("title", quest.title());
            json.addProperty("description", quest.description()); json.addProperty("icon", quest.icon().toString());
            json.addProperty("completed", progress.completed(quest.id())); json.addProperty("run", progress.currentRun(quest.id()));
            var objectives = new JsonArray();
            for (var objective : quest.objectives()) {
                var value = new JsonObject(); value.addProperty("id", objective.id().toString()); value.addProperty("goal", objective.goal());
                value.addProperty("count", progress.count(quest.id(), objective.id())); objectives.add(value);
            }
            json.add("objectives", objectives); quests.add(json);
        }));
        var earned = new JsonArray();
        var statuses = new HashMap<String, String>();
        receipts.values().stream().filter(entry -> entry.key().playerId().equals(progress.playerId())).forEach(entry -> statuses.put(
                entry.key().questId() + "/" + entry.key().runNumber() + "/" + entry.key().rewardId(), entry.state().name()));
        // Bounded current presentation; permanent historical receipts remain server-owned and inspectable.
        int start = Math.max(0, progress.earned().size() - 2000);
        for (var value : progress.earned().subList(start, progress.earned().size())) {
            var json = new JsonObject(); json.addProperty("quest", value.quest().toString()); json.addProperty("run", value.runNumber());
            json.add("reward", QuestRewardLedger.rewardJson(value.originalValue()));
            json.addProperty("status", statuses.getOrDefault(value.quest() + "/" + value.runNumber() + "/" + value.originalValue().id(), "PENDING")); earned.add(json);
        }
        root.add("quests", quests); root.add("earned", earned); root.addProperty("tracking", progress.tracking() == null
                || available.filter(snapshot -> disclosed(snapshot, progress, progress.tracking())).isEmpty() ? "" : progress.tracking().toString());
        return root;
    }
}
