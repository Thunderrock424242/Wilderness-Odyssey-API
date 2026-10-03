package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.quest.command.QuestOperation;
import com.thunder.wildernessodysseyapi.quest.command.QuestPermissions;
import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.integration.QuestAvailability;
import com.thunder.wildernessodysseyapi.quest.network.QuestViewService;
import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardDelivery;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardKey;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardLedger;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardService;
import com.thunder.wildernessodysseyapi.quest.validation.QuestValidationReport;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestIoDispatcher;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestPublicationService;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestPublicationStore;
import com.thunder.wildernessodysseyapi.quest.world.QuestWorldSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/** One lifecycle-owned composition of runtime, publication, receipts and authenticated projections. */
public final class QuestServerSession implements AutoCloseable {
    private final MinecraftServer server;
    private final UUID id = UUID.randomUUID();
    private final QuestIoDispatcher io = new QuestIoDispatcher();
    private final ConcurrentLinkedQueue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
    private final Set<UUID> blocked = new HashSet<>();
    private final Set<QuestRewardKey> earning = new HashSet<>();
    private final Map<UUID, Integer> earnCursor = new HashMap<>();
    private final Map<UUID, Long> nextInventory = new HashMap<>();
    private final QuestRequestGate requestGate = new QuestRequestGate();
    private QuestServerEvents.Reload candidate;
    private QuestAvailability availability;
    private QuestPublicationStore store;
    private QuestRewardLedger ledger;
    private QuestRuntime runtime;
    private QuestPublicationService publications;
    private QuestRewardService rewards;
    private QuestViewService views;
    private com.thunder.wildernessodysseyapi.quest.network.QuestWorkshopServer workshop;
    private CompletableFuture<QuestPublicationStore> opening;
    private volatile boolean closed;
    private String problem = "Quest storage is opening.";
    private int inventoryCursor;

