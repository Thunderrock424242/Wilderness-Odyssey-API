package com.thunder.aether.server.api;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrictJsonTest {
    @Test void rejectsDuplicateMembersAndNonJsonSyntaxAtTheTrustBoundary() {
        for (String input : new String[]{"{\"paused\":true,\"paused\":false}", "{paused:true}",
                "{'paused':true}", "{\"paused\":true} false", "{\"nested\":{\"a\":1,\"a\":2}}"}) {
            assertThrows(IllegalArgumentException.class, () -> JsonHttp.object(input.getBytes(StandardCharsets.UTF_8)),input);
        }
        assertTrue(JsonHttp.object("{\"paused\":true}".getBytes(StandardCharsets.UTF_8)).get("paused").getAsBoolean());
    }
}
