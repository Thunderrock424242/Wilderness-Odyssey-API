package com.thunder.aether.server.playground;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.aether.server.model.PromptCatalog;
import com.thunder.aether.server.ollama.OllamaAdapter;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SandboxRuntimeTest {
    @Test void usesOnlyIsolatedMessagesAndEnforcesRuntimeIdentity() throws Exception {
        HttpServer runtime=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        runtime.createContext("/api/chat",e->{
            var body=JsonHttp.object(e.getRequestBody().readAllBytes());
            assertEquals("tiny:local",body.get("model").getAsString()); assertFalse(body.has("tools"));
            assertFalse(body.has("format")); assertEquals(1,body.getAsJsonArray("messages").size());
            assertEquals("private sandbox",body.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
            JsonHttp.send(e,200,java.util.Map.of("model","tiny:local","done",true,"done_reason","stop","message",java.util.Map.of("content","Real runtime answer")));
        }); runtime.start();
        var config=new ServerConfig("127.0.0.1",0,3,"http://127.0.0.1:"+runtime.getAddress().getPort(),"production:8b",3,256,1,1,60,65536,4,false,false,false,"");
        try(var adapter=new OllamaAdapter(config,new PromptCatalog(""))) {
            JsonArray messages=new JsonArray(); JsonObject m=new JsonObject(); m.addProperty("role","user");m.addProperty("content","private sandbox");messages.add(m);
            var result=adapter.sandbox("tiny:local",messages,12,0.3,System.nanoTime()+TimeUnit.SECONDS.toNanos(2));
            assertEquals("Real runtime answer",result.answer()); assertEquals("stop",result.finishReason());
        } finally { runtime.stop(0); }
    }
}
