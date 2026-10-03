package com.thunder.wildernessodysseyapi.quest.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.quest.command.*;
import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestServerSession;
import com.thunder.wildernessodysseyapi.quest.workshop.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private Workshop sessions live entirely inside the current quest server owner. */
public final class QuestWorkshopServer implements AutoCloseable {
    private record Editor(UUID token, ServerPlayer actor) { }
    private final QuestServerSession owner;
    private final Map<UUID, Editor> editors = new HashMap<>();
    private final QuestWorkshopTransfers transfers = new QuestWorkshopTransfers();
    private QuestWorkshopService service;
    private String problem = "Workshop draft is loading. Try again shortly.";
    private boolean closed;

    public QuestWorkshopServer(QuestServerSession owner, QuestPublicationStore publication, List<QuestSourceDocument> seed) {
        this.owner = owner;
        QuestWorkshopStore.open(publication, seed).whenComplete((store, failure) -> owner.execute(() -> {
            if (closed) return;
            if (failure != null) {
                problem = "Workshop draft needs server review. Existing draft files are preserved.";
                ModConstants.LOGGER.error("Quest Workshop draft could not restore; gameplay ownership is unchanged", failure); return;
            }
            service = new QuestWorkshopService(store.draft(), store::save, new QuestWorkshopService.Authority() {
                public boolean allowed(UUID actor, Object incarnation) {
                    var player = owner.server().getPlayerList().getPlayer(actor);
                    return !closed && player == incarnation && authorized(player);
                }
                public void execute(Runnable callback) { owner.execute(callback); }
            });
            problem = "";
        }));
    }
    public boolean ready() { return !closed && service != null; }
    private boolean authorized(ServerPlayer player) {
        return player != null && !closed && !owner.closed() && QuestConfig.values().enabled()
                && owner.server().getPlayerList().getPlayer(player.getUUID()) == player && QuestPermissions.mayPerform(player, QuestOperation.VALIDATE);
    }
    public void open(ServerPlayer player) {
        if (!authorized(player)) { player.sendSystemMessage(Component.literal("You do not have permission to open the Quest Workshop.")); return; }
        if (!ready()) { player.sendSystemMessage(Component.literal(problem)); return; }
        if (service.saving()) { player.sendSystemMessage(Component.literal("A Workshop save is finishing. Open the editor again shortly.")); return; }
        if (!editors.containsKey(player.getUUID()) && editors.size() >= 8) { player.sendSystemMessage(Component.literal("The Workshop is busy with eight operators. Try again when an editor session closes.")); return; }
        forget(player.getUUID()); var editor = new Editor(UUID.randomUUID(), player); editors.put(player.getUUID(), editor);
        snapshot(player, editor, UUID.randomUUID(), QuestWorkshopPayload.Kind.OPEN);
    }
    private void snapshot(ServerPlayer player, Editor editor, UUID request, QuestWorkshopPayload.Kind kind) {
        if (!transfers.queue(player.getUUID(), owner.id(), editor.token(), request, kind, service.draft().revision(), service.draft().encode()))
            status(player, editor, request, QuestWorkshopPayload.Kind.BUSY, "Workshop transfer capacity is busy. Retry shortly.");
    }
    public void handle(ServerPlayer player, QuestWorkshopPayload frame) {
        var editor = editors.get(player.getUUID());
        if (editor == null) return;
        var admission = QuestWorkshopAdmission.check(frame, owner.id(), editor.token(), editor.actor(), player, authorized(player),
                () -> owner.requestGate().admit(player.getUUID(), frame.request(), owner.server().getTickCount(), false, QuestConfig.values()));
        switch (admission) {
            case IGNORE -> { return; }
            case REVOKE -> { revoke(player, editor); return; }
            case CLOSE -> { forget(player.getUUID()); return; }
            case INVALID -> { status(player, editor, frame.request(), QuestWorkshopPayload.Kind.ERROR, "Unexpected Workshop close body."); return; }
            case BUSY -> { status(player, editor, frame.request(), QuestWorkshopPayload.Kind.BUSY, "Workshop requests are arriving too quickly. Retry shortly."); return; }
            default -> { }
        }
        try {
            var bytes = transfers.accept(player.getUUID(), frame, owner.server().getTickCount());
            if (bytes.isEmpty()) return;
            switch (frame.kind()) {
                case SNAPSHOT -> {
                    if (bytes.get().length != 0) throw new IllegalArgumentException("Unexpected reload body.");
                    if (service.saving()) status(player, editor, frame.request(), QuestWorkshopPayload.Kind.BUSY, "A save is finishing. Reload will retry shortly.");
                    else snapshot(player, editor, frame.request(), QuestWorkshopPayload.Kind.SNAPSHOT);
                }
                case VALIDATE -> {
                    if (bytes.get().length != 0 || frame.revision() != service.draft().revision()) {
                        status(player, editor, frame.request(), QuestWorkshopPayload.Kind.CONFLICT, "Save or reload the current draft before validation."); return;
                    }
                    var report = owner.validateWorkshopDraft(service.draft().sources());
                    var lines = new ArrayList<String>(); lines.add(report.accepted() ? "Draft is valid. Live quests remain unchanged." : "Draft has validation findings. Live quests remain unchanged.");
                    report.findings().stream().limit(32).forEach(finding -> {
                        String line = finding.severity() + " " + finding.contentId() + " " + finding.fieldPath() + ": " + finding.message();
                        lines.add(line.substring(0, Math.min(512, line.length())));
                    });
                    transfers.queue(player.getUUID(), owner.id(), editor.token(), frame.request(), QuestWorkshopPayload.Kind.VALIDATION,
                            service.draft().revision(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
                }
                case EDIT -> service.edit(player.getUUID(), player, frame.revision(), QuestWorkshopEdit.decode(bytes.get())).whenComplete((result, failure) -> {
                    if (!authorized(player) || editors.get(player.getUUID()) != editor) return;
                    if (failure != null) { status(player, editor, frame.request(), QuestWorkshopPayload.Kind.ERROR, "Draft save failed. Local work is retained."); return; }
                    var kind = switch (result.status()) {
                        case ACK -> QuestWorkshopPayload.Kind.ACK; case CONFLICT -> QuestWorkshopPayload.Kind.CONFLICT;
                        case BUSY -> QuestWorkshopPayload.Kind.BUSY; case DENIED -> QuestWorkshopPayload.Kind.DENIED;
                        case INVALID, FAILED -> QuestWorkshopPayload.Kind.ERROR;
                    };
                    status(player, editor, frame.request(), kind, result.message());
                    if (result.status() == QuestWorkshopService.Status.ACK) for (var entry : List.copyOf(editors.entrySet())) {
                        if (!entry.getKey().equals(player.getUUID()) && authorized(entry.getValue().actor()))
                            status(entry.getValue().actor(), entry.getValue(), UUID.randomUUID(), QuestWorkshopPayload.Kind.STALE, "Another operator saved a newer draft.");
                    }
                });
                default -> { }
            }
        } catch (java.io.IOException | IllegalArgumentException exception) {
            transfers.forget(player.getUUID()); status(player, editor, frame.request(), QuestWorkshopPayload.Kind.ERROR, "The edit or transfer was invalid. Local changes are retained.");
        }
    }
    private void status(ServerPlayer player, Editor editor, UUID request, QuestWorkshopPayload.Kind kind, String message) {
        if (!authorized(player)) return; byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        PacketDistributor.sendToPlayer(player, new QuestWorkshopPayload(owner.id(), editor.token(), request, kind, service.draft().revision(), 0, bytes.length, bytes));
    }
    private void revoke(ServerPlayer player, Editor editor) {
        if (owner.server().getPlayerList().getPlayer(player.getUUID()) == player)
            PacketDistributor.sendToPlayer(player, new QuestWorkshopPayload(owner.id(), editor.token(), UUID.randomUUID(), QuestWorkshopPayload.Kind.DENIED, 0, 0, 0, new byte[0]));
        forget(player.getUUID());
    }
    public void tick() {
        transfers.expire(owner.server().getTickCount());
        for (var entry : List.copyOf(editors.entrySet())) if (!authorized(entry.getValue().actor())) {
            // A revoke message contains no draft body. Stop private buffers immediately.
            var editor = entry.getValue(); revoke(editor.actor(), editor);
        }
        transfers.pump((playerId, frame) -> { var editor = editors.get(playerId); if (editor != null && authorized(editor.actor())) PacketDistributor.sendToPlayer(editor.actor(), frame); });
    }
    public void forget(UUID player) { editors.remove(player); transfers.forget(player); }
    @Override public void close() { closed = true; editors.clear(); transfers.clear(); if (service != null) service.close(); }
}
