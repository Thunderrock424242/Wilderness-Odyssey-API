package com.thunder.wildernessodysseyapi.ai.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.thunder.aether.server.AetherServer;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.wildernessodysseyapi.ai.story.provider.AetherBackendClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** The actual mod-side client talks to the actual standalone service, with only Ollama mocked. */
class AIClientGatewayIntegrationTest {
    @TempDir Path state;

    @Test
    void gatewayHealthGenerationAndOutageReachActualMinecraftFallback() throws Exception {
        HttpServer ollama=HttpServer.create(new InetSocketAddress("127.0.0.1",0),16);
        var workers=Executors.newFixedThreadPool(2);
        ollama.setExecutor(workers);
        ollama.createContext("/api/tags",exchange -> {
            byte[] bytes="{\"models\":[{\"name\":\"aether-custom:8b\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
            exchange.close();
        });
        ollama.createContext("/api/chat",exchange -> {
            String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            JsonObject envelope=new JsonObject(),message=new JsonObject();
            message.addProperty("content",input.contains("strict factual response verifier")
                    ? "{\"approved\":true}" : "{\"speaker\":\"Aether\",\"display\":\"Verified gateway reply.\",\"speech\":\"Verified gateway reply.\"}");
            envelope.add("message",message);
            byte[] bytes=envelope.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
            exchange.close();
        });
        ollama.start();
        try(AetherServer gateway=new AetherServer(new ServerConfig("127.0.0.1",0,3,
                "http://127.0.0.1:"+ollama.getAddress().getPort(),"aether-custom:8b",3,256,
                List.of("test-gateway-key"),2,4,60,65536,4,false,false,false,""))){
            gateway.start();gateway.refreshHealth();
            AIBackendConfig backendConfig=new AIBackendConfig(true,AIBackendConfig.Mode.REMOTE,
                    "http://127.0.0.1:"+gateway.port(),"test-gateway-key","minecraft-test-server",
                    4,1,0,30,2,false);
            try(AetherBackendClient transport=new AetherBackendClient(backendConfig)){
                var health=transport.checkHealth();
                assertTrue(health.reachable());assertTrue(health.modelReady());
            }
            AIConfig config;
            try(var input=getClass().getClassLoader().getResourceAsStream("ai_config.yaml")){
                config=AIConfigLoader.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            }
            config.setBackend(backendConfig);
            try(AIClient client=new AIClient(config,state,() -> true)){
                UUID player=UUID.randomUUID();
                var context=new AIFallbackResponder.ResponseContext(Set.of("surface","biome:forest","zone:meteor_site"));
                assertEquals("Verified gateway reply.",client.sendMessageWithVoice("minecraft:overworld",
                        "save-"+player,player,"Tester","Aether, hello",context).text());
                gateway.close();
                String question="Aether, show prompts";
                assertEquals(client.fallback(question,context),client.sendMessageWithVoice("minecraft:overworld",
                        "save-"+player,player,"Tester",question,context));
                assertFalse(client.getBackendStatus().reachable());
            }
        }finally{ollama.stop(0);workers.shutdownNow();}
    }
}
