package com.thunder.aether.server.playground;

import com.google.gson.*;
import com.thunder.aether.server.config.*;
import com.thunder.aether.server.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MainAdmissionCapacityTest {
    @TempDir Path directory;
    @Test void definitiveNativeJournalExhaustionFinishesAsFailedImmediately() throws Exception {
        Path nativePath=directory.resolve("native");Files.createDirectories(nativePath);
        Instant now=Instant.now();
        try(var out=Files.newBufferedWriter(nativePath.resolve("admission.jsonl"))) {
            for(int i=0;i<4096;i++) {
                JsonObject command=new JsonObject();command.addProperty("requestId",UUID.randomUUID().toString());
                command.addProperty("expectedRevision",i);command.addProperty("expiresAt",now.plusSeconds(240).toString());
                command.addProperty("paused",true);command.addProperty("actor","capacity-test");
                JsonObject entry=new JsonObject();entry.add("command",command);entry.addProperty("appliedAt",now.toString());entry.addProperty("revision",i+1);
                out.write(entry+"\n");
            }
        }
        var security=GatewaySecurityConfig.load(Map.of("minecraft_server_id","test-server","service_write_enabled",true,
                "access_issuer","https://team.cloudflareaccess.com","access_audience","gateway","service_write_client_id","service"),Map.of(),nativePath);
        var config=new ServerConfig("127.0.0.1",0,3,"http://127.0.0.1:9","aether:8b",3,256,1,1,60,65536,4,false,false,false,"",security,ActivationConfig.disabled());
        try(var journal=new AdmissionJournal(nativePath);var store=new PlaygroundStore(directory.resolve("peer"),"test-server")) {
            var adapter=new MainServerAdapter(config,store,journal);JsonObject command=new JsonObject();
            command.addProperty("operation_id","capacity-op");command.addProperty("server_id","test-server");command.addProperty("action","ai.pause");
            JsonObject params=new JsonObject();params.addProperty("paused",false);command.add("parameters",params);
            command.addProperty("expected_revision","4096");command.addProperty("created_at",now.toString());command.addProperty("expires_at",now.plusSeconds(120).toString());
            command.addProperty("actor","staff");command.addProperty("reason","Reviewed maintenance change");
            assertEquals("failed",adapter.submit(command).get("state").getAsString());
            assertEquals("failed",adapter.operation("capacity-op").get("state").getAsString());
            assertEquals(4096L,journal.snapshot().get("revision"));
        }
    }
}
