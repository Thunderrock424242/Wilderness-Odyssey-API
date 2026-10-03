package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.quest.workshop.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Optimistic authoring/history state persists independently of screen widgets and resize. */
public final class QuestWorkshopModel {
    public record Submission(UUID request, long revision, QuestWorkshopEdit edit) { }
    private record History(QuestWorkshopEdit forward, QuestWorkshopEdit inverse, int bytes) { }
    private final ArrayDeque<QuestWorkshopEdit> pending = new ArrayDeque<>();
    private final ArrayDeque<History> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private QuestWorkshopDraft draft;
    private Submission inFlight;
    private int pendingBytes, historyBytes;
    private boolean conflict, paused;
    public double panX = 20, panY = 20, zoom = 1;
    public ResourceLocation chapter, selected;
    public String search = "", message = "Draft loaded.";
    public long changedAt;
    public List<String> findings = List.of();

    public QuestWorkshopModel(QuestWorkshopDraft draft) { this.draft = draft; }
    public QuestWorkshopDraft draft() { return draft; }
    public boolean dirty() { return !pending.isEmpty(); }
    public boolean conflicted() { return conflict; }
    public boolean matchesSave(UUID request) { return inFlight != null && inFlight.request().equals(request); }
    public boolean canUndo() { return !conflict && !undo.isEmpty(); }
    public boolean canRedo() { return !conflict && !redo.isEmpty(); }
    public void edit(QuestWorkshopEdit edit) {
        var next = draft.apply(edit); var inverse = QuestWorkshopEdit.difference(next, draft);
        int bytes = edit.encode().length + inverse.encode().length;
        enqueue(edit, next);
        for (var entry : redo) historyBytes -= entry.bytes(); redo.clear();
        undo.addLast(new History(edit, inverse, bytes)); historyBytes += bytes;
        while (undo.size() > 64 || historyBytes > 4 * 1024 * 1024) historyBytes -= undo.removeFirst().bytes();
    }
    private void enqueue(QuestWorkshopEdit edit, QuestWorkshopDraft next) {
        if (conflict) throw new IllegalArgumentException("Resolve the revision conflict before editing.");
        int bytes = edit.encode().length;
        if (pending.size() >= 64 || (long) pendingBytes + bytes > QuestWorkshopDraft.MAX_BYTES) throw new IllegalArgumentException("Too many unsaved edits. Save before continuing.");
        pending.addLast(edit); pendingBytes += bytes; draft = next; paused = false; changedAt = System.nanoTime(); message = "Unsaved changes.";
    }
    public void undo() {
        if (!canUndo()) return; var entry = undo.getLast(); enqueue(entry.inverse(), draft.apply(entry.inverse())); undo.removeLast(); redo.addLast(entry);
    }
    public void redo() {
        if (!canRedo()) return; var entry = redo.getLast(); enqueue(entry.forward(), draft.apply(entry.forward())); redo.removeLast(); undo.addLast(entry);
    }
    public Optional<Submission> nextSave() {
        if (inFlight != null || pending.isEmpty() || conflict || paused) return Optional.empty();
        inFlight = new Submission(UUID.randomUUID(), draft.revision(), pending.getFirst()); message = "Saving draft…"; return Optional.of(inFlight);
    }
    public void ack(UUID request, long revision) {
        if (inFlight == null || !inFlight.request().equals(request)) return;
        if (revision != inFlight.revision() + 1) { conflict(revision); return; }
        pendingBytes -= pending.removeFirst().encode().length; draft = draft.withRevision(revision); inFlight = null;
        message = dirty() ? "Saving remaining changes…" : "Draft saved.";
    }
    public void busy(UUID request) { if (inFlight != null && inFlight.request().equals(request)) { inFlight = null; changedAt = System.nanoTime(); message = "Waiting for another save…"; } }
    public void failure(UUID request, String message) {
        if (inFlight != null && inFlight.request().equals(request)) { inFlight = null; paused = true; this.message = message; }
    }
    public void conflict(long revision) { conflict = true; paused = true; inFlight = null; message = "Draft changed on the server (revision " + revision + "). Local changes retained; reload explicitly."; }
    public void retry() { if (!conflict) { paused = false; changedAt = 0; } }
}
