package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticStoreTest {
    @TempDir Path directory;

    @Test
    void filtersChatAndRedactsBeforeRetention() {
        var buffer = new DiagnosticLogBuffer(1024);
        buffer.append("INFO", "net.minecraft.client.gui.components.ChatComponent", "[CHAT] secret conversation");
        buffer.append("INFO", "net.minecraft.server.MinecraftServer", "<Alex> another private conversation");
        buffer.append("ERROR", "example", "Bearer abcdef01234567890123456789012345 password=hunter2 https://discord.com/api/webhooks/123/SECRET user@example.com C:\\Users\\Alice\\game 1.2.3.4");
        String text = buffer.drain();
        assertFalse(text.contains("conversation"));
        for (String secret : new String[]{"abcdef", "hunter2", "SECRET", "user@example.com", "Alice", "1.2.3.4"}) {
            assertFalse(text.contains(secret), secret);
        }
        assertEquals("", buffer.drain());
    }

    @Test
    void boundsLogMemoryEvenForLargeUnicodeMessages() {
        var buffer = new DiagnosticLogBuffer(1000);
        buffer.append("ERROR", "example", "界".repeat(100_000));
        assertTrue(buffer.drain().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 1000);
    }

    @Test
    void recoversSeparateKindsWithStableIdsAndDiscardRemovesPendingFiles() throws Exception {
        var store = new DiagnosticStore(directory);
        var log = artifact("log", "one");
        var crash = artifact("crash", "two");
        store.enqueue(log);
        store.enqueue(crash);
        var recovered = new DiagnosticStore(directory);
        assertEquals(2, recovered.pending().size());
        assertTrue(recovered.pending().stream().anyMatch(item -> item.artifactId().equals(log.artifactId()) && item.kind().equals("log")));
        recovered.acknowledge(log.artifactId());
        assertEquals("crash", new DiagnosticStore(directory).pending().getFirst().kind());
        recovered.discard();
        assertTrue(new DiagnosticStore(directory).pending().isEmpty());
    }

    @Test
    void capsQueueAndSkipsCorruptRowsWithoutScanningUnrelatedFiles() throws Exception {
        var store = new DiagnosticStore(directory);
        for (int i = 0; i < 20; i++) store.enqueue(artifact("log", "entry" + i));
        assertEquals(16, store.pending().size());
        Files.writeString(directory.resolve("not-an-artifact.json"), "{bad");
        assertEquals(16, new DiagnosticStore(directory).pending().size());
    }

    private static DiagnosticProtocol.Artifact artifact(String kind, String text) {
        return new DiagnosticProtocol.Artifact("1.0", UUID.randomUUID().toString(), "campaign-1", "tester-1", "collection-1",
                "session-1", kind, "client", Instant.EPOCH.toString(), "text/plain", "utf8", text);
    }
}
