package com.thunder.aether.server.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerSecurityConfigTest {
    @Test void rejectsPublicOllamaEndpointsAndPublicOriginBindings() {
        assertThrows(IllegalArgumentException.class, () -> config("0.0.0.0","http://127.0.0.1:11434"));
        assertThrows(IllegalArgumentException.class, () -> config("127.0.0.1","https://8.8.8.8:11434"));
        assertThrows(IllegalArgumentException.class, () -> config("127.0.0.1","https://public.example.com"));
        for (String host : new String[]{"127.0.0.1", "localhost", "10.1.2.3", "172.16.1.1", "192.168.1.2", "[::1]", "[fd12::1]"}) {
            assertDoesNotThrow(() -> config("127.0.0.1","http://"+host+":11434"));
        }
    }

    @Test void packagedDefaultsCannotActivateProduction() throws Exception {
        ServerConfig defaults = ServerConfig.load(null, java.util.Map.of());
        assertFalse(defaults.activation().permits(defaults.model()));
        assertFalse(defaults.security().administrationEnabled());
        assertEquals("127.0.0.1", defaults.bind());
    }

    private ServerConfig config(String bind,String ollama) {
        return new ServerConfig(bind,0,3,ollama,"aether-custom:8b",3,256,1,1,60,65536,4,false,false,false,"");
    }
}
