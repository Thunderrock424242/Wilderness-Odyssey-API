package com.thunder.wildernessodysseyapi.playtest.verification;

import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.playtest.LocalWebhook;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftVerificationRelayClientTest {
    private final MinecraftVerificationRelayClient client = new MinecraftVerificationRelayClient();

    @Test
    void acceptsBoundedCodesWithoutChangingCase() {
        assertTrue(MinecraftVerificationRelayClient.validCode("Ab09-_"));
        assertTrue(MinecraftVerificationRelayClient.validCode("a".repeat(64)));
    }

    @Test
    void rejectsMalformedAndOverlongCodesBeforePosting() {
        for (String code : new String[]{"", "abc", "a".repeat(65), " abc123", "ab cd", "@everyone", "雪12345", "code\nmore", "a.bcd"}) {
            assertFalse(MinecraftVerificationRelayClient.validCode(code));
            assertEquals(REJECTED, client.sendVerification("", 1, code, "uuid", "name").join());
        }
        assertFalse(MinecraftVerificationRelayClient.validCode(null));
    }

    @Test
    void missingConfigurationIsRecoverable() {
        assertEquals(NOT_CONFIGURED, client.sendVerification("", 1, "validCode", "uuid", "name").join());
    }

    @Test
    void preservesBotContractAndOnlyConfirmsDelivery() throws Exception {
        try (var endpoint = new LocalWebhook(204, "", false)) {
            assertEquals(DELIVERED, client.sendVerification(endpoint.endpoint(), 2, "Ab09-_", "uuid", "Player")
                    .get(4, TimeUnit.SECONDS));
            var payload = JsonParser.parseString(endpoint.request).getAsJsonObject();
            var content = JsonParser.parseString(payload.get("content").getAsString()).getAsJsonObject();
            assertEquals("wo_minecraft_verify", content.get("type").getAsString());
            assertEquals("Ab09-_", content.get("code").getAsString());
            assertEquals("uuid", content.get("minecraftUuid").getAsString());
            assertEquals("Player", content.get("minecraftName").getAsString());
            assertTrue(payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
        }
    }

    @Test
    void handlesExpiredCodeAndHttpFailure() throws Exception {
        try (var expired = new LocalWebhook(410, "", false);
             var unavailable = new LocalWebhook(500, "error", false)) {
            assertEquals(REJECTED, client.sendVerification(expired.endpoint(), 2, "validCode", "uuid", "Player").get(4, TimeUnit.SECONDS));
            assertEquals(UNAVAILABLE, client.sendVerification(unavailable.endpoint(), 2, "validCode", "uuid", "Player").get(4, TimeUnit.SECONDS));
        }
    }

    @Test
    void handlesMalformedRelayReplyAndTimeout() throws Exception {
        try (var malformed = new LocalWebhook(200, "invalid", false);
             var delayed = new LocalWebhook(204, "", true)) {
            assertEquals(INVALID_RESPONSE, client.sendVerification(malformed.endpoint(), 2, "validCode", "uuid", "Player").get(4, TimeUnit.SECONDS));
            assertEquals(UNAVAILABLE, client.sendVerification(delayed.endpoint(), 1, "validCode", "uuid", "Player").get(3, TimeUnit.SECONDS));
        }
    }
}