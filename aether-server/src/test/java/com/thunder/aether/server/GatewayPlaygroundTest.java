package com.thunder.aether.server;

import com.google.gson.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.config.*;
import com.thunder.aether.server.playground.*;
import com.thunder.aether.server.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GatewayPlaygroundTest {
    @TempDir Path directory;
    final String monitor="monitor-000000000000000000000000001", admin="admin-00000000000000000000000000001";
    final String read="main-read-00000000000000000000000001", write="main-write-0000000000000000000000001";
    String assertion;
    @Test void peerContractsScopesCancellationSandboxAndMainAdapter() throws Exception {
        HttpServer runtime=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        runtime.createContext("/api/tags",e->JsonHttp.send(e,200,Map.of("models",List.of(Map.of("name","tiny:local")))));
        runtime.createContext("/api/chat",e->JsonHttp.send(e,200,Map.of("model","tiny:local","done",true,"done_reason","stop","message",Map.of("content","Isolated answer"))));runtime.start();
        try(HttpClient client=HttpClient.newHttpClient(); AetherServer server=server(runtime.getAddress().getPort())) {
            server.start(); server.refreshHealth();
            var caps=request(client,server,"GET","/v1/admin/playground/capabilities",monitor,null);
            assertEquals(200,caps.statusCode()); assertEquals("test-server",json(caps).get("serverId").getAsString());
            assertFalse(json(caps).getAsJsonObject("features").get("training").getAsBoolean());
            assertEquals("no-store",caps.headers().firstValue("cache-control").orElseThrow());
            String id=UUID.randomUUID().toString(), path="/v1/admin/playground/training/jobs/by-request/"+id;
            assertEquals(403,request(client,server,"GET",path,monitor,null).statusCode());
            assertEquals(404,request(client,server,"GET",path,admin,null).statusCode());
            var receipt=request(client,server,"POST",path+"/cancel",admin,"{\"requestId\":\""+id+"\"}");
            assertEquals(200,receipt.statusCode()); assertTrue(json(receipt).get("cancelledBeforeAcceptance").getAsBoolean());
            assertEquals(receipt.body(),request(client,server,"POST",path+"/cancel",admin,"{\"requestId\":\""+id+"\"}").body());
            JsonObject late=new JsonObject();late.addProperty("schemaVersion","1.0");late.addProperty("serverId","test-server");
            late.addProperty("requestId",id);late.addProperty("actor","staff");late.addProperty("baseModelId","tiny:local");late.addProperty("presetId","lora-smoke");
            String snapshot="[{\"id\":\"reviewed\",\"revision\":\"r1\",\"prompt\":\"Hello\",\"target\":\"Welcome\"}]";
            JsonObject dataset=new JsonObject();dataset.addProperty("json",snapshot);dataset.addProperty("sha256",PlaygroundContract.hash(snapshot));late.add("dataset",dataset);
            late.addProperty("reason","Explicit reviewed test");late.addProperty("createdAt",Instant.now().toString());late.addProperty("expiresAt",Instant.now().plusSeconds(120).toString());late.addProperty("acknowledgeTraining",true);
            var cancelledJob=request(client,server,"POST","/v1/admin/playground/training/jobs",admin,late.toString());
            assertEquals(200,cancelledJob.statusCode(),cancelledJob.body());assertEquals("cancelled",json(cancelledJob).get("state").getAsString());
            assertEquals(cancelledJob.body(),request(client,server,"GET",path,admin,null).body());
            late.addProperty("requestId",UUID.randomUUID().toString());
            assertEquals(503,request(client,server,"POST","/v1/admin/playground/training/jobs",admin,late.toString()).statusCode());
            String sandbox="{\"schemaVersion\":\"1.0\",\"serverId\":\"test-server\",\"requestId\":\""+id+"\",\"actor\":\"staff\",\"expiresAt\":\""+Instant.now().plusSeconds(60)+"\",\"modelId\":\"tiny:local\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}],\"maxOutputTokens\":16,\"temperature\":0.5}";
            var answer=request(client,server,"POST","/v1/admin/playground/sandbox",admin,sandbox);
            assertEquals(200,answer.statusCode(),answer.body()); assertEquals("Isolated answer",json(answer).get("answer").getAsString());
            assertEquals(422,request(client,server,"POST","/v1/admin/playground/sandbox",admin,sandbox.replace("test-server","wrong-server")).statusCode());
            var health=json(request(client,server,"GET","/v1/service/health",read,null));
            assertTrue(health.getAsJsonObject("minecraft").get("tps").isJsonNull());
            assertEquals("unknown",health.getAsJsonObject("minecraft").get("status").getAsString());
            long sequence=health.get("sequence").getAsLong();
            assertTrue(json(request(client,server,"GET","/v1/service/health",read,null)).get("sequence").getAsLong()>sequence);
            var actions=json(request(client,server,"GET","/v1/service/capabilities",read,null)).getAsJsonArray("actions");
            assertEquals("[\"ai.pause\"]",actions.toString());
            String operation="{\"operation_id\":\"operation_1\",\"server_id\":\"test-server\",\"action\":\"ai.pause\",\"parameters\":{\"paused\":true},\"expected_revision\":\"0\",\"created_at\":\""+Instant.now()+"\",\"expires_at\":\""+Instant.now().plusSeconds(120)+"\",\"actor\":\"guild|staff\",\"reason\":\"Maintenance requested\"}";
            assertEquals(403,request(client,server,"POST","/v1/service/operations",read,operation).statusCode());
            var op=request(client,server,"POST","/v1/service/operations",write,operation);
            assertEquals(200,op.statusCode(),op.body()); assertEquals("succeeded",json(op).get("state").getAsString());
            assertEquals(op.body(),request(client,server,"POST","/v1/service/operations",write,operation).body());
            assertEquals(409,request(client,server,"POST","/v1/service/operations",write,operation.replace("true","false")).statusCode());
            assertEquals(op.body(),request(client,server,"GET","/v1/service/operations/operation_1",read,null).body());
            assertEquals(403,request(client,server,"GET","/health",read,null).statusCode());
            JsonObject fixtures=new JsonObject();fixtures.add("capabilities",json(caps));fixtures.add("sandbox",json(answer));
            fixtures.add("cancelledJob",json(cancelledJob));
            fixtures.add("cancellation",json(receipt));fixtures.add("health",health);fixtures.add("operation",json(op));
            fixtures.add("serviceCapabilities",json(request(client,server,"GET","/v1/service/capabilities",read,null)));
            java.nio.file.Files.writeString(Path.of(System.getProperty("aether.jar")).getParent().getParent().resolve("peer-roundtrip.json"),fixtures.toString());
        } finally { runtime.stop(0); }
    }
    private AetherServer server(int port) throws Exception {
        RSAKey key=new RSAKeyGenerator(2048).keyID("test").generate();
        SignedJWT jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(),new JWTClaimsSet.Builder()
                .issuer("https://team.cloudflareaccess.com").audience("gateway").claim("common_name","kinetic-service")
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).build());jwt.sign(new RSASSASigner(key));assertion=jwt.serialize();
        var security=GatewaySecurityConfig.load(Map.of("minecraft_server_id","test-server","administration_enabled",true,
                "service_write_enabled",true,"access_issuer","https://team.cloudflareaccess.com","access_audience","gateway",
                "administration_client_id","kinetic-service","monitoring_client_id","kinetic-service","service_read_client_id","kinetic-service","service_write_client_id","kinetic-service"),
                Map.of("AETHER_MONITORING_TOKEN",monitor,"AETHER_ADMINISTRATION_TOKEN",admin,"MAIN_SERVER_READ_TOKEN",read,"MAIN_SERVER_WRITE_TOKEN",write),directory.resolve("native"));
        var playground=PlaygroundConfig.load(Map.of("sandbox_enabled",true,"models",List.of(Map.of("id","tiny:local","label","Tiny")),
                "presets",List.of("lora-smoke"),
                "state_directory",directory.resolve("playground").toString()),directory);
        var config=new ServerConfig("127.0.0.1",0,10,"http://127.0.0.1:"+port,"tiny:local",10,256,1,1,60,65536,4,false,false,false,"",security,ActivationConfig.disabled(),playground,8192);
        return new AetherServer(config,new ServiceAuthenticator(security,new AccessTokenVerifier(security.accessIssuer(),security.accessAudience(),new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())))));
    }
    private HttpResponse<String> request(HttpClient client,AetherServer server,String method,String path,String token,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+path)).header("Authorization","Bearer "+token)
                .header("Cf-Access-Jwt-Assertion",assertion);
        if(body!=null)builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonObject json(HttpResponse<String> r) { return JsonParser.parseString(r.body()).getAsJsonObject(); }
}
