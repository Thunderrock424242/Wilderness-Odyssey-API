package com.thunder.aether.server.bootstrap;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LauncherExecutableTest {
    @TempDir Path directory;

    @Test void validatesOperatorConfigWithoutStartingServicesOrCreatingState() throws Exception {
        Path prompts = directory.resolve("behavior.yml");
        Files.writeString(prompts, "personality:\n  name: Aether\n  tone: Patient\n");
        Path config = directory.resolve("aether-server.yml");
        Files.writeString(config, "prompts_file: behavior.yml\n");
        String output = run("--validate-config", config.toString());
        assertTrue(output.contains("\"configurationValid\":true"), output);
        assertTrue(output.contains("\"inferenceActivated\":false"), output);
        assertFalse(Files.exists(directory.resolve("aether-state")));
        assertFalse(Files.exists(directory.resolve("runtime")));
    }

    @Test void validatesBehaviorBeforeUpload() throws Exception {
        Path prompts = directory.resolve("behavior.yml");
        Files.writeString(prompts, "personality:\n  name: Aether\nsubsystems:\n  - name: Terra\n");
        String output = run("--validate-prompts", prompts.toString());
        assertTrue(output.contains("\"promptsValid\":true"), output);
        assertTrue(output.contains("Terra"), output);
    }

    @Test void malformedBehaviorFailsWithoutEchoingOperatorData() throws Exception {
        Path prompts = directory.resolve("invalid.yml");
        Files.writeString(prompts, "personality: !!invalid operator-private-test-value\n");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("aether.jar"), "--validate-prompts", prompts.toString())
                .redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(1, process.exitValue(), output);
            assertFalse(output.contains("operator-private-test-value"), output);
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
    }

    private String run(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("aether.jar")));
        command.addAll(java.util.List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            return output;
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
    }

    @Test void helpExplainsBundledStartupWithoutStartingServices() throws Exception {
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("aether.jar"), "--help").redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("--data-dir"), output);
            assertTrue(output.contains("--external"), output);
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
    }
}
