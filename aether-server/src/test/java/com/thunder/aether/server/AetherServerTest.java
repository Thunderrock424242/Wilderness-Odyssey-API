package com.thunder.aether.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.aether.server.security.ApiSecurity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP integration tests against mocked Ollama, including the executable service JAR. */
class AetherServerTest {
    @TempDir Path temporary;
    private HttpServer mock;
    private AetherServer server;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ExecutorService mockWorkers=Executors.newFixedThreadPool(4);
    private final AtomicInteger chatCalls=new AtomicInteger();
    private final AtomicReference<String> model=new AtomicReference<>("aether-custom:8b");
    private final AtomicReference<String> mode=new AtomicReference<>("OK");
    private final AtomicInteger delayMillis=new AtomicInteger();
    private final CountDownLatch entered=new CountDownLatch(1);
    private final CountDownLatch release=new CountDownLatch(1);
    private final List<JsonObject> modelRequests=new CopyOnWriteArrayList<>();

    @BeforeEach void startOllama() throws Exception {
        mock=HttpServer.create(new InetSocketAddress("127.0.0.1",0),32);
        mock.setExecutor(mockWorkers);
        mock.createContext("/api/tags",exchange -> {
            byte[] body=("{\"models\":[{\"name\":\""+model.get()+"\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);
            try(var out=exchange.getResponseBody()){out.write(body);}
            exchange.close();
        });
        mock.createContext("/api/chat",exchange -> {
            chatCalls.incrementAndGet();
            JsonObject request=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            modelRequests.add(request);
            String system=request.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
            boolean verifier=system.contains("strict factual response verifier");
            entered.countDown();
            try {
                if(mode.get().equals("BLOCK") && !verifier){release.await(5,TimeUnit.SECONDS);}
                if(delayMillis.get()>0){Thread.sleep(delayMillis.get());}
                if(mode.get().equals("OFFLINE")){
                    exchange.sendResponseHeaders(503,-1);exchange.close();return;
                }
                JsonObject outer=new JsonObject(), message=new JsonObject();
                message.addProperty("content",verifier
                        ? "{\"approved\":"+!mode.get().equals("REJECT")+"}"
                        : "{\"speaker\":\"Aether\",\"display\":\"Recovered response.\",\"speech\":\"Recovered response.\",\"emotion\":\"calm\"}");
                outer.add("message",message);
                byte[] body=outer.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,body.length);
                try(var out=exchange.getResponseBody()){out.write(body);}
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            finally{exchange.close();}
        });
        mock.start();
    }

    @AfterEach void close() {
        release.countDown();
        if(server!=null){server.close();}
        if(mock!=null){mock.stop(0);}
        mockWorkers.shutdownNow();
        http.shutdownNow();
    }

    @Test void generationPreservesContextAndUsesServerOwnedPromptAndVerification() throws Exception {
        start(2,4,60,3);
        String id=UUID.randomUUID().toString();
        var response=post(request(id,"server-a"),"server-key-a");
        assertEquals(200,response.statusCode());
        JsonObject body=json(response);
        assertTrue(body.get("success").getAsBoolean());
        assertEquals(id,body.get("requestId").getAsString());
        assertEquals("Recovered response.",body.get("response").getAsString());
        assertEquals("aether-custom:8b",body.get("model").getAsString());
        assertEquals(2,chatCalls.get());
        assertTrue(modelRequests.getFirst().toString().contains("zone:meteor_site"));
        assertTrue(modelRequests.getFirst().toString().contains("knowledge_boundaries"));
        assertFalse(modelRequests.getFirst().toString().contains("server-key-a"));
        assertTrue(modelRequests.getLast().toString().contains("strict factual response verifier"));
    }

    @Test void healthIsPublicButReadyAndGenerationRequireValidSingleBearerCredential() throws Exception {
        start(1,1,60,3);
        assertEquals(200,get("/health",null).statusCode());
        assertEquals(401,get("/ready",null).statusCode());
        assertEquals(401,post(request(),"wrong-key").statusCode());
        assertEquals(401,post(request(),null).statusCode());
        assertEquals(0,chatCalls.get());
        assertEquals(200,get("/ready","server-key-a").statusCode());
    }

    @Test void missingConfiguredModelIsNotReadyWhileServiceRemainsHealthy() throws Exception {
        model.set("other-model:latest");start(1,1,60,3);server.refreshHealth();
        var ready=get("/ready","server-key-a");
        assertEquals(503,ready.statusCode());
        assertFalse(json(ready).get("ready").getAsBoolean());
        assertEquals("UP",json(get("/health",null)).get("status").getAsString());
    }

    @Test void unavailableOllamaProducesCategoricalFailure() throws Exception {
        start(1,1,60,3);mock.stop(0);mock=null;server.refreshHealth();
        assertEquals("DOWN",json(get("/health",null)).get("ollama").getAsString());
        var response=post(request(),"server-key-a");
        assertEquals(503,response.statusCode());
        assertFalse(json(response).get("success").getAsBoolean());
        assertEquals("MODEL_UNAVAILABLE",json(response).get("error").getAsString());
    }

    @Test void rejectedVerifierNeverExposesTheDraftAsSuccess() throws Exception {
        mode.set("REJECT");start(1,1,60,3);
        var response=post(request(),"server-key-a");
        assertEquals(503,response.statusCode());
        assertEquals("UNVERIFIED_RESPONSE",json(response).get("error").getAsString());
        assertFalse(response.body().contains("Recovered response."));
    }

    @Test void draftAndVerificationShareOneDeadline() throws Exception {
        delayMillis.set(650);start(1,1,60,1);
        var response=assertTimeoutPreemptively(Duration.ofSeconds(2),() -> post(request(),"server-key-a"));
        assertEquals(408,response.statusCode());
        assertEquals("TIMEOUT",json(response).get("error").getAsString());
        assertEquals(2,chatCalls.get());
    }

    @Test void invalidTypesIdsAgentsHistoryRolesAndDeepJsonAreRejectedBeforeInference() throws Exception {
        start(1,1,60,3);
        for(String field:List.of("requestId","playerId","speaker")){
            JsonObject value=JsonParser.parseString(request()).getAsJsonObject();
            value.addProperty(field,"not-valid");
            assertEquals(400,post(value.toString(),"server-key-a").statusCode());
        }
        JsonObject value=JsonParser.parseString(request()).getAsJsonObject();
        value.addProperty("message",4);
        assertEquals(400,post(value.toString(),"server-key-a").statusCode());
        assertEquals(400,post("{\"unexpected\":"+"[".repeat(40)+"0"+"]".repeat(40)+"}","server-key-a").statusCode());
        assertEquals(413,post("x".repeat(70000),"server-key-a").statusCode());
        assertEquals(0,chatCalls.get());
    }

    @Test void credentialsBoundRateLimitsAcrossSpoofedServerIdsAndIsolateOtherServers() throws Exception {
        start(1,1,1,3);
        assertEquals(200,post(request(UUID.randomUUID().toString(),"server-a"),"server-key-a").statusCode());
        assertEquals(429,post(request(UUID.randomUUID().toString(),"different-id"),"server-key-a").statusCode());
        assertEquals(200,post(request(UUID.randomUUID().toString(),"server-b"),"server-key-b").statusCode());
        assertEquals(4,chatCalls.get());
    }

    @Test void fullQueueRejectsPromptlyAndHealthRemainsResponsiveDuringInference() throws Exception {
        mode.set("BLOCK");start(1,1,60,5);
        var first=postAsync(request(),"server-key-a");
        assertTrue(entered.await(2,TimeUnit.SECONDS));
        var second=postAsync(request(),"server-key-b");
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(System.nanoTime()<until && json(get("/health",null)).get("queuedGenerations").getAsInt()!=1){Thread.sleep(10);}
        assertEquals(1,json(get("/health",null)).get("queuedGenerations").getAsInt());
        assertTimeoutPreemptively(Duration.ofMillis(500),() -> assertEquals(200,get("/health",null).statusCode()));
        var overload=post(request(),"server-key-a");
        assertEquals(503,overload.statusCode());
        assertEquals("OVERLOADED",json(overload).get("error").getAsString());
        release.countDown();
        assertEquals(200,first.get(3,TimeUnit.SECONDS).statusCode());
        assertEquals(200,second.get(3,TimeUnit.SECONDS).statusCode());
    }

    @Test void simultaneousPlayersReceiveTheirOwnRequestIds() throws Exception {
        start(2,4,60,3);
        String firstId=UUID.randomUUID().toString(),secondId=UUID.randomUUID().toString();
        var first=postAsync(request(firstId,"server-a"),"server-key-a");
        var second=postAsync(request(secondId,"server-b"),"server-key-b");
        assertEquals(firstId,json(first.get(3,TimeUnit.SECONDS)).get("requestId").getAsString());
        assertEquals(secondId,json(second.get(3,TimeUnit.SECONDS)).get("requestId").getAsString());
    }

    @Test void safeLogsRedactKnownCredentialsEvenWhenContentLoggingIsEnabled() {
        ApiSecurity security=new ApiSecurity(List.of("server-key-a","server-key-b"),60);
        assertEquals("hello <redacted> <redacted> bye",security.safeLog("hello server-key-a\nserver-key-b\rbye"));
    }

    @Test void executableJarStartsAndHandlesHealthGenerationAndOutage() throws Exception {
        Path jar=Path.of(System.getProperty("aether.jar"));
        assertTrue(Files.isRegularFile(jar));
        int port;
        try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        Path configuration=temporary.resolve("server.yml");
        Files.writeString(configuration,"""
                server:
                  bind: 127.0.0.1
                  port: %d
                ollama:
                  url: http://127.0.0.1:%d
                  model: aether-custom:8b
                  timeout_seconds: 2
                security:
                  api_keys: []
                """.formatted(port,mock.getAddress().getPort()));
        Path log=temporary.resolve("service.log");
        ProcessBuilder process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-jar",jar.toString(),"--config",configuration.toString());
        process.environment().put("AETHER_API_KEYS","server-key-a");
        process.redirectErrorStream(true).redirectOutput(log.toFile());
        Process running=process.start();
        try {
            URI health=URI.create("http://127.0.0.1:"+port+"/health");
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            boolean up=false;
            while(System.nanoTime()<until && running.isAlive()){
                try{up=http.send(HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(1)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode()==200;if(up){break;}}
                catch(Exception ignored){}
                Thread.sleep(50);
            }
            assertTrue(up,"Executable server did not become healthy");
            HttpRequest generate=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1/aether/generate"))
                    .timeout(Duration.ofSeconds(4)).header("Authorization","Bearer server-key-a")
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(request())).build();
            assertEquals(200,http.send(generate,HttpResponse.BodyHandlers.ofString()).statusCode());
            mode.set("OFFLINE");
            assertEquals(503,http.send(generate,HttpResponse.BodyHandlers.ofString()).statusCode());
        }finally{running.destroy();if(!running.waitFor(3,TimeUnit.SECONDS)){running.destroyForcibly();running.waitFor();}}
        String output=Files.readString(log);
        assertFalse(output.contains("server-key-a"));
        assertFalse(output.contains("Aether, what happened"));
    }

    private void start(int concurrency,int queue,int rpm,int seconds) throws Exception {
        server=new AetherServer(new ServerConfig("127.0.0.1",0,3,
                "http://127.0.0.1:"+mock.getAddress().getPort(),"aether-custom:8b",seconds,256,
                List.of("server-key-a","server-key-b"),concurrency,queue,rpm,65536,4,false,false,false,""));
        server.start();server.refreshHealth();
    }
    private HttpResponse<String> get(String path,String key) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+path)).timeout(Duration.ofSeconds(3)).GET();
        if(key!=null){request.header("Authorization","Bearer "+key);}
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private CompletableFuture<HttpResponse<String>> postAsync(String body,String key){
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+"/v1/aether/generate"))
                .timeout(Duration.ofSeconds(6)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if(key!=null){request.header("Authorization","Bearer "+key);}
        return http.sendAsync(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String body,String key) throws Exception {return postAsync(body,key).get(7,TimeUnit.SECONDS);}
    private static JsonObject json(HttpResponse<String> response){return JsonParser.parseString(response.body()).getAsJsonObject();}
    private static String request(){return request(UUID.randomUUID().toString(),"server-a");}
    private static String request(String id,String server){
        JsonObject body=new JsonObject();
        body.addProperty("requestId",id);body.addProperty("serverId",server);
        body.addProperty("world","minecraft:overworld");body.addProperty("playerId",UUID.randomUUID().toString());
        body.addProperty("playerName","Tester");body.addProperty("speaker","Aether");body.addProperty("message","Aether, what happened?");
        body.add("context",JsonParser.parseString("{\"dimension\":\"minecraft:overworld\",\"biome\":\"forest\",\"surface\":true,\"tags\":[\"surface\",\"zone:meteor_site\",\"lore:project_eden_03\"]}"));
        return body.toString();
    }
}
