package com.thunder.wildernessodysseyapi.ai.story.provider;

import com.thunder.wildernessodysseyapi.ai.story.AISettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounded Ollama discovery and startup without launching a process. */
class OllamaLocalRuntimeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesOnlyValidatedLoopbackHealthEndpoints() {
        assertEquals(
                URI.create("http://127.0.0.1:11434/api/tags"),
                OllamaLocalRuntime.resolveTagsUri("http://127.0.0.1:11434").orElseThrow()
        );
        assertEquals(
                URI.create("http://[::1]:11434/api/tags"),
                OllamaLocalRuntime.resolveTagsUri("http://[::1]:11434/api/chat").orElseThrow()
        );
        assertTrue(OllamaLocalRuntime.resolveTagsUri("https://127.0.0.1:11434").isEmpty());
        assertTrue(OllamaLocalRuntime.resolveTagsUri("http://192.168.1.20:11434").isEmpty());
    }

    @Test
    void prefersInstalledWindowsApplicationAndRejectsArbitraryExecutables() throws Exception {
        Path installation = temporaryDirectory.resolve("Programs").resolve("Ollama");
        Files.createDirectories(installation);
        Path server = Files.createFile(installation.resolve("ollama.exe"));
        Path application = Files.createFile(installation.resolve("ollama app.exe"));
        Path arbitrary = Files.createFile(temporaryDirectory.resolve("something-else.exe"));

        assertEquals(
                application,
                OllamaLocalRuntime.resolveExecutable("", temporaryDirectory.toString(), "").orElseThrow()
        );
        assertTrue(OllamaLocalRuntime.resolveExecutable(
                arbitrary.toString(), "", "").isEmpty());
        assertEquals(
                List.of(server.toAbsolutePath().normalize().toString(), "serve"),
                OllamaLocalRuntime.buildLaunchCommand(server)
        );
        assertEquals(
                List.of(application.toAbsolutePath().normalize().toString()),
                OllamaLocalRuntime.buildLaunchCommand(application)
        );
    }

    @Test
    void alreadyRunningEndpointNeverStartsAnotherProcess() {
        OllamaLocalRuntime runtime = new OllamaLocalRuntime(
                (uri, timeout) -> true,
                command -> {
                    throw new AssertionError("process should not start");
                },
                milliseconds -> {
                    throw new AssertionError("startup should not wait");
                },
                "Windows 11",
                "",
                ""
        );

        assertEquals(
                OllamaLocalRuntime.StartupResult.ALREADY_RUNNING,
                runtime.ensureAvailable(new AISettings())
        );
    }

    @Test
    void startsTrustedApplicationAndWaitsUntilEndpointIsReady() throws Exception {
        Path application = Files.createFile(temporaryDirectory.resolve("ollama app.exe"));
        AtomicInteger probes = new AtomicInteger();
        List<List<String>> commands = new ArrayList<>();
        OllamaLocalRuntime runtime = new OllamaLocalRuntime(
                (uri, timeout) -> probes.getAndIncrement() > 0,
                command -> commands.add(List.copyOf(command)),
                milliseconds -> {
                },
                "Windows 11",
                "",
                ""
        );
        AISettings settings = new AISettings();
        settings.setOllamaExecutable(application.toString());

        assertEquals(OllamaLocalRuntime.StartupResult.STARTED, runtime.ensureAvailable(settings));
        assertEquals(List.of(List.of(application.toAbsolutePath().normalize().toString())), commands);
        assertEquals(2, probes.get());
    }

    @Test
    void disabledAutostartPreservesFallbackWithoutLaunching() {
        AISettings settings = new AISettings();
        settings.setOllamaAutostartEnabled(false);
        OllamaLocalRuntime runtime = new OllamaLocalRuntime(
                (uri, timeout) -> false,
                command -> {
                    throw new AssertionError("process should not start");
                },
                milliseconds -> {
                    throw new AssertionError("startup should not wait");
                },
                "Windows 11",
                temporaryDirectory.toString(),
                ""
        );

        OllamaLocalRuntime.StartupResult result = runtime.ensureAvailable(settings);

        assertEquals(OllamaLocalRuntime.StartupResult.AUTOSTART_DISABLED, result);
        assertFalse(result.isAvailable());
    }

    @Test
    void readinessPollingStopsAtConfiguredBound() throws Exception {
        Path application = Files.createFile(temporaryDirectory.resolve("ollama app.exe"));
        AtomicInteger probes = new AtomicInteger();
        AtomicInteger starts = new AtomicInteger();
        OllamaLocalRuntime runtime = new OllamaLocalRuntime(
                (uri, timeout) -> {
                    probes.incrementAndGet();
                    return false;
                },
                command -> starts.incrementAndGet(),
                milliseconds -> {
                },
                "Windows 11",
                "",
                ""
        );
        AISettings settings = new AISettings();
        settings.setOllamaExecutable(application.toString());
        settings.setOllamaStartupTimeoutSeconds(5);

        assertEquals(OllamaLocalRuntime.StartupResult.TIMED_OUT, runtime.ensureAvailable(settings));
        assertEquals(1, starts.get());
        assertEquals(21, probes.get());
    }
}
