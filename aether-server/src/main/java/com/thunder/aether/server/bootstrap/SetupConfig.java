package com.thunder.aether.server.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

/** Creates first-launch settings once; operator changes survive every later boot. */
public final class SetupConfig {
    private SetupConfig() {}

    /** Creates complete defaults for the public gateway once; callers hold the data-directory lock. */
    public static Path ensure(Path directory, BundleManifest manifest, Map<String, String> environment) throws IOException {
        Path config = directory.resolve("aether-server.yml");
        DataDirectory.rejectLinks(config);
        if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("Configuration path is not a file."); }
            return config;
        }
        String portValue = environment.getOrDefault("SERVER_PORT", environment.getOrDefault("PORT", "8085"));
        int port;
        try { port = Integer.parseInt(portValue); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Hosting port must be an integer."); }
        if (port < 1 || port > 65535 || port == 11435) { throw new IllegalArgumentException("Hosting port must be 1-65535 and distinct from Ollama's private port."); }
        String contents = """
                # First-launch settings for the public Aether service. No access key is required.
                # Public deployments must provide HTTPS through the hosting proxy.
                server:
                  bind: "0.0.0.0"
                  port: %d
                  request_timeout_seconds: 10
                ollama:
                  url: "http://127.0.0.1:11435"
                  model: "%s"
                  timeout_seconds: 30
                  max_output_tokens: 256
                limits:
                  max_concurrent_generations: 2
                  max_queue_size: 20
                  requests_per_minute: 60
                  max_request_bytes: 65536
                  http_workers: 8
                logging:
                  log_requests: false
                  log_player_messages: false
                  log_responses: false
                prompts_file: ""
                """.formatted(port, manifest.targetModel());
        Files.createDirectories(directory);
        Path pending = directory.resolve(".aether-config.pending");
        DataDirectory.rejectLinks(pending);
        Files.deleteIfExists(pending);
        try {
            if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                Files.createFile(pending, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else { Files.createFile(pending); }
            Files.writeString(pending, contents);
            // Same-directory publication of a complete file, without replacing operator configuration.
            Files.move(pending, config);
        } finally { Files.deleteIfExists(pending); }
        return config;
    }
}
