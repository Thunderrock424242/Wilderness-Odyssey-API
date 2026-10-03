package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.quest.network.*;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Pure snapshot/cache authority contains no Minecraft client classes; native presentation installs a listener. */
public final class QuestWorkshopClientState {
    public static final QuestWorkshopClientState INSTANCE = new QuestWorkshopClientState();
    private final QuestWorkshopTransfers incoming = new QuestWorkshopTransfers(), outgoing = new QuestWorkshopTransfers();
    private QuestWorkshopModel model;
    private UUID server, editor;
    private long tick;
    private boolean forceReload, validateAfterSave, denied, closing, released;
    private UUID reloadRequest;
    private long retryReloadAt;
    private final Consumer<QuestWorkshopPayload> sender;
    public QuestWorkshopClientState() { this(PacketDistributor::sendToServer); }
    public QuestWorkshopClientState(Consumer<QuestWorkshopPayload> sender) { this.sender = Objects.requireNonNull(sender); }
    public Consumer<QuestWorkshopPayload.Kind> listener = ignored -> { };
    public Optional<QuestWorkshopModel> model() { return Optional.ofNullable(model); }

    public void accept(QuestWorkshopPayload frame) {
        if (frame.kind() != QuestWorkshopPayload.Kind.OPEN && (server == null || !server.equals(frame.server()) || !editor.equals(frame.editor()))) return;
        try {
            var assembled = incoming.accept(frame.server(), frame, tick); if (assembled.isEmpty()) return; byte[] bytes = assembled.get();
            if (frame.kind() == QuestWorkshopPayload.Kind.OPEN || frame.kind() == QuestWorkshopPayload.Kind.SNAPSHOT) {
                var draft = QuestWorkshopDraft.decode(bytes);
                if (draft.revision() != frame.revision()) throw new IllegalArgumentException("Snapshot revision mismatch.");
                boolean sameWorld = frame.server().equals(server) && model != null && model.draft().id().equals(draft.id());
                outgoing.clear(); server = frame.server(); editor = frame.editor(); denied = false; closing = false; released = false; reloadRequest = null;
                if (sameWorld && model.dirty() && !forceReload) model.conflict(draft.revision());
                else { model = new QuestWorkshopModel(draft); forceReload = false; }
                listener.accept(frame.kind()); return;
            }
            if (model == null) return;
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.length() > 20_000) throw new IllegalArgumentException("Oversized Workshop status.");
            switch (frame.kind()) {
                case ACK -> model.ack(frame.request(), frame.revision());
                case BUSY -> {
                    if (model.matchesSave(frame.request())) outgoing.clear(); model.busy(frame.request());
                    if (frame.request().equals(reloadRequest)) { reloadRequest = null; retryReloadAt = tick + 20; }
                }
                case ERROR -> {
                    boolean saving = model.matchesSave(frame.request()); if (saving) outgoing.clear(); model.failure(frame.request(), text);
                    if (saving && closing) release();
                    if (frame.request().equals(reloadRequest)) { reloadRequest = null; forceReload = false; model.message = text; }
                }
                case CONFLICT, STALE -> { if (frame.revision() > model.draft().revision() || frame.kind() == QuestWorkshopPayload.Kind.CONFLICT) { outgoing.clear(); model.conflict(frame.revision()); } }
                case DENIED -> { denied = true; outgoing.clear(); model.conflict(frame.revision()); model.message = "Workshop permission was revoked. Local work retained; no further edits can be saved."; }
                case VALIDATION -> { var lines = text.split("\n", -1); if (lines.length > 33) throw new IllegalArgumentException("Too many validation findings."); model.findings = List.of(lines); model.message = lines[0]; }
                default -> { }
            }
            listener.accept(frame.kind());
        } catch (java.io.IOException | RuntimeException exception) {
            if (model != null) model.message = "Workshop response was incomplete or invalid. Previous draft retained.";
        }
    }
    public void tick() {
        tick++; incoming.expire(tick);
        if (model == null || denied || released) return;
        if (closing && (!model.dirty() || model.conflicted())) { release(); return; }
        if (forceReload) { if (reloadRequest == null && tick >= retryReloadAt) reloadRequest = command(QuestWorkshopPayload.Kind.SNAPSHOT); return; }
        if (System.nanoTime() - model.changedAt > 750_000_000L) model.nextSave().ifPresent(save -> {
            if (!outgoing.queue(server, server, editor, save.request(), QuestWorkshopPayload.Kind.EDIT, save.revision(), save.edit().encode()))
                model.failure(save.request(), "Edit transfer is too large. Local work retained.");
        });
        outgoing.pump((peer, payload) -> sender.accept(payload));
        if (validateAfterSave && !model.dirty() && !model.conflicted()) { validateAfterSave = false; command(QuestWorkshopPayload.Kind.VALIDATE); }
    }
    private UUID command(QuestWorkshopPayload.Kind kind) {
        if (model == null || denied) return null;
        var request = UUID.randomUUID(); sender.accept(new QuestWorkshopPayload(server, editor, request, kind, model.draft().revision(), 0, 0, new byte[0])); return request;
    }
    public void validate() { if (model == null) return; model.retry(); validateAfterSave = true; }
    public void reload() { forceReload = true; outgoing.clear(); reloadRequest = command(QuestWorkshopPayload.Kind.SNAPSHOT); }
    public void releaseWhenSaved() { closing = true; validateAfterSave = false; if (model != null) model.retry(); }
    private void release() { command(QuestWorkshopPayload.Kind.CLOSE); released = true; closing = false; outgoing.clear(); }
    public void clear() { incoming.clear(); outgoing.clear(); model = null; server = null; editor = null; tick = 0; forceReload = false; validateAfterSave = false; denied = false; closing = false; released = false; reloadRequest = null; retryReloadAt = 0; }
}
