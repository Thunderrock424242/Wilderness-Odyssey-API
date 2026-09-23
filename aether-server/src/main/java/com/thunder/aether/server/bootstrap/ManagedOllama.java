package com.thunder.aether.server.bootstrap;

import com.google.gson.JsonObject;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.config.ServerConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns only the native child launched for this data directory; never adopts a system Ollama service. */
public final class ManagedOllama implements AutoCloseable {
    @FunctionalInterface interface Starter { Process start(ProcessBuilder builder) throws IOException; }
    private final Process child;
    private final HttpClient http;
    private final URI endpoint;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ManagedOllama(Process child, URI endpoint) {
        this.child = child; this.endpoint = endpoint;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Starts and prepares the private runtime, closing it if any setup stage fails. */
    public static ManagedOllama launch(ServerConfig config, BundleManifest bundle, Path data) throws Exception {
        return launch(config, bundle, data, ProcessBuilder::start);
    }

    static ManagedOllama launch(ServerConfig config, BundleManifest bundle, Path directory, Starter starter) throws Exception {
        Path data = directory.toAbsolutePath().normalize();
        URI endpoint = JsonHttp.baseUri(config.ollamaUrl());
        if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
                || endpoint.getPort() < 1 || !endpoint.getPath().isEmpty()
                || !config.model().equals(bundle.targetModel())) {
            throw new IOException("Managed mode requires a loopback Ollama endpoint and the bundled target model.");
        }
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), endpoint.getPort()));
        } catch (IOException occupied) {
            throw new IOException("Ollama's private port is occupied. Choose a free port in aether-server.yml.");
        }
        Path executable = data.resolve(bundle.executable());
        DataDirectory.rejectLinks(executable);
        if (!Files.isRegularFile(executable)) { throw new IOException("Bundled Ollama executable is missing."); }
        Path models = data.resolve("models");
        DataDirectory.rejectLinks(models);
        Files.createDirectories(models);
        Path home = data.resolve("home");
        DataDirectory.rejectLinks(home);
        Files.createDirectories(home);
        ProcessBuilder builder = new ProcessBuilder(executable.toString(), "serve").directory(data.toFile());
        builder.environment().put("OLLAMA_HOST", "127.0.0.1:" + endpoint.getPort());
        builder.environment().put("OLLAMA_MODELS", models.toString());
        builder.environment().put("OLLAMA_NO_CLOUD", "1");
        // Ollama also initializes keys under its user home, independently of OLLAMA_MODELS.
        builder.environment().put("HOME", home.toString());
        builder.environment().put("USERPROFILE", home.toString());
        builder.environment().remove("AETHER_API_KEYS");
        builder.environment().remove("AETHER_BACKEND_API_KEY");
        builder.redirectErrorStream(true);
        Process process;
        try { process = starter.start(builder); }
        catch (IOException blocked) { throw new IOException("Host could not launch bundled Ollama. Native executables and compatible libraries are required."); }
        ManagedOllama owner = new ManagedOllama(process, endpoint);
        try {
            owner.captureDiagnostics(data.resolve("ollama-startup.log"));
            owner.waitForRuntime(Duration.ofMinutes(2));
            if (!owner.hasModel(bundle.sourceModel())) { throw new IOException("Ollama cannot find the bundled base model."); }
            JsonObject create = new JsonObject();
            create.addProperty("from", bundle.sourceModel());
            create.addProperty("model", bundle.targetModel());
            create.addProperty("system", "You are A.E.T.H.E.R., Wilderness Odyssey's expedition intelligence. Follow the gateway's supplied canon and specialist instructions.");
            create.addProperty("stream", false);
            JsonObject result = owner.request("/api/create", create, Duration.ofMinutes(3));
            if (!"success".equals(result.has("status") ? result.get("status").getAsString() : "")
                    || !owner.hasModel(bundle.targetModel())) {
                throw new IOException("Ollama could not register the bundled Aether model.");
            }
            // Keep cold model loading outside the first player's bounded chat request.
            System.getLogger("AetherLauncher").log(System.Logger.Level.INFO,
                    "Loading Aether into memory before opening the gateway; this may take several minutes.");
            JsonObject preload = new JsonObject();
            preload.addProperty("model", bundle.targetModel());
            preload.addProperty("stream", false);
            preload.addProperty("keep_alive", "1h");
            try {
                JsonObject loaded = owner.request("/api/generate", preload, Duration.ofMinutes(3));
                if (!loaded.has("done") || !loaded.get("done").getAsBoolean()) {
                    throw new IOException("Ollama did not finish loading the model.");
                }
            } catch (InterruptedException interrupted) { throw interrupted; }
            catch (Exception unavailable) {
                throw new IOException("Aether could not load within the startup allowance. Check host RAM/GPU resources and ollama-startup.log.");
            }
            return owner;
        } catch (Exception failure) { owner.close(); throw failure; }
    }

    private void waitForRuntime(Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!child.isAlive()) { throw new IOException("Bundled Ollama exited during startup. See ollama-startup.log."); }
            try { request("/api/tags", null, Duration.ofSeconds(2)); return; }
            catch (InterruptedException interrupted) { throw interrupted; }
            catch (Exception notYetReady) { Thread.sleep(200); }
        }
        throw new IOException("Bundled Ollama did not become ready within two minutes.");
    }

    private boolean hasModel(String model) throws Exception {
        JsonObject tags = request("/api/tags", null, Duration.ofSeconds(5));
        if (!tags.has("models") || !tags.get("models").isJsonArray()) { return false; }
        for (var value : tags.getAsJsonArray("models")) {
            if (value.isJsonObject() && value.getAsJsonObject().has("name")
                    && model.equals(value.getAsJsonObject().get("name").getAsString())) { return true; }
        }
        return false;
    }

    private JsonObject request(String path, JsonObject body, Duration timeout) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(endpoint + path)).timeout(timeout);
        if (body == null) { builder.GET(); }
        else { builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())); }
        var pending = http.sendAsync(builder.build(), ignored -> new JsonHttp.Body(1_048_576));
        try {
            HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) { throw new IOException("Ollama setup request failed."); }
            return JsonHttp.object(response.body());
        } finally { if (!pending.isDone()) { pending.cancel(true); } }
    }

    private void captureDiagnostics(Path log) throws IOException {
        DataDirectory.rejectLinks(log);
        var output = Files.newOutputStream(log);
        // Drain forever but retain at most 4 MiB of native startup diagnostics. Gateway keys are not inherited.
        Thread.ofPlatform().daemon().name("aether-ollama-output").start(() -> {
            try (output; var input = child.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count, retained = 0;
                while ((count = input.read(buffer)) != -1) {
                    int keep = Math.min(count, 4 * 1024 * 1024 - retained);
                    if (keep > 0) { output.write(buffer, 0, keep); output.flush(); retained += keep; }
                }
            } catch (IOException ignored) { /* Closing the owned process closes its pipe. */ }
        });
    }

    /** Reports whether this owner still has a running native child. */
    public boolean isAlive() { return !closed.get() && child.isAlive(); }

    /** Stops the process and its own descendants, never another user's/system Ollama process. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) { return; }
        http.shutdownNow();
        List<ProcessHandle> descendants = child.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        child.destroy();
        boolean interrupted = false;
        try { if (!child.waitFor(5, TimeUnit.SECONDS)) { child.destroyForcibly(); } }
        catch (InterruptedException stopping) { interrupted = true; child.destroyForcibly(); }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (interrupted) { Thread.currentThread().interrupt(); }
    }
}
