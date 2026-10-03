package com.thunder.wildernessodysseyapi.quest.workshop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/** Durable world/ledger identity, provisioned before a reward can be reserved or delivered. */
public record QuestStoreMetadata(UUID worldId, UUID ledgerId) {
    public byte[] encode() {
        var body = body();
        body.addProperty("checksum", QuestDefinitionCodec.hash(new Gson().toJson(body).getBytes(StandardCharsets.UTF_8)));
        return new Gson().toJson(body).getBytes(StandardCharsets.UTF_8);
    }

    public static QuestStoreMetadata decode(byte[] bytes) throws IOException {
        if (bytes.length > 4096) throw new IOException("Quest metadata exceeds its size limit.");
        var json = new QuestDefinitionCodec().readBounded(new ByteArrayInputStream(bytes));
        try {
            if (!json.keySet().equals(Set.of("formatVersion", "worldId", "ledgerId", "checksum"))
                    || !json.get("formatVersion").getAsJsonPrimitive().isNumber()
                    || json.get("formatVersion").getAsBigDecimal().intValueExact() != 1) throw new IllegalArgumentException("Unsupported format.");
            var metadata = new QuestStoreMetadata(UUID.fromString(json.get("worldId").getAsString()), UUID.fromString(json.get("ledgerId").getAsString()));
            String expected = QuestDefinitionCodec.hash(new Gson().toJson(metadata.body()).getBytes(StandardCharsets.UTF_8));
            if (!expected.equals(json.get("checksum").getAsString())) throw new IllegalArgumentException("Checksum mismatch.");
            return metadata;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
            throw new IOException("Quest metadata is corrupt or uses an unsupported format.", exception);
        }
    }

    private JsonObject body() {
        var body = new JsonObject();
        body.addProperty("formatVersion", 1);
        body.addProperty("worldId", worldId.toString());
        body.addProperty("ledgerId", ledgerId.toString());
        return body;
    }
}
