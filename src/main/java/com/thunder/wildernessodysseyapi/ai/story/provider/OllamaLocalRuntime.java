package com.thunder.wildernessodysseyapi.ai.story.provider;

import com.thunder.wildernessodysseyapi.ai.story.AISettings;
import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts an installed Windows Ollama application for private local Aether chat.
 *
 * <p>This class owns only availability and process startup. Model requests and
 * warm-up remain owned by {@link OllamaChatClient}, while scheduling remains
 * owned by the repository's existing asynchronous I/O executor.</p>
 */
public final class OllamaLocalRuntime {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final long POLL_INTERVAL_MILLIS = 250L;
    private static final long POLL_SLEEP_MILLIS = 50L;
    private static final Duration POLL_PROBE_TIMEOUT = Duration.ofMillis(
            POLL_INTERVAL_MILLIS - POLL_SLEEP_MILLIS
    );
    private static final String WINDOWS_APPLICATION = "ollama app.exe";
    private static final String WINDOWS_SERVER = "ollama.exe";

    private final EndpointProbe endpointProbe;
    private final ProcessStarter processStarter;
    private final Sleeper sleeper;
    private final String operatingSystem;
    private final String localAppData;
    private final String pathEnvironment;
    private final AtomicBoolean startupInProgress = new AtomicBoolean();

    /** Creates the production runtime using the current Windows environment. */
    public OllamaLocalRuntime() {
        this(
                new HttpEndpointProbe(),
                new DirectProcessStarter(),
                Thread::sleep,
                System.getProperty("os.name", ""),
                System.getenv("LOCALAPPDATA"),
                System.getenv("PATH")
        );
    }

    OllamaLocalRuntime(
            EndpointProbe endpointProbe,
            ProcessStarter processStarter,
            Sleeper sleeper,
            String operatingSystem,
            String localAppData,
            String pathEnvironment
    ) {
        this.endpointProbe = endpointProbe;
        this.processStarter = processStarter;
        this.sleeper = sleeper;
        this.operatingSystem = operatingSystem == null ? "" : operatingSystem;
        this.localAppData = localAppData;
        this.pathEnvironment = pathEnvironment;
    }

