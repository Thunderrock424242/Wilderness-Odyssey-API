package com.thunder.wildernessodysseyapi.quest.workshop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** One world-bound draft; corruption disables authoring without replacing publication or reward state. */
public final class QuestWorkshopStore {
    private final QuestPublicationStore store;
    private volatile QuestWorkshopDraft draft;
    private boolean saving;
    private QuestWorkshopStore(QuestPublicationStore store, QuestWorkshopDraft draft) { this.store = store; this.draft = draft; }

    public static CompletionStage<QuestWorkshopStore> open(QuestPublicationStore store, List<QuestSourceDocument> seed) {
        var captured = List.copyOf(seed);
        return store.readWorkshopDraft().thenCompose(bytes -> {
            try {
                if (bytes.isPresent()) return CompletableFuture.completedFuture(new QuestWorkshopStore(store, decode(bytes.get(), store.metadata().worldId())));
                var draft = QuestWorkshopDraft.create(captured);
                return store.writeWorkshopDraft(encode(draft, store.metadata().worldId())).thenApply(ignored -> new QuestWorkshopStore(store, draft));
            } catch (IOException | RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
        });
    }
    public QuestWorkshopDraft draft() { return draft; }
    public synchronized CompletionStage<QuestWorkshopDraft> save(long expectedRevision, QuestWorkshopDraft next) {
        if (saving || !draft.id().equals(next.id()) || draft.revision() == Long.MAX_VALUE
                || expectedRevision != draft.revision() || next.revision() != expectedRevision + 1)
            return CompletableFuture.failedFuture(new IOException("Stale or overlapping Workshop save."));
        byte[] contents;
        try { contents = encode(next, store.metadata().worldId()); }
        catch (IOException | RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
        saving = true;
        try { return store.writeWorkshopDraft(contents).handle((ignored, failure) -> {
            synchronized (this) {
                saving = false;
                if (failure != null) throw new java.util.concurrent.CompletionException(failure);
                draft = next; return next;
            }
        }); } catch (RuntimeException exception) { saving = false; return CompletableFuture.failedFuture(exception); }
    }
    private static byte[] encode(QuestWorkshopDraft draft, UUID world) throws IOException {
        var root = new JsonObject(); root.addProperty("formatVersion", 1); root.addProperty("worldId", world.toString());
        root.add("draft", JsonParser.parseString(new String(draft.encode(), StandardCharsets.UTF_8)));
        root.addProperty("checksum", QuestDefinitionCodec.hash(QuestWorkshopDraft.bytes(root)));
        byte[] contents = QuestWorkshopDraft.bytes(root);
        // Apply the exact restart decoder before replacing the last usable file. The envelope adds
        // depth and nodes beyond the network draft; acknowledgement promises this file can restore.
        decode(contents, world); return contents;
    }
    private static QuestWorkshopDraft decode(byte[] bytes, UUID world) throws IOException {
        try {
            var root = new QuestDefinitionCodec().readProjection(bytes); QuestWorkshopDraft.fields(root, "formatVersion", "worldId", "draft", "checksum");
            if (QuestWorkshopDraft.number(root, "formatVersion") != 1 || !world.equals(UUID.fromString(root.get("worldId").getAsString())))
                throw new IllegalArgumentException("Unsupported or foreign Workshop store.");
            String checksum = root.get("checksum").getAsString(); root.remove("checksum");
            if (!QuestDefinitionCodec.hash(QuestWorkshopDraft.bytes(root)).equals(checksum)) throw new IllegalArgumentException("Workshop store checksum mismatch.");
            return QuestWorkshopDraft.decode(QuestWorkshopDraft.bytes(root.getAsJsonObject("draft")));
        } catch (RuntimeException exception) { throw new IOException("Workshop file needs review; its contents are preserved.", exception); }
    }
}