    QuestServerSession(MinecraftServer server, QuestAvailability availability, QuestServerEvents.Reload candidate) {
        this.server = server; this.availability = availability; this.candidate = candidate;
    }
    void open() {
        opening = QuestPublicationStore.open(server.getWorldPath(LevelResource.ROOT), io, availability, id, boundary -> { }).toCompletableFuture();
        opening.whenComplete((opened, failure) -> execute(() -> {
            if (failure != null) { failed("Quest publication storage could not open; files are preserved.", failure); return; }
            store = opened;
            QuestRewardLedger.open(store).whenComplete((restored, ledgerFailure) -> execute(() -> {
                if (ledgerFailure != null) { failed("Quest reward receipts need review; claims are locked.", ledgerFailure); return; }
                ledger = restored;
                try { ready(); } catch (RuntimeException exception) { failed("Quest state could not reconcile; files are preserved.", exception); }
            }));
        }));
    }
    private void ready() {
        var world = QuestWorldSavedData.get(server); world.reconcileIdentity(store.metadata().worldId());
        runtime = new QuestRuntime(id, world.state().orElseThrow(), world::update, this::transitions);
        views = new QuestViewService(runtime, ledger, server.getPlayerList()::getPlayer);
        var delivery = new QuestRewardDelivery();
        rewards = new QuestRewardService(ledger, new QuestRewardService.Authority() {
            public boolean current(UUID player) {
                var actor = server.getPlayerList().getPlayer(player);
                return !closed && actor != null && runtime.isCurrent(actor) && actor.isAlive() && !blocked.contains(player)
                        && QuestPermissions.mayPerform(actor, QuestOperation.CLAIM);
            }
            public Object identity(UUID player) { return server.getPlayerList().getPlayer(player); }
            public boolean canDeliver(UUID player, com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition reward) {
                var actor = server.getPlayerList().getPlayer(player); return actor != null && delivery.canDeliver(actor, reward);
            }
            public QuestRewardLedger.DeliveryOutcome deliver(UUID player, com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition reward) {
                var actor = server.getPlayerList().getPlayer(player);
                return actor == null ? QuestRewardLedger.DeliveryOutcome.NOT_APPLIED : delivery.deliver(actor, reward);
            }
            public void execute(Runnable callback) { QuestServerSession.this.execute(callback); }
        });
        publications = new QuestPublicationService(store, new QuestPublicationService.Authority() {
            public Optional<QuestPublicationService.Request> inspect(UUID operator, String hash, String base) {
                var actor = server.getPlayerList().getPlayer(operator);
                if (closed || actor == null || !QuestPermissions.mayPerform(actor, QuestOperation.PUBLISH) || candidate == null
                        || !candidate.report().accepted() || !candidate.report().snapshot().orElseThrow().hash().equals(hash)) return Optional.empty();
                return Optional.of(new QuestPublicationService.Request(operator, candidate.report().snapshot().orElseThrow(), base,
                        candidate.availability().generation(), candidate.availability()));
            }
            public boolean recheck(QuestPublicationService.Request request) {
                return inspect(request.operator(), request.snapshot().hash(), request.baseHash()).filter(next -> next.registryGeneration() == request.registryGeneration()).isPresent();
            }
            public void execute(Runnable callback) { QuestServerSession.this.execute(callback); }
            public void committed(QuestPublicationStore.PublicationCommit commit) { activate(commit); }
        });
        problem = "";
        if (candidate != null) runtime.acceptCandidate(candidate.report());
        if (store.activeSnapshot().isPresent()) {
            activate(new QuestPublicationStore.PublicationCommit(store.activeSnapshot().orElseThrow(), store.revision(), store.metadata().worldId(), id));
        } else tryInitialPublication();
        var seed = store.activeSnapshot().or(() -> candidate == null ? Optional.empty() : candidate.report().snapshot())
                .map(QuestCampaignSnapshot::sources).orElse(List.of());
        workshop = new com.thunder.wildernessodysseyapi.quest.network.QuestWorkshopServer(this, store, seed);
    }
    private void tryInitialPublication() {
        var captured = candidate;
        if (closed || publications == null || captured == null || !captured.report().accepted() || store.activeSnapshot().isPresent()) return;
        publications.seed(captured.report().snapshot().orElseThrow(), captured.availability(), () -> !closed && QuestConfig.values().enabled()
                && candidate == captured && availability.generation() == captured.availability().generation()
                && ledger.receipts().isEmpty() && runtime.world().facts().isEmpty())
                .whenComplete((result, failure) -> execute(() -> {
                    if (failure != null) failed("Initial quest publication could not activate.", failure);
                    else if (!result.accepted() && candidate != captured) tryInitialPublication();
                }));
    }
    void candidate(QuestServerEvents.Reload reload) {
        if (closed || reload.availability().generation() < availability.generation()) return;
        candidate = reload; availability = reload.availability();
        if (runtime != null) { runtime.acceptCandidate(reload.report()); runtime.refreshAvailability(availability); refreshPlayers(); tryInitialPublication(); }
    }
    private void activate(QuestPublicationStore.PublicationCommit commit) {
        if (closed) return;
        runtime.activate(commit); runtime.refreshAvailability(availability); refreshPlayers();
    }
    void refreshPlayers() {
        if (runtime == null || !QuestConfig.values().enabled()) return;
        for (var player : server.getPlayerList().getPlayers()) login(player);
    }
    void observe(ServerPlayer player, QuestEvent.Evidence evidence) {
        observe(player, UUID.randomUUID(), evidence);
    }
    public void observe(ServerPlayer player, UUID occurrence, QuestEvent.Evidence evidence) {
        if (runtime == null || !QuestConfig.values().enabled() || !runtime.isCurrent(player) || blocked.contains(player.getUUID())) return;
        safe(player, () -> {
            runtime.onEvent(player, new QuestEvent(id, occurrence, player.getUUID(), player.level().getGameTime(), evidence));
            if (!(evidence instanceof QuestEvent.Possession) && runtime.interested(player))
                runtime.onEvent(player, new QuestEvent(id, UUID.randomUUID(), player.getUUID(), player.level().getGameTime(),
                        new QuestEvent.Possession(QuestObjectiveEvents.captureInventory(player))));
        });
    }
    void login(ServerPlayer player) {
        if (runtime == null || !runtime.isCurrent(player) || !QuestConfig.values().enabled()) return;
        safe(player, () -> {
            var progress = runtime.progress(player); var recovered = ledger.reconcile(progress);
            if (recovered != progress) runtime.replaceProgress(player, recovered);
            earnCursor.putIfAbsent(player.getUUID(), 0); flushEarned(player);
            QuestObjectiveEvents.seed(player); views.sendInitial(player);
            nextInventory.put(player.getUUID(), server.getTickCount() + Math.floorMod(player.getUUID().hashCode(), 20L));
        });
    }
    void forget(ServerPlayer player) {
        if (workshop != null) workshop.forget(player.getUUID());
        if (runtime != null) runtime.forget(player.getUUID());
        if (views != null) views.clear(player);
        blocked.remove(player.getUUID()); earnCursor.remove(player.getUUID()); nextInventory.remove(player.getUUID()); requestGate.clear(player.getUUID());
    }
    private void transitions(ServerPlayer player, QuestTransitionBatch batch) {
        if (!batch.earned().isEmpty()) flushEarned(player);
        views.sendDirty(player);
    }
    private void flushEarned(ServerPlayer player) {
        var earned = runtime.progress(player).earned();
        int cursor = Math.min(earnCursor.getOrDefault(player.getUUID(), 0), earned.size());
        int admitted = 0;
        while (cursor < earned.size() && admitted < 4 && earning.size() < 128) {
            int position = cursor; var value = earned.get(cursor);
            var key = new QuestRewardKey(store.metadata().worldId(), player.getUUID(), value.quest(), value.runNumber(), value.originalValue().id());
            if (ledger.receipts().containsKey(key) || earning.contains(key)) { cursor++; continue; }
            earning.add(key); admitted++; cursor++;
            ledger.recordEarned(key, value.originalValue(), value.definitionHash()).whenComplete((result, failure) -> execute(() -> {
                earning.remove(key);
                if (failure != null && server.getPlayerList().getPlayer(key.playerId()) != null) earnCursor.merge(key.playerId(), position, Math::min);
                else if (!result.accepted()) { blocked.add(key.playerId()); failed("A quest entitlement conflicts with its durable receipt; claims are locked for that player.", new IllegalStateException("Reward value mismatch")); }
                var actor = server.getPlayerList().getPlayer(key.playerId());
                if (actor != null && failure == null) safe(actor, () -> views.sendInitial(actor));
            }));
        }
        earnCursor.put(player.getUUID(), cursor);
    }
    void tick() {
        io.pump();
        for (int count = 0; count < 256; count++) { var callback = callbacks.poll(); if (callback == null) break; callback.run(); }
        if (workshop != null) workshop.tick();
        if (runtime == null || !QuestConfig.values().enabled()) return;
        rewards.pump(); views.pump();
        var players = server.getPlayerList().getPlayers(); if (players.isEmpty()) return;
        int budget = Math.min(players.size(), QuestConfig.values().inventoryPlayersPerTick());
        for (int count = 0; count < budget; count++) {
            var player = players.get(Math.floorMod(inventoryCursor++, players.size()));
            if (blocked.contains(player.getUUID()) || server.getTickCount() < nextInventory.getOrDefault(player.getUUID(), 0L)) continue;
            nextInventory.put(player.getUUID(), (long) server.getTickCount() + 20);
            safe(player, () -> { flushEarned(player); if (runtime.interested(player)) QuestObjectiveEvents.inventory(player); });
        }
    }
    public void execute(Runnable callback) { if (!closed) callbacks.add(() -> { if (!closed) callback.run(); }); }
    private void safe(ServerPlayer player, Runnable work) {
        if (blocked.contains(player.getUUID())) return;
        try { work.run(); } catch (RuntimeException exception) {
            blocked.add(player.getUUID()); ModConstants.LOGGER.error("Quest state for player {} was preserved and suspended", player.getUUID(), exception);
        }
    }
    private void failed(String message, Throwable failure) { problem = message; ModConstants.LOGGER.error(message, failure); }
    public String status() {
        if (!problem.isEmpty()) return problem;
        String active = runtime.activeSnapshot().map(QuestCampaignSnapshot::hash).orElse("none");
        String pending = candidate == null ? "none" : candidate.report().snapshot().map(QuestCampaignSnapshot::hash).orElse("invalid");
        return "Active: " + active + "; candidate: " + pending + "; I/O worker: " + io.lastWorker() + ". Quests are optional.";
    }
    public QuestValidationReport candidateReport() { return candidate == null ? new QuestValidationReport(Optional.empty(), List.of()) : candidate.report(); }
    public Optional<QuestRuntime> runtime() { return Optional.ofNullable(runtime); }
    public Optional<QuestPublicationService> publications() { return Optional.ofNullable(publications); }
    public Optional<QuestRewardLedger> ledger() { return Optional.ofNullable(ledger); }
    public QuestViewService views() { return views; }
    public UUID id() { return id; }
    public MinecraftServer server() { return server; }
    public long availabilityGeneration() { return availability.generation(); }
    public String ioWorker() { return io.lastWorker(); }
    public boolean closed() { return closed; }
    public QuestRequestGate requestGate() { return requestGate; }
    public Optional<com.thunder.wildernessodysseyapi.quest.network.QuestWorkshopServer> workshop() { return Optional.ofNullable(workshop); }
    public QuestValidationReport validateWorkshopDraft(List<com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument> sources) {
        if (!server.isSameThread()) throw new IllegalStateException("Draft validation requires the server thread.");
        return new com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec().decode(sources, availability);
    }
    public java.util.concurrent.CompletionStage<QuestRewardService.ClaimResult> claim(ServerPlayer player, ResourceLocation quest, long run, ResourceLocation reward, UUID request) {
        if (runtime == null || rewards == null || !runtime.isCurrent(player) || blocked.contains(player.getUUID()) || !QuestPermissions.mayPerform(player, QuestOperation.CLAIM))
            return CompletableFuture.completedFuture(QuestRewardService.ClaimResult.UNAVAILABLE);
        flushEarned(player);
        var key = new QuestRewardKey(store.metadata().worldId(), player.getUUID(), quest, run, reward);
        if (!ledger.receipts().containsKey(key) && runtime.progress(player).earned().stream().anyMatch(value -> value.quest().equals(quest)
                && value.runNumber() == run && value.originalValue().id().equals(reward))) return CompletableFuture.completedFuture(QuestRewardService.ClaimResult.PENDING);
        return rewards.claim(key, request);
    }
    @Override public void close() {
        closed = true;
        if (workshop != null) workshop.close();
        if (publications != null) publications.close();
        if (rewards != null) rewards.close();
        if (runtime != null) runtime.close();
        if (views != null) views.clear();
        callbacks.clear(); blocked.clear(); earnCursor.clear(); nextInventory.clear(); earning.clear(); requestGate.clear();
        var closing = opening.handle((opened, failure) -> opened).thenCompose(opened -> opened == null ? CompletableFuture.completedFuture(null) : opened.close()).toCompletableFuture();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!closing.isDone() && System.nanoTime() < deadline) {
            io.pump();
            try { closing.get(10, TimeUnit.MILLISECONDS); } catch (java.util.concurrent.TimeoutException ignored) { }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
            catch (java.util.concurrent.ExecutionException exception) { break; }
        }
        closing.whenComplete((ignored, failure) -> io.close());
        if (!closing.isDone()) ModConstants.LOGGER.warn("Quest shutdown has not acknowledged lease release; another session cannot acquire its write lease yet.");
    }
}
