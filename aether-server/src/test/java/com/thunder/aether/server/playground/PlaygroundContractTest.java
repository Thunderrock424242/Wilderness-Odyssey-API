package com.thunder.aether.server.playground;

import com.google.gson.*;
import com.thunder.aether.server.api.JsonHttp;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlaygroundContractTest {
    static JsonObject training() {
        JsonObject r = new JsonObject();
        r.addProperty("schemaVersion", "1.0"); r.addProperty("serverId", "test-server");
        r.addProperty("requestId", UUID.randomUUID().toString()); r.addProperty("actor", "staff-1");
        r.addProperty("baseModelId", "tiny:local"); r.addProperty("presetId", "lora-smoke");
        String data = "[{\"id\":\"example-1\",\"revision\":\"r1\",\"prompt\":\"Hello\",\"target\":\"Welcome\"}]";
        JsonObject d = new JsonObject(); d.addProperty("json", data); d.addProperty("sha256", PlaygroundContract.hash(data));
        r.add("dataset", d); r.addProperty("reason", "Reviewed smoke examples");
        r.addProperty("createdAt", Instant.now().toString()); r.addProperty("expiresAt", Instant.now().plusSeconds(240).toString());
        r.addProperty("acknowledgeTraining", true); return r;
    }
    @Test void literalHashAndOrderedStrictDataset() {
        JsonObject r = training();
        var parsed = PlaygroundContract.training(r, "test-server");
        assertEquals(1, parsed.items().size());
        r.getAsJsonObject("dataset").addProperty("json", r.getAsJsonObject("dataset").get("json").getAsString()+" ");
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.training(r, "test-server"));
    }
    @Test void rejectsSubstitutionExtraMembersDuplicatesAndByteOverflow() {
        JsonObject r = training();
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.training(r, "other"));
        r.addProperty("command", "shell");
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.training(r, "test-server"));
        r.remove("command");
        String sample = r.getAsJsonObject("dataset").get("json").getAsString();
        String duplicate = sample.substring(0,sample.length()-1)+","+sample.substring(1);
        r.getAsJsonObject("dataset").addProperty("json", duplicate);
        r.getAsJsonObject("dataset").addProperty("sha256", PlaygroundContract.hash(duplicate));
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.training(r, "test-server"));
        String huge = "界".repeat(100000);
        r.getAsJsonObject("dataset").addProperty("json", huge);
        r.getAsJsonObject("dataset").addProperty("sha256", PlaygroundContract.hash(huge));
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.training(r, "test-server"));
    }
    @Test void strictUtf8AndExpiry() {
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.object(new byte[]{'{','"','x','"',':','"',(byte)0xff,'"','}'}));
        JsonObject r = training(); r.addProperty("expiresAt", Instant.now().minusSeconds(1).toString());
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.fresh(r));
        r.addProperty("expiresAt", Instant.now().plusSeconds(301).toString());
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.fresh(r));
    }
    @Test void canonicalFingerprintIgnoresEnvelopeMemberOrderButPreservesDatasetBytes() {
        JsonObject r = training(), reordered = new JsonObject();
        r.keySet().stream().sorted(java.util.Comparator.reverseOrder()).forEach(k -> reordered.add(k,r.get(k)));
        assertEquals(PlaygroundContract.fingerprint(r), PlaygroundContract.fingerprint(reordered));
        assertThrows(IllegalArgumentException.class, () -> PlaygroundContract.object("{\"x\":1,\"x\":2}".getBytes()));
    }
    @Test void datasetMustBeOneCompleteArrayNotObjectMemberInjection() {
        JsonObject r=training();String injected=r.getAsJsonObject("dataset").get("json").getAsString()+",\"extra\":true";
        r.getAsJsonObject("dataset").addProperty("json",injected);
        r.getAsJsonObject("dataset").addProperty("sha256",PlaygroundContract.hash(injected));
        assertThrows(IllegalArgumentException.class,()->PlaygroundContract.training(r,"test-server"));
    }
}
