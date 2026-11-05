package com.thunder.aether.server;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import com.thunder.aether.server.api.ChatRequest;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.aether.server.security.AdmissionJournal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real gateway HTTP and mocked Ollama; does not claim hosted inference. */
class GatewayChatTest {
    @TempDir Path directory;
    private AetherServer gateway;
    private HttpServer runtime;
    private final HttpClient http=HttpClient.newHttpClient();
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final List<JsonObject> calls=new CopyOnWriteArrayList<>();
    private final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
    private volatile String mode="OK";
    private boolean paused;
    private static final String TOKEN="test-inference-token-00000000000000000001";
    private static final String MONITOR="test-monitoring-token-000000000000000001";
    private static final String ADMIN="test-administration-token-000000000000001";
    private JsonObject fixture() throws Exception {
        try(var stream=getClass().getResourceAsStream("/aether-chat-v1.json")) {
            return JsonHttp.object(stream.readAllBytes());
        }
    }
    private String body() throws Exception { return fixture().getAsJsonObject("request").toString(); }
    @AfterEach void stop() {
        release.countDown();
        if(gateway!=null)gateway.close();
        if(runtime!=null)runtime.stop(0);
        workers.shutdownNow(); http.shutdownNow();
    }
    private void start(boolean admitted,int timeout) throws Exception {
        runtime=HttpServer.create(new InetSocketAddress("127.0.0.1",0),16);
        runtime.setExecutor(workers);
        runtime.createContext("/api/tags",exchange -> {
            byte[] bytes="{\"models\":[{\"name\":\"qwen3.5:4b\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        runtime.createContext("/api/chat",exchange -> {
            try {
                JsonObject input=JsonHttp.object(exchange.getRequestBody().readAllBytes()); calls.add(input);
                entered.countDown();
                if(mode.equals("BLOCK"))release.await(5,TimeUnit.SECONDS);
                if(mode.equals("OFFLINE")) {exchange.sendResponseHeaders(503,-1);return;}
                String system=input.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
                String text=system.contains("strict factual response verifier")
                        ? "{\"approved\":"+!mode.equals("REJECT")+"}"
                        : mode.equals("MALFORMED") ? "not-json" : "{\"response\":\"Hello explorer.\"}";
                if (!system.contains("strict factual response verifier") && mode.equals("EXTRA")) text="{\"response\":\"Hello\",\"action\":\"op\"}";
                if (!system.contains("strict factual response verifier") && mode.equals("EMPTY")) text="{\"response\":\" \"}";
                if (system.contains("strict factual response verifier") && mode.equals("VERIFIER_TYPE")) text="{\"approved\":\"true\"}";
                var message=new JsonObject(); message.addProperty("content",text);
                var output=new JsonObject(); output.add("message",message);
                byte[] bytes=output.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes);
            } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally {exchange.close();}
        });
        runtime.start();
        Path config=directory.resolve("gateway.yml");
        Files.writeString(config,"""
                server:
                  bind: 127.0.0.1
                  port: 0
                ollama:
                  url: http://127.0.0.1:%d
                  model: qwen3.5:4b
                  timeout_seconds: %d
                  context_tokens: 2048
                  max_output_tokens: 256
                limits:
                  max_concurrent_generations: 1
                  max_queue_size: 0
                security:
                  minecraft_server_id: official-server
                activation:
                  hosting_verified: %s
                  capacity_verified: %s
                  inference_enabled: true
                  verified_model: qwen3.5:4b
                """.formatted(runtime.getAddress().getPort(),timeout,admitted,admitted));
        var settings=ServerConfig.load(config,Map.of("AETHER_INFERENCE_TOKEN",TOKEN,"AETHER_MONITORING_TOKEN",MONITOR,"AETHER_ADMINISTRATION_TOKEN",ADMIN));
        if (paused) {
            try(var journal=new AdmissionJournal(settings.security().stateDirectory())) {
                var command=new JsonObject(); command.addProperty("requestId",UUID.randomUUID().toString());
                command.addProperty("expectedRevision",0); command.addProperty("expiresAt",Instant.now().plusSeconds(60).toString());
                command.addProperty("paused",true); command.addProperty("actor","test-operator"); journal.apply(command);
            }
        }
        gateway=new AetherServer(settings);
        gateway.start(); gateway.refreshHealth();
    }
    private HttpRequest request(String path,String token,String body) {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+gateway.port()+path)).timeout(Duration.ofSeconds(5));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return body==null ? builder.GET().build() : builder.header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private HttpResponse<String> send(String path,String token,String body) throws Exception {
        return http.send(request(path,token,body),HttpResponse.BodyHandlers.ofString());
    }
    @Test void sharedFixturesAreStrictAndDoNotAdmitGameState() throws Exception {
        JsonObject valid=fixture().getAsJsonObject("request");
        assertEquals("discord",ChatRequest.parse(valid).surface());
        for(var patch:fixture().getAsJsonArray("invalidPatches")) {
            JsonObject invalid=valid.deepCopy();
            patch.getAsJsonObject().entrySet().forEach(entry -> invalid.add(entry.getKey(),entry.getValue()));
            assertThrows(IllegalArgumentException.class,() -> ChatRequest.parse(invalid));
        }
    }
    @Test void authenticatedStatelessChatUsesCanonJsonNoThinkingAndMatchingResponse() throws Exception {
        start(true,3);
        var response=send("/v1/aether/chat",TOKEN,body());
        assertEquals(200,response.statusCode(),response.body());
        assertEquals(fixture().get("response"),JsonHttp.object(response.body().getBytes(StandardCharsets.UTF_8)));
        assertEquals(2,calls.size());
        for(var call:calls) {
            assertFalse(call.get("think").getAsBoolean()); assertEquals("json",call.get("format").getAsString());
            assertEquals(2048,call.getAsJsonObject("options").get("num_ctx").getAsInt());
            assertTrue(call.toString().contains("knowledge_boundaries"));
            assertFalse(call.toString().contains("principalId")); assertFalse(call.toString().contains(TOKEN));
        }
        assertEquals(2,calls.getFirst().getAsJsonArray("messages").size());
        var other=fixture().getAsJsonObject("request"); other.addProperty("scopeId","c".repeat(64));
        other.addProperty("surface","minecraft"); other.addProperty("message","Another independent turn");
        assertEquals(200,send("/v1/aether/chat",TOKEN,other.toString()).statusCode());
        assertEquals(2,calls.get(2).getAsJsonArray("messages").size());
        assertFalse(calls.get(2).toString().contains("Hello explorer"));
    }
    @Test void chatAndStatusEnforceScopeIdentityAndCommissioning() throws Exception {
        start(false,3);
        assertEquals(401,send("/v1/aether/chat",null,body()).statusCode());
        assertEquals(403,send("/v1/aether/chat",MONITOR,body()).statusCode());
        assertEquals(403,send("/v1/aether/chat",ADMIN,body()).statusCode());
        var browser=HttpRequest.newBuilder(request("/v1/aether/chat",TOKEN,body()),(name,value) -> true)
                .header("Origin","https://browser.example").build();
        assertEquals(401,http.send(browser,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401,send("/v1/aether/chat/status",null,null).statusCode());
        assertEquals(403,send("/v1/aether/chat/status",MONITOR,null).statusCode());
        assertEquals(503,send("/v1/aether/chat",TOKEN,body()).statusCode());
        var status=send("/v1/aether/chat/status",TOKEN,null);
        assertEquals(200,status.statusCode()); assertTrue(status.body().contains("\"available\":false"));
        var wrong=fixture().getAsJsonObject("request"); wrong.addProperty("serverId","other-server");
        assertEquals(403,send("/v1/aether/chat",TOKEN,wrong.toString()).statusCode());
        wrong=fixture().getAsJsonObject("request");wrong.addProperty("world","minecraft:overworld");
        assertEquals(400,send("/v1/aether/chat",TOKEN,wrong.toString()).statusCode());
        assertTrue(calls.isEmpty());
    }
    @Test void sharedQueueRejectsOverloadAndNativeRouteRemainsProtected() throws Exception {
        start(true,3);mode="BLOCK";
        var pending=http.sendAsync(request("/v1/aether/chat",TOKEN,body()),HttpResponse.BodyHandlers.ofString());
        assertTrue(entered.await(2,TimeUnit.SECONDS));
        assertEquals(503,send("/v1/aether/chat",TOKEN,body()).statusCode());
        String nativeBody="{\"requestId\":\"00000000-0000-4000-8000-000000000001\",\"serverId\":\"official-server\","
                +"\"playerId\":\"00000000-0000-4000-8000-000000000002\",\"playerName\":\"Player\",\"world\":\"minecraft:overworld\",\"message\":\"Hi\"}";
        assertEquals(503,send("/v1/aether/generate",TOKEN,nativeBody).statusCode());
        assertEquals(200,send("/v1/aether/chat/status",TOKEN,null).statusCode());
        release.countDown(); assertEquals(200,pending.get(4,TimeUnit.SECONDS).statusCode());
    }
    @Test void timeoutAndModelFailuresNeverBecomeSuccess() throws Exception {
        start(true,1);
        for(String failure:List.of("MALFORMED","OFFLINE","REJECT","EXTRA","EMPTY","VERIFIER_TYPE")) {
            mode=failure; var response=send("/v1/aether/chat",TOKEN,body());
            assertEquals(503,response.statusCode()); assertFalse(response.body().contains("Hello explorer"));
        }
        mode="BLOCK";
        assertEquals(408,send("/v1/aether/chat",TOKEN,body()).statusCode());
    }
    @Test void durableNativePauseRejectsChatWithoutChangingReadinessContracts() throws Exception {
        paused=true;start(true,3);
        var response=send("/v1/aether/chat",TOKEN,body());
        assertEquals(503,response.statusCode()); assertTrue(response.body().contains("INFERENCE_PAUSED"));
        assertTrue(send("/v1/aether/chat/status",TOKEN,null).body().contains("\"available\":false"));
        assertTrue(calls.isEmpty());
    }
}
