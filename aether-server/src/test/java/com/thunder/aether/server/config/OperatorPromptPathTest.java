package com.thunder.aether.server.config;

import com.thunder.aether.server.model.PromptCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OperatorPromptPathTest {
    @TempDir Path directory;

    @Test void loadsBehaviorBesideConfigRegardlessOfProcessWorkingDirectory() throws Exception {
        Files.writeString(directory.resolve("behavior.yml"), "personality:\n  name: Aether\nsubsystems:\n  - name: Terra\n");
        Path configFile = directory.resolve("aether-server.yml");
        Files.writeString(configFile, "prompts_file: behavior.yml\n");
        ServerConfig config = ServerConfig.load(configFile, Map.of());
        PromptCatalog prompts = assertDoesNotThrow(() -> new PromptCatalog(config.promptsFile()));
        assertEquals(java.util.List.of("Aether", "Terra"), prompts.speakers());
    }
}
