package com.thunder.wildernessodysseyapi.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlaytestConfigMigrationTest {
    @TempDir Path directory;

    @Test
    void movesPrivateSettingsAndPreservesUnrelatedCategories() throws Exception {
        Path common = directory.resolve("common.toml");
        Path server = directory.resolve("server.toml");
        Files.writeString(common, "[ownership]\nenabled = true\n");
        StringBuilder original = new StringBuilder("[weather]\nenabled = false\n");
        for (String section : List.of("verificationRelay", "telemetry", "playerTelemetry", "eventTelemetry", "feedback")) {
            original.append("[").append(section).append("]\nvalue = \"private-test-value\"\n");
        }
        Files.writeString(server, original);
        PlaytestConfigMigration.prepare(common, server);
        for (String section : List.of("verificationRelay", "telemetry", "playerTelemetry", "eventTelemetry", "feedback")) {
            assertTrue(read(common).contains(section));
            assertFalse(read(server).contains(section));
        }
        assertEquals(Boolean.TRUE, read(common).get("ownership.enabled"));
        assertEquals(Boolean.FALSE, read(server).get("weather.enabled"));
        assertFalse(Files.readString(server).contains("private-test-value"));
    }

    @Test
    void preservesCommonChoicesAndCanResumeAfterCommonWasAlreadyWritten() throws Exception {
        Path common = directory.resolve("common.toml");
        Path server = directory.resolve("server.toml");
        Files.writeString(common, "[feedback]\nenabled = false\nwebhookUrl = \"chosen\"\n");
        Files.writeString(server, "[feedback]\nenabled = true\nwebhookUrl = \"old\"\ncooldownSeconds = 45\n");
        PlaytestConfigMigration.prepare(common, server);
        assertEquals(Boolean.FALSE, read(common).get("feedback.enabled"));
        assertEquals("chosen", read(common).get("feedback.webhookUrl"));
        assertEquals(45, read(common).<Integer>get("feedback.cooldownSeconds").intValue());
        String savedCommon = Files.readString(common);
        String savedServer = Files.readString(server);
        PlaytestConfigMigration.prepare(common, server);
        assertEquals(savedCommon, Files.readString(common));
        assertEquals(savedServer, Files.readString(server));
    }

    @Test
    void malformedInputStopsRegistrationWithoutLeakingSecrets() throws Exception {
        Path common = directory.resolve("common.toml");
        Path server = directory.resolve("server.toml");
        String original = "[feedback]\nwebhookUrl = \"private-test-value\"\n";
        Files.writeString(server, original);
        Files.writeString(common, "[feedback]\nwebhookUrl = \"private-test-value\n");
        var error = assertThrows(IllegalStateException.class, () -> PlaytestConfigMigration.prepare(common, server));
        assertEquals(original, Files.readString(server));
        assertFalse(error.getMessage().contains("private-test-value"));
        assertNull(error.getCause());
    }

    @Test
    void absentServerDoesNotCreateFiles() {
        PlaytestConfigMigration.prepare(directory.resolve("common.toml"), directory.resolve("server.toml"));
        assertFalse(Files.exists(directory.resolve("common.toml")));
    }

    private CommentedConfig read(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(path)) {
            return new TomlParser().parse(reader);
        }
    }
}