package com.thunder.wildernessodysseyapi.quest.workshop;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.progress.QuestProgressMigration;
import com.thunder.wildernessodysseyapi.quest.validation.QuestContentLookup;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server authorization -> immutable preparation -> recheck -> durable commit -> game-thread activation. */
public final class QuestPublicationService implements AutoCloseable {
    public interface Authority {
        Optional<Request> inspect(UUID operator, String candidateHash, String expectedBaseHash);
        boolean recheck(Request request);
        void execute(Runnable callback);
        void committed(QuestPublicationStore.PublicationCommit commit);
    }

    public record Request(UUID operator, QuestCampaignSnapshot snapshot, String baseHash, long registryGeneration,
                          QuestContentLookup availability) { }
    public record PublishResult(boolean accepted, String message, Optional<QuestPublicationStore.PublicationCommit> commit) { }

    private final QuestPublicationStore store;
    private final Authority authority;
    private final AtomicBoolean publishing = new AtomicBoolean();
    private volatile boolean closed;
    private volatile CompletableFuture<PublishResult> pending;

    public QuestPublicationService(QuestPublicationStore store, Authority authority) {
        this.store = store;
        this.authority = authority;
    }

    public CompletionStage<PublishResult> publish(ServerPlayer operator, String candidateHash, String expectedBaseHash) {
        return publish(operator.getUUID(), candidateHash, expectedBaseHash);
    }

    /** The UUID overload is for a trusted server authority/test adapter, never a client-selected player ID. */
    public CompletionStage<PublishResult> publish(UUID operator, String candidateHash, String expectedBaseHash) {
        if (closed || candidateHash == null || !candidateHash.matches("[0-9a-f]{64}") || expectedBaseHash == null
                || !expectedBaseHash.isEmpty() && !expectedBaseHash.matches("[0-9a-f]{64}")) {
            return denied("Publication is unavailable or the requested version is invalid.");
        }
        var inspected = authority.inspect(operator, candidateHash, expectedBaseHash);
        if (inspected.isEmpty()) return denied("You cannot publish this candidate. Validate it and check your permissions.");
        var request = inspected.get();
        if (!request.operator().equals(operator) || !request.snapshot().hash().equals(candidateHash)
                || !request.baseHash().equals(expectedBaseHash) || request.registryGeneration() != request.snapshot().registryGeneration()
                || request.registryGeneration() != request.availability().generation()
                || !currentHash().equals(expectedBaseHash)) return denied("The candidate or active publication changed. Validate again before publishing.");
        var previous = store.activeSnapshot();
        if (previous.isPresent() && !new QuestProgressMigration().check(previous.get(), request.snapshot()).compatible()) {
            return denied("This change needs an explicit progress migration. The current publication remains active.");
        }
        return prepareAndCommit(request, () -> authority.recheck(request));
    }

    /** Trusted initial provisioning uses the same owner-thread acceptance boundary as operator publication. */
    public CompletionStage<PublishResult> seed(QuestCampaignSnapshot snapshot, QuestContentLookup availability,
                                               java.util.function.BooleanSupplier stillEligible) {
        if (closed || store.activeSnapshot().isPresent() || store.revision() != 0 || !stillEligible.getAsBoolean())
            return denied("Initial publication is no longer eligible.");
        var request = new Request(null, snapshot, "", availability.generation(), availability);
        return prepareAndCommit(request, () -> stillEligible.getAsBoolean() && store.activeSnapshot().isEmpty() && store.revision() == 0);
    }

    private CompletionStage<PublishResult> prepareAndCommit(Request request, java.util.function.BooleanSupplier recheck) {
        if (!publishing.compareAndSet(false, true)) return denied("Another publication is being prepared. Wait for its result.");
        var result = new CompletableFuture<PublishResult>();
        pending = result;
        store.prepare(request.snapshot(), request.availability()).whenComplete((stored, failure) -> handoff(result, () -> {
            if (closed) { finish(result, false, "Publication stopped with this server session.", null); return; }
            if (failure != null) { failed(result, failure); return; }
            if (!recheck.getAsBoolean() || !currentHash().equals(request.baseHash())
                    || stored.snapshot().registryGeneration() != request.registryGeneration()) {
                finish(result, false, "Permission, content availability or publication changed. Validate again before publishing.", null);
                return;
            }
            if (store.revision() == Long.MAX_VALUE) { finish(result, false, "Publication history has reached its supported limit.", null); return; }
            // This is commit acceptance. Later permission loss does not undo an authorized durable write.
            var token = new QuestPublicationStore.CommitToken(request.baseHash(), stored.snapshot().hash(),
                    store.metadata().worldId(), store.session(), store.revision() + 1);
            store.activate(stored, token).whenComplete((commit, commitFailure) -> handoff(result, () -> {
                if (closed) { finish(result, false, "Publication stopped with this server; a durable commit will restore on restart.", null); return; }
                if (commitFailure != null) { failed(result, commitFailure); return; }
                authority.committed(commit);
                finish(result, true, "Quest publication activated. Existing completion and earned rewards are preserved.", commit);
            }));
        }));
        return result;
    }

    private String currentHash() {
        return store.activeSnapshot().map(QuestCampaignSnapshot::hash).orElse("");
    }

    private void handoff(CompletableFuture<PublishResult> result, Runnable callback) {
        try {
            authority.execute(() -> {
                try {
                    callback.run();
                } catch (RuntimeException failure) {
                    failed(result, failure);
                }
            });
        } catch (RuntimeException failure) {
            failed(result, failure);
        }
    }

    private void failed(CompletableFuture<PublishResult> result, Throwable failure) {
        ModConstants.LOGGER.error("[Quests] Publication could not finish; durable state and prior snapshots are preserved.", failure);
        finish(result, false, "Publication could not finish. Check the server diagnostics; any durable commit is preserved.", null);
    }

    private void finish(CompletableFuture<PublishResult> result, boolean accepted, String message, QuestPublicationStore.PublicationCommit commit) {
        publishing.set(false);
        result.complete(new PublishResult(accepted, message, Optional.ofNullable(commit)));
    }

    private static CompletionStage<PublishResult> denied(String message) {
        return CompletableFuture.completedFuture(new PublishResult(false, message, Optional.empty()));
    }

    @Override
    public void close() {
        closed = true;
        var result = pending;
        if (result != null) finish(result, false, "Publication stopped with this server; durable commits remain available on restart.", null);
    }
}
