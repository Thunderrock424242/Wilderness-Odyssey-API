package com.thunder.aether.server;

import com.google.gson.JsonObject;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.*;
import com.thunder.aether.server.config.*;
import com.thunder.aether.server.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GatewayAdministrationTest {
    @TempDir Path directory;
    private static final String ADMIN = "test-administration-credential-0000000001";
    private static final String MONITOR = "test-monitoring-credential-000000000000001";
    private static final String INFERENCE = "test-inference-credential-0000000000000001";

    @Test void signedAdministrationIsScopedDurableAndCannotEnableUncommissionedInference() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("trusted").generate();
        String assertion = assertion(key);
        JsonObject command = new JsonObject();
        command.addProperty("requestId",UUID.randomUUID().toString());
        command.addProperty("expectedRevision",0);
        command.addProperty("expiresAt",Instant.now().plusSeconds(240).toString());
        command.addProperty("paused",true);
        command.addProperty("actor","discord-staff-123");
        try (HttpClient http = HttpClient.newHttpClient(); AetherServer gateway = gateway(key)) {
            gateway.start();
            assertEquals(401, post(http,gateway,ADMIN,null,command.toString()).statusCode());
            assertEquals(401, post(http,gateway,ADMIN,"forged",command.toString()).statusCode());
            assertEquals(403, post(http,gateway,INFERENCE,assertion,command.toString()).statusCode());
            assertEquals(200, post(http,gateway,ADMIN,assertion,command.toString()).statusCode());
            assertTrue(post(http,gateway,ADMIN,assertion,command.toString()).body().contains("\"replayed\":true"));
            command.addProperty("paused",false);
            assertEquals(409, post(http,gateway,ADMIN,assertion,command.toString()).statusCode());
            assertTrue(get(http,gateway,"/v1/admin/admission",MONITOR).body().contains("\"paused\":true"));
            assertEquals(401,get(http,gateway,"/health",ADMIN).statusCode());
            var status = get(http,gateway,"/v1/aether/status",INFERENCE);
            assertTrue(status.body().contains("ACTIVATION_REQUIRED"));
            assertFalse(status.body().contains("model"));
        }
        try (HttpClient http = HttpClient.newHttpClient(); AetherServer gateway = gateway(key)) {
            gateway.start();
            assertTrue(get(http,gateway,"/v1/admin/admission",MONITOR).body().contains("\"paused\":true"));
            command.addProperty("requestId",UUID.randomUUID().toString());
            command.addProperty("expectedRevision",1);
            assertEquals(200,post(http,gateway,ADMIN,assertion,command.toString()).statusCode());
            assertTrue(get(http,gateway,"/v1/aether/status",INFERENCE).body().contains("ACTIVATION_REQUIRED"));
        }
        String audit = Files.readString(directory.resolve("requests.jsonl"));
        assertTrue(audit.contains("ADMINISTRATION"));
        assertFalse(audit.contains(ADMIN));
        assertFalse(audit.contains(assertion));
        assertFalse(audit.contains("discord-staff-123"));
        assertEquals(2,Files.readAllLines(directory.resolve("admission.jsonl")).size());
    }

    private AetherServer gateway(RSAKey key) throws Exception {
        var security = GatewaySecurityConfig.load(Map.of("administration_enabled",true,
                        "access_issuer","https://team.cloudflareaccess.com","access_audience","gateway",
                        "administration_client_id","kinetic-admin"),
                Map.of("AETHER_INFERENCE_TOKEN",INFERENCE,"AETHER_MONITORING_TOKEN",MONITOR,
                        "AETHER_ADMINISTRATION_TOKEN",ADMIN),directory);
        var config = new ServerConfig("127.0.0.1",0,3,"http://127.0.0.1:9","aether-custom:8b",
                3,256,1,1,60,65536,4,false,false,false,"",security,ActivationConfig.disabled());
        var verifier = new AccessTokenVerifier(security.accessIssuer(),security.accessAudience(),
                new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())));
        return new AetherServer(config,new ServiceAuthenticator(security,verifier));
    }

    private String assertion(RSAKey key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer("https://team.cloudflareaccess.com").audience("gateway")
                        .claim("common_name","kinetic-admin").issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(300))).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private HttpResponse<String> post(HttpClient http,AetherServer gateway,String token,String jwt,String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+gateway.port()+"/v1/admin/admission"))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (jwt != null) { request.header("Cf-Access-Jwt-Assertion",jwt); }
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient http,AetherServer gateway,String path,String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+gateway.port()+path))
                .header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
}
