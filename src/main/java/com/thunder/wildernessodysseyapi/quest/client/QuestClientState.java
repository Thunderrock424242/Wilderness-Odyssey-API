package com.thunder.wildernessodysseyapi.quest.client;

import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.network.QuestProgressDeltaPayload;
import com.thunder.wildernessodysseyapi.quest.network.QuestViewPayload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Read-only cache has no Minecraft client dependency; dedicated-server registration can safely reference it. */
public final class QuestClientState {
    public enum ApplyResult { APPLIED, IGNORED, RESYNC }
    public static final QuestClientState INSTANCE = new QuestClientState();
    private UUID session;
    private String hash = "";
    private long publicationRevision;
    private long revision;
    private JsonObject view;
    private QuestViewPayload pending;
    private ByteArrayOutputStream assembly;
    private int nextChunk;

    public void accept(QuestViewPayload chunk) {
        if (session != null && session.equals(chunk.session()) && (chunk.publicationRevision() < publicationRevision
                || chunk.publicationRevision() == publicationRevision && chunk.playerRevision() < revision)) return;
        if (chunk.index() == 0) { pending = chunk; assembly = new ByteArrayOutputStream(); nextChunk = 0; }
        if (pending == null || !sameTransfer(pending, chunk) || chunk.index() != nextChunk) { pending = null; assembly = null; return; }
        assembly.writeBytes(chunk.bytes()); nextChunk++;
        if (nextChunk != chunk.chunks()) return;
        try {
            var decoded = decode(assembly.toByteArray());
            session = chunk.session(); hash = chunk.hash(); publicationRevision = chunk.publicationRevision(); revision = chunk.playerRevision(); view = decoded;
        } catch (IOException | RuntimeException ignored) { /* Preserve the previous complete view; a bounded resync can recover. */ }
        pending = null; assembly = null;
    }

    public ApplyResult accept(QuestProgressDeltaPayload delta) {
        if (session == null || !session.equals(delta.session()) || !hash.equals(delta.hash()) || publicationRevision != delta.publicationRevision()) return ApplyResult.RESYNC;
        if (delta.playerRevision() <= revision) return ApplyResult.IGNORED;
        if (delta.baseRevision() != revision) return ApplyResult.RESYNC;
        try { view = decode(delta.bytes()); revision = delta.playerRevision(); return ApplyResult.APPLIED; }
        catch (IOException | RuntimeException ignored) { return ApplyResult.RESYNC; }
    }

    private static JsonObject decode(byte[] bytes) throws IOException {
        var json = new QuestDefinitionCodec().readProjection(bytes);
        if (!json.keySet().equals(java.util.Set.of("quests", "earned", "tracking")) || !json.get("quests").isJsonArray()
                || json.getAsJsonArray("quests").size() > 2000 || !json.get("earned").isJsonArray()
                || json.getAsJsonArray("earned").size() > 2000 || json.get("tracking").getAsString().length() > 256) throw new IOException("Invalid quest projection.");
        String tracking = string(json, "tracking", 256); if (!tracking.isEmpty()) id(tracking);
        var questIds = new java.util.HashSet<String>();
        for (var entry : json.getAsJsonArray("quests")) {
            var quest = object(entry, java.util.Set.of("id", "title", "description", "icon", "completed", "run", "objectives"));
            String questId = string(quest, "id", 256); id(questId);
            if (!questIds.add(questId)) throw new IOException("Duplicate quest projection.");
            string(quest, "title", 256); string(quest, "description", 4096); id(string(quest, "icon", 256));
            if (!quest.get("completed").isJsonPrimitive() || !quest.get("completed").getAsJsonPrimitive().isBoolean()) throw new IOException("Invalid completion flag.");
            number(quest, "run", 0, 100_000);
            if (!quest.get("objectives").isJsonArray() || quest.getAsJsonArray("objectives").size() > 32) throw new IOException("Too many projected objectives.");
            var objectiveIds = new java.util.HashSet<String>();
            for (var value : quest.getAsJsonArray("objectives")) {
                var objective = object(value, java.util.Set.of("id", "goal", "count"));
                String objectiveId = string(objective, "id", 256); id(objectiveId);
                if (!objectiveIds.add(objectiveId)) throw new IOException("Duplicate projected objective.");
                number(objective, "goal", 1, Long.MAX_VALUE); number(objective, "count", 0, Long.MAX_VALUE);
            }
        }
        var rewards = new java.util.HashSet<String>();
        for (var entry : json.getAsJsonArray("earned")) {
            var value = object(entry, java.util.Set.of("quest", "run", "reward", "status"));
            String quest = string(value, "quest", 256); id(quest); long run = number(value, "run", 0, 100_000);
            if (!value.get("reward").isJsonObject()) throw new IOException("Invalid projected reward.");
            var reward = new QuestDefinitionCodec().reward(value.getAsJsonObject("reward"));
            if (!rewards.add(quest + "/" + run + "/" + reward.id()) || !java.util.Set.of("PENDING", "EARNED", "RESERVED", "DELIVERED", "NEEDS_REVIEW")
                    .contains(string(value, "status", 32))) throw new IOException("Invalid projected reward identity/state.");
        }
        return json;
    }

    private static JsonObject object(com.google.gson.JsonElement value, java.util.Set<String> fields) throws IOException {
        if (!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(fields)) throw new IOException("Invalid projection fields.");
        return value.getAsJsonObject();
    }
    private static String string(JsonObject object, String name, int max) throws IOException {
        var value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length() > max) throw new IOException("Invalid projection text.");
        return value.getAsString();
    }
    private static void id(String value) throws IOException {
        if (!value.contains(":") || net.minecraft.resources.ResourceLocation.tryParse(value) == null) throw new IOException("Invalid projected ID.");
    }
    private static long number(JsonObject object, String name, long min, long max) throws IOException {
        var value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IOException("Invalid projected number.");
        try { long result = value.getAsBigDecimal().longValueExact(); if (result < min || result > max) throw new ArithmeticException(); return result; }
        catch (ArithmeticException exception) { throw new IOException("Projected number is out of bounds.", exception); }
    }

    private static boolean sameTransfer(QuestViewPayload a, QuestViewPayload b) {
        return a.session().equals(b.session()) && a.transfer().equals(b.transfer()) && a.hash().equals(b.hash()) && a.publicationRevision() == b.publicationRevision()
                && a.playerRevision() == b.playerRevision() && a.totalBytes() == b.totalBytes() && a.chunks() == b.chunks();
    }
    public Optional<JsonObject> view() { return view == null ? Optional.empty() : Optional.of(view.deepCopy()); }
    public String hash() { return hash; }
    public long revision() { return revision; }
    public UUID session() { return session; }
    public void clear() { session = null; hash = ""; publicationRevision = 0; revision = 0; view = null; pending = null; assembly = null; nextChunk = 0; }
}
