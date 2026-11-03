package com.thunder.aether.server.security;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MainServiceScopeTest {
    @Test void readScopeIsIndependentAndRejectsSecretReuse() {
        var values=Map.of("MAIN_SERVER_READ_TOKEN","main-read-token-000000000000000000001","AETHER_MONITORING_TOKEN","monitor-token-00000000000000000000001");
        var config=GatewaySecurityConfig.load(Map.of(),values,Path.of("."));
        try(var auth=new ServiceAuthenticator(config)) {
            Headers h=new Headers();h.set("Authorization","Bearer "+values.get("MAIN_SERVER_READ_TOKEN"));
            assertEquals(ServiceAuthenticator.Scope.SERVICE_READ,auth.authenticate(h,Instant.now()));
        }
        assertThrows(IllegalArgumentException.class,()->GatewaySecurityConfig.load(Map.of(),Map.of(
                "MAIN_SERVER_READ_TOKEN",values.get("AETHER_MONITORING_TOKEN"),"AETHER_MONITORING_TOKEN",values.get("AETHER_MONITORING_TOKEN")),Path.of(".")));
    }
}
