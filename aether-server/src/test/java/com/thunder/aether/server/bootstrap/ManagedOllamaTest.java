package com.thunder.aether.server.bootstrap;

import com.sun.net.httpserver.HttpServer;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.aether.server.api.JsonHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ManagedOllamaTest {
    @TempDir Path data;
    private BundleManifest bundle() throws IOException {
        Files.createDirectories(data.resolve("runtime"));
        Files.writeString(data.resolve("runtime/ollama"), "fixture");
        return new BundleManifest(1, BundleManifest.currentPlatform(), "0.17.7", "runtime/ollama",
                "llama3.1:8b", "aether-custom:8b",
                List.of(new BundleManifest.Asset("runtime/ollama", 7, "0".repeat(64))));
    }
    private ServerConfig config(int port) {
        return new ServerConfig("127.0.0.1", 0, 2, "http://127.0.0.1:" + port,
                "aether-custom:8b", 2, 128, 1, 1, 60, 65536, 2,
                false, false, false, "");
    }

    @Test void startsPrivateRuntimeCreatesApprovedModelAndStopsOnlyOwnedChild() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
        AtomicBoolean created = new AtomicBoolean();
        AtomicBoolean warmed = new AtomicBoolean();
        MockProcess[] child = new MockProcess[1];
        try (var runtime = ManagedOllama.launch(config(port), bundle(), data, builder -> {
            assertEquals(data.resolve("runtime/ollama").toString(), builder.command().getFirst());
            assertEquals("serve", builder.command().get(1));
            assertEquals("127.0.0.1:" + port, builder.environment().get("OLLAMA_HOST"));
            assertEquals(data.resolve("models").toString(), builder.environment().get("OLLAMA_MODELS"));
            assertEquals("1", builder.environment().get("OLLAMA_NO_CLOUD"));
            assertEquals(data.resolve("home").toString(), builder.environment().get("HOME"));
            assertEquals(data.resolve("home").toString(), builder.environment().get("USERPROFILE"));
            assertFalse(builder.environment().containsKey("AETHER_API_KEYS"));
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 4);
            http.createContext("/api/tags", exchange -> JsonHttp.send(exchange, 200, Map.of("models", List.of(
                    Map.of("name", "llama3.1:8b"), Map.of("name", "aether-custom:8b")))));
            http.createContext("/api/create", exchange -> {
                var request = JsonHttp.object(exchange.getRequestBody().readAllBytes());
                created.set("llama3.1:8b".equals(request.get("from").getAsString())
                        && "aether-custom:8b".equals(request.get("model").getAsString())
                        && !request.get("stream").getAsBoolean());
                JsonHttp.send(exchange, 200, Map.of("status", "success"));
            });
            http.createContext("/api/generate", exchange -> {
                var request = JsonHttp.object(exchange.getRequestBody().readAllBytes());
                warmed.set("aether-custom:8b".equals(request.get("model").getAsString())
                        && "1h".equals(request.get("keep_alive").getAsString())
                        && !request.has("prompt") && !request.get("stream").getAsBoolean());
                JsonHttp.send(exchange, 200, Map.of("done", true));
            });
            http.start();
            return child[0] = new MockProcess(http);
        })) {
            assertTrue(created.get());
            assertTrue(warmed.get(), "model must be loaded before the gateway starts");
            assertTrue(child[0].isAlive());
        }
        assertFalse(child[0].isAlive());
    }

    @Test void failedModelWarmupStopsTheOwnedProcessBeforeServing() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
        MockProcess[] child = new MockProcess[1];
        assertThrows(IOException.class, () -> ManagedOllama.launch(config(port), bundle(), data, builder -> {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 4);
            http.createContext("/api/tags", exchange -> JsonHttp.send(exchange, 200, Map.of("models", List.of(
                    Map.of("name", "llama3.1:8b"), Map.of("name", "aether-custom:8b")))));
            http.createContext("/api/create", exchange -> JsonHttp.send(exchange, 200, Map.of("status", "success")));
            http.createContext("/api/generate", exchange -> JsonHttp.send(exchange, 503, Map.of("error", "not enough memory")));
            http.start();
            return child[0] = new MockProcess(http);
        }));
        assertNotNull(child[0]);
        assertFalse(child[0].isAlive());
    }
    @Test void occupiedPortNeverStartsOrAdoptsAnUnrelatedProcess() throws Exception {
        AtomicBoolean launched = new AtomicBoolean();
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            assertThrows(IOException.class, () -> ManagedOllama.launch(config(socket.getLocalPort()),
                    bundle(), data, builder -> { launched.set(true); throw new IOException(); }));
            assertFalse(launched.get());
            assertFalse(socket.isClosed());
        }
    }

    @Test void remoteEndpointCannotBeUsedAsManagedRuntime() throws Exception {
        var original = config(11435);
        var remote = new ServerConfig(original.bind(), original.port(), 2, "http://example.com:11435",
                original.model(), 2, 128, 1, 1, 60, 65536, 2, false, false, false, "");
        assertThrows(IOException.class, () -> ManagedOllama.launch(remote, bundle(), data,
                builder -> { fail("must reject before process launch"); return null; }));
    }

    private static final class MockProcess extends Process {
        private final HttpServer http;
        private boolean alive = true;
        MockProcess(HttpServer http) { this.http = http; }
        public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        public InputStream getInputStream() { return InputStream.nullInputStream(); }
        public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        public int waitFor() { return 0; }
        public boolean waitFor(long timeout, TimeUnit unit) { return !alive; }
        public int exitValue() { if (alive) { throw new IllegalThreadStateException(); } return 0; }
        public boolean isAlive() { return alive; }
        public void destroy() { alive = false; http.stop(0); }
        public Process destroyForcibly() { destroy(); return this; }
        public Stream<ProcessHandle> descendants() { return Stream.empty(); }
    }
}
