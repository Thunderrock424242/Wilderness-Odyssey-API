package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgressStore;
import com.thunder.wildernessodysseyapi.quest.progress.QuestProgressMigration;
import com.thunder.wildernessodysseyapi.quest.validation.QuestValidationReport;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestPublicationStore;
import com.thunder.wildernessodysseyapi.quest.world.QuestWorldState;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** One server-session owner. Candidate reloads never activate definitions or produce gameplay effects. */
public final class QuestRuntime implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final UUID session;
    private final Consumer<QuestWorldState> worldWriter;
    private final BiConsumer<ServerPlayer, QuestTransitionBatch> transitions;
    private final QuestPlayerProgressStore persistence = new QuestPlayerProgressStore();
    private final Map<UUID, QuestPlayerProgress> players = new HashMap<>();
    private final Map<UUID, Boolean> inventoryInterest = new HashMap<>();
    private QuestWorldState world;
    private QuestCampaignSnapshot active;
    private QuestCampaignSnapshot available;
    private QuestObjectiveEngine engine;
    private QuestValidationReport candidate = new QuestValidationReport(Optional.empty(), List.of());
    private long publicationRevision;
    private boolean closed;

    public QuestRuntime(UUID session, QuestWorldState world, Consumer<QuestWorldState> worldWriter,
                        BiConsumer<ServerPlayer, QuestTransitionBatch> transitions) {
        this.session = session;
        this.world = world;
        this.worldWriter = worldWriter;
        this.transitions = transitions;
    }

    public UUID session() { return session; }
    public QuestWorldState world() { requireOwner(); return world; }
    public long publicationRevision() { requireOwner(); return publicationRevision; }
    public Optional<QuestCampaignSnapshot> activeSnapshot() { requireOwner(); return Optional.ofNullable(active); }
    /** Current validated availability for projection/tracking; activeSnapshot retains durable publication identity. */
    public Optional<QuestCampaignSnapshot> availableSnapshot() { requireOwner(); return Optional.ofNullable(available); }
    public QuestValidationReport candidateReport() { requireOwner(); return candidate; }

    public void acceptCandidate(QuestValidationReport report) {
        requireOwner();
        if (!closed) candidate = report;
    }

    /** A reload can suspend unavailable content while retaining the durable active identity and all progress. */
    public void refreshAvailability(com.thunder.wildernessodysseyapi.quest.validation.QuestContentLookup lookup) {
        requireOwner();
        if (active == null || closed) return;
        var available = new com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec().decode(active.sources(), lookup);
        this.available = available.snapshot().orElse(null);
        engine = available.snapshot().map(QuestObjectiveEngine::compile).orElse(null);
        inventoryInterest.clear();
    }

    public void activate(QuestPublicationStore.PublicationCommit commit) {
        requireOwner();
        if (closed || !session.equals(commit.session()) || !world.worldId().equals(commit.worldId())
                || commit.revision() <= publicationRevision) return;
        if (active != null && !new QuestProgressMigration().check(active, commit.snapshot()).compatible()) {
            throw new IllegalStateException("Publication requires an explicit progress migration.");
        }
        active = commit.snapshot();
        available = active;
        engine = QuestObjectiveEngine.compile(active);
        publicationRevision = commit.revision();
        inventoryInterest.clear();
    }

    public QuestPlayerProgress progress(ServerPlayer player) {
        requireCurrent(player);
        return players.computeIfAbsent(player.getUUID(), ignored -> persistence.load(player));
    }

    /** Used by tracking and durable entitlement reconciliation; callers cannot change the player owner. */
    public void replaceProgress(ServerPlayer player, QuestPlayerProgress replacement) {
        requireCurrent(player);
        if (!player.getUUID().equals(replacement.playerId())) throw new IllegalArgumentException("Quest owner mismatch.");
        persistence.save(player, replacement);
        players.put(player.getUUID(), replacement);
        inventoryInterest.remove(player.getUUID());
    }

    public void onEvent(ServerPlayer player, QuestEvent event) {
        requireCurrent(player);
        if (engine == null || !session.equals(event.serverSession()) || !player.getUUID().equals(event.actor())) return;
        var previous = progress(player);
        var batch = engine.apply(previous, world, event);
        if (!batch.world().equals(world)) {
            world = batch.world();
            worldWriter.accept(world);
        }
        players.put(player.getUUID(), batch.player());
        if (batch.player().revision() != previous.revision()) {
            persistence.save(player, batch.player());
            inventoryInterest.remove(player.getUUID());
        }
        transitions.accept(player, batch);
    }

    public boolean interested(ServerPlayer player) {
        return engine != null && inventoryInterest.computeIfAbsent(player.getUUID(), ignored -> engine.hasInventoryInterest(progress(player)));
    }

    public void forget(UUID player) { requireOwner(); players.remove(player); inventoryInterest.remove(player); }

    public boolean isCurrent(ServerPlayer player) {
        requireOwner();
        return !closed && player.getServer() != null && player.getServer().isSameThread()
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private void requireCurrent(ServerPlayer player) {
        requireOwner();
        if (!isCurrent(player)) throw new IllegalStateException("Quest player is outside this active server session.");
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Quest runtime requires its server thread.");
    }

    @Override
    public void close() {
        requireOwner();
        closed = true;
        players.clear();
        inventoryInterest.clear();
        active = null;
        available = null;
        engine = null;
        candidate = new QuestValidationReport(Optional.empty(), List.of());
    }
}
