package com.thunder.wildernessodysseyapi.quest.workshop;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Owner-thread revision admission. An accepted durable save may finish after the actor disconnects. */
public final class QuestWorkshopService implements AutoCloseable {
    public enum Status { ACK, CONFLICT, DENIED, BUSY, INVALID, FAILED }
    public record Result(Status status, long revision, String message) { }
    public interface Authority {
        boolean allowed(UUID actor, Object incarnation);
        void execute(Runnable callback);
    }
    @FunctionalInterface public interface Persistence {
        CompletionStage<QuestWorkshopDraft> save(long expectedRevision, QuestWorkshopDraft next);
    }
    private final Authority authority;
    private final Persistence persistence;
    private final Thread owner = Thread.currentThread();
    private QuestWorkshopDraft draft;
    private boolean saving, closed;
    public QuestWorkshopService(QuestWorkshopDraft draft, Persistence persistence, Authority authority) {
        this.draft = draft; this.persistence = persistence; this.authority = authority;
    }
    public QuestWorkshopDraft draft() { checkThread(); return draft; }
    public boolean saving() { checkThread(); return saving; }
    public CompletionStage<Result> edit(UUID actor, Object incarnation, long expectedRevision, QuestWorkshopEdit edit) {
        checkThread();
        if (closed || !authority.allowed(actor, incarnation)) return result(Status.DENIED, "Editing is no longer permitted. Local changes are retained.");
        if (expectedRevision != draft.revision()) return result(Status.CONFLICT, "Another operator saved a newer draft. Reload explicitly before editing further.");
        if (saving) return result(Status.BUSY, "A draft save is finishing. Your edit can be retried.");
        QuestWorkshopDraft next;
        try {
            if (draft.revision() == Long.MAX_VALUE) throw new IllegalArgumentException("Draft revision is exhausted.");
            next = draft.apply(edit).withRevision(draft.revision() + 1);
        } catch (RuntimeException exception) { return result(Status.INVALID, "The edit exceeds draft bounds or references invalid data."); }
        // Acceptance is on the owning server thread. Workers only receive the immutable accepted draft.
        saving = true; var completion = new CompletableFuture<Result>();
        try {
            persistence.save(expectedRevision, next).whenComplete((stored, failure) -> authority.execute(() -> {
                checkThread(); saving = false;
                if (failure == null) { draft = stored; completion.complete(new Result(Status.ACK, draft.revision(), "Draft saved.")); }
                else completion.complete(new Result(Status.FAILED, draft.revision(), "Draft save failed. Local changes and the last acknowledged draft are preserved."));
            }));
        } catch (RuntimeException exception) {
            saving = false; completion.complete(new Result(Status.FAILED, draft.revision(), "Draft save could not start. Local changes are retained."));
        }
        return completion;
    }
    private CompletionStage<Result> result(Status status, String message) { return CompletableFuture.completedFuture(new Result(status, draft.revision(), message)); }
    private void checkThread() { if (Thread.currentThread() != owner) throw new IllegalStateException("Workshop authority requires its owner thread."); }
    @Override public void close() { checkThread(); closed = true; }
}
