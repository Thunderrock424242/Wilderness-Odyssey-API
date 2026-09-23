package com.thunder.aether.server.bootstrap;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LauncherExecutableTest {
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