    /**
     * Ensures the configured loopback Ollama endpoint is available.
     *
     * <p>The caller must invoke this method from a background I/O worker. It
     * may wait for the installed application to publish its local endpoint.</p>
     */
    public StartupResult ensureAvailable(AISettings settings) {
        if (settings == null) {
            return StartupResult.INVALID_ENDPOINT;
        }
        Optional<URI> tagsUri = resolveTagsUri(settings.getEndpoint());
        if (tagsUri.isEmpty()) {
            ModConstants.LOGGER.warn("[Aether] Ollama auto-start rejected a non-loopback endpoint.");
            return StartupResult.INVALID_ENDPOINT;
        }

        try {
            if (endpointProbe.isAvailable(tagsUri.get(), PROBE_TIMEOUT)) {
                return StartupResult.ALREADY_RUNNING;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return StartupResult.INTERRUPTED;
        }

        if (!settings.isOllamaAutostartEnabled()) {
            return StartupResult.AUTOSTART_DISABLED;
        }
        if (!isWindows(operatingSystem)) {
            ModConstants.LOGGER.warn(
                    "[Aether] Ollama auto-start is currently supported only on Windows; continuing with fallback."
            );
            return StartupResult.UNSUPPORTED_PLATFORM;
        }

        List<Path> executables = resolveExecutables(
                settings.getOllamaExecutable(),
                localAppData,
                pathEnvironment
        );
        if (executables.isEmpty()) {
            ModConstants.LOGGER.warn(
                    "[Aether] Ollama is stopped and no trusted Windows installation was found. "
                            + "Install Ollama or set settings.ollama_executable in ai_config.yaml."
            );
            return StartupResult.EXECUTABLE_NOT_FOUND;
        }
        if (!startupInProgress.compareAndSet(false, true)) {
            return StartupResult.STARTUP_IN_PROGRESS;
        }

        try {
            int remainingPolls = Math.max(
                    1,
                    (int) Math.ceil(settings.getOllamaStartupTimeoutSeconds() * 1000.0D / POLL_INTERVAL_MILLIS)
            );
            boolean launchedAnyCandidate = false;
            for (int candidateIndex = 0; candidateIndex < executables.size(); candidateIndex++) {
                Path executable = executables.get(candidateIndex);
                List<String> command = buildLaunchCommand(executable);
                try {
                    processStarter.start(command);
                    launchedAnyCandidate = true;
                    ModConstants.LOGGER.info(
                            "[Aether] Started {}; waiting for its loopback endpoint.",
                            executable.getFileName()
                    );
                } catch (IOException | RuntimeException exception) {
                    ModConstants.LOGGER.warn(
                            "[Aether] Failed to start {}: {}",
                            executable.getFileName(),
                            safeMessage(exception.getMessage())
                    );
                    continue;
                }

                int candidatesRemaining = executables.size() - candidateIndex;
                int candidatePolls = Math.max(1, remainingPolls / candidatesRemaining);
                remainingPolls = Math.max(0, remainingPolls - candidatePolls);
                for (int poll = 0; poll < candidatePolls; poll++) {
                    sleeper.sleep(POLL_SLEEP_MILLIS);
                    if (endpointProbe.isAvailable(tagsUri.get(), POLL_PROBE_TIMEOUT)) {
                        ModConstants.LOGGER.info("[Aether] Local Ollama endpoint is ready.");
                        return StartupResult.STARTED;
                    }
                }

                if (candidateIndex + 1 < executables.size()) {
                    ModConstants.LOGGER.warn(
                            "[Aether] {} did not make Ollama ready; trying {}.",
                            executable.getFileName(),
                            executables.get(candidateIndex + 1).getFileName()
                    );
                }
            }
            if (!launchedAnyCandidate) {
                return StartupResult.START_FAILED;
            }
            ModConstants.LOGGER.warn(
                    "[Aether] Ollama did not become ready within {} seconds; continuing with fallback.",
                    settings.getOllamaStartupTimeoutSeconds()
            );
            return StartupResult.TIMED_OUT;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return StartupResult.INTERRUPTED;
        } finally {
            startupInProgress.set(false);
        }
    }

    static Optional<URI> resolveTagsUri(String endpoint) {
        Optional<URI> chatUri = OllamaChatClient.resolveChatUri(endpoint);
        if (chatUri.isEmpty()) {
            return Optional.empty();
        }
        URI validated = chatUri.get();
        try {
            return Optional.of(new URI(
                    validated.getScheme(),
                    null,
                    validated.getHost(),
                    validated.getPort(),
                    "/api/tags",
                    null,
                    null
            ));
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    static Optional<Path> resolveExecutable(
            String configuredExecutable,
            String localAppData,
            String pathEnvironment
    ) {
        return resolveExecutables(configuredExecutable, localAppData, pathEnvironment)
                .stream()
                .findFirst();
    }

    static List<Path> resolveExecutables(
            String configuredExecutable,
            String localAppData,
            String pathEnvironment
    ) {
        Optional<Path> configured = trustedExecutable(configuredExecutable);
        if (configured.isPresent()) {
            return List.of(configured.get());
        }

        Set<Path> discoveredServers = new LinkedHashSet<>();
        Set<Path> discoveredApplications = new LinkedHashSet<>();
        if (localAppData != null && !localAppData.isBlank()) {
            try {
                Path installation = Path.of(localAppData).resolve("Programs").resolve("Ollama");
                trustedExecutable(installation.resolve(WINDOWS_SERVER)).ifPresent(discoveredServers::add);
                trustedExecutable(installation.resolve(WINDOWS_APPLICATION)).ifPresent(discoveredApplications::add);
            } catch (InvalidPathException ignored) {
                // Continue to the bounded PATH lookup.
            }
        }

        if (pathEnvironment != null && !pathEnvironment.isBlank()) {
            for (String entry : pathEnvironment.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                String directory = stripQuotes(entry.trim());
                if (directory.isEmpty()) {
                    continue;
                }
                try {
                    Path installation = Path.of(directory);
                    trustedExecutable(installation.resolve(WINDOWS_SERVER)).ifPresent(discoveredServers::add);
                    trustedExecutable(installation.resolve(WINDOWS_APPLICATION)).ifPresent(discoveredApplications::add);
                } catch (InvalidPathException ignored) {
                    // Ignore malformed PATH entries without expanding the search scope.
                }
            }
        }

        List<Path> discovered = new ArrayList<>(discoveredServers.size() + discoveredApplications.size());
        discovered.addAll(discoveredServers);
        discovered.addAll(discoveredApplications);
        return List.copyOf(discovered);
    }

    static List<String> buildLaunchCommand(Path executable) {
        Path normalized = executable.toAbsolutePath().normalize();
        if (WINDOWS_SERVER.equalsIgnoreCase(normalized.getFileName().toString())) {
            return List.of(normalized.toString(), "serve");
        }
        return List.of(normalized.toString());
    }

    private static Optional<Path> trustedExecutable(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path candidate = Path.of(stripQuotes(rawPath.trim()));
            if (!candidate.isAbsolute()) {
                return Optional.empty();
            }
            return trustedExecutable(candidate);
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Path> trustedExecutable(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        Path filename = normalized.getFileName();
        if (filename == null || !isAllowedExecutableName(filename.toString()) || !Files.isRegularFile(normalized)) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private static boolean isAllowedExecutableName(String filename) {
        return WINDOWS_APPLICATION.equalsIgnoreCase(filename)
                || WINDOWS_SERVER.equalsIgnoreCase(filename);
    }

    private static boolean isWindows(String operatingSystem) {
        return operatingSystem.toLowerCase(Locale.ROOT).contains("windows");
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "no additional detail";
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() > 180 ? singleLine.substring(0, 180) : singleLine;
    }

    /** Result of one bounded endpoint check and optional startup attempt. */
    public enum StartupResult {
        ALREADY_RUNNING(true),
        STARTED(true),
        AUTOSTART_DISABLED(false),
        INVALID_ENDPOINT(false),
        UNSUPPORTED_PLATFORM(false),
        EXECUTABLE_NOT_FOUND(false),
        STARTUP_IN_PROGRESS(false),
        START_FAILED(false),
        TIMED_OUT(false),
        INTERRUPTED(false);

        private final boolean available;

        StartupResult(boolean available) {
            this.available = available;
        }

        /** Returns whether the caller can proceed to the normal model warm-up. */
        public boolean isAvailable() {
            return available;
        }
    }

    @FunctionalInterface
    interface EndpointProbe {
        boolean isAvailable(URI uri, Duration timeout) throws InterruptedException;
    }

    @FunctionalInterface
    interface ProcessStarter {
        void start(List<String> command) throws IOException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    private static final class HttpEndpointProbe implements EndpointProbe {
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(PROBE_TIMEOUT)
                .build();

        @Override
        public boolean isAvailable(URI uri, Duration timeout) throws InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() / 100 == 2;
            } catch (IOException | RuntimeException exception) {
                return false;
            }
        }
    }

    private static final class DirectProcessStarter implements ProcessStarter {
        @Override
        public void start(List<String> command) throws IOException {
            new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
    }
}
