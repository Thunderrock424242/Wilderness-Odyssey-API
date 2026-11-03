package com.thunder.aether.server.playground;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CandidateArtifactsTest {
    @TempDir Path directory;
    @Test void missingOrFabricatedWeightsNeverComplete() throws Exception {
        var request=PlaygroundContractTest.training();
        JsonObject job=new JsonObject(); job.addProperty("baseModelId","tiny:local");job.addProperty("presetId","lora-smoke");
        job.add("datasetHash",request.getAsJsonObject("dataset").get("sha256"));
        assertThrows(Exception.class,()->CandidateArtifacts.verify(directory,job,1024*1024));
        Files.writeString(directory.resolve("adapter_model.safetensors"),"fake weights");
        Files.writeString(directory.resolve("metadata.json"),"{}");
        assertThrows(Exception.class,()->CandidateArtifacts.verify(directory,job,1024*1024));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="AETHER_TRAINING_SMOKE_PATH",matches=".+")
    void realOptimizerArtifactPassesServerValidationAndCorruptionFails() throws Exception {
        Path root=Path.of(System.getenv("AETHER_TRAINING_SMOKE_PATH"));
        JsonObject meta=com.thunder.aether.server.api.JsonHttp.object(Files.readAllBytes(root.resolve("evidence.json")));
        JsonObject job=new JsonObject();
        for(String key:java.util.List.of("baseModelId","presetId","datasetHash","approvedBaseSha256"))job.add(key,meta.get(key));
        assertTrue(CandidateArtifacts.verify(root.resolve("candidate"),job,1024*1024).get("weightsChanged").getAsBoolean());
        assertTrue(meta.get("candidateReloaded").getAsBoolean());assertTrue(meta.get("baseWeightsUnchanged").getAsBoolean());
        try(var store=new PlaygroundStore(directory.resolve("real-job"),"test-server")) {
            JsonObject request=PlaygroundContractTest.training();request.add("baseModelId",meta.get("baseModelId"));
            request.getAsJsonObject("dataset").addProperty("json",Files.readString(root.resolve("dataset.json")));
            request.getAsJsonObject("dataset").add("sha256",meta.get("datasetHash"));
            JsonObject accepted=store.submit(PlaygroundContract.training(request,"test-server"),8,"pinned-smoke-runtime");
            store.claim();CandidateArtifacts.verify(root.resolve("candidate"),accepted,1024*1024);
            JsonObject completed=store.finish(accepted.get("requestId").getAsString(),"candidate-smoke-verified");
            assertEquals("completed",completed.get("state").getAsString());
            assertEquals(completed,store.find(accepted.get("requestId").getAsString()));
            Files.writeString(root.resolve("peer-completed.json"),completed.toString());
        }
        Path copy=directory.resolve("corrupt");Files.createDirectory(copy);
        try(var paths=Files.list(root.resolve("candidate"))) { for(Path p:paths.toList())if(Files.isRegularFile(p))Files.copy(p,copy.resolve(p.getFileName())); }
        Files.write(copy.resolve("adapter_model.safetensors"),new byte[]{1},StandardOpenOption.APPEND);
        assertThrows(Exception.class,()->CandidateArtifacts.verify(copy,job,1024*1024));
    }
}
