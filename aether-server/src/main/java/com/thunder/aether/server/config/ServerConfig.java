package com.thunder.aether.server.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Validated operator-only configuration, usable without Minecraft or mod loaders. */
public record ServerConfig(String bind, int port, int requestTimeoutSeconds, String ollamaUrl,
        String model, int timeoutSeconds, int maxOutputTokens,
        int concurrency, int queueSize, int requestsPerMinute, int maxRequestBytes, int httpWorkers,
        boolean logRequests, boolean logPlayerMessages, boolean logResponses, String promptsFile) {
    public ServerConfig {
        require(bind != null && !bind.isBlank() && bind.length() <= 255);
        range(port, 0, 65535); range(requestTimeoutSeconds, 1, 60);
        range(timeoutSeconds, 1, 120); range(maxOutputTokens, 32, 1024);
        range(concurrency, 1, 16); range(queueSize, 0, 256);
        range(requestsPerMinute, 1, 6000); range(maxRequestBytes, 1024, 262144);
        range(httpWorkers, 2, 32);
        require(model != null && model.matches("[A-Za-z0-9._/:@-]{1,128}"));
        com.thunder.aether.server.api.JsonHttp.baseUri(ollamaUrl);
        promptsFile = promptsFile == null ? "" : promptsFile;
    }

    /** Loads public-service settings; obsolete key fields and environment credentials are ignored. */
    public static ServerConfig load(Path file, Map<String, String> environment) throws Exception {
        Map<?, ?> root;
        try (InputStream input = file == null
                ? ServerConfig.class.getResourceAsStream("/aether-server.yml") : Files.newInputStream(file)) {
            root = yaml(input);
        }
        Map<?, ?> server = section(root, "server"), ollama = section(root, "ollama"),
                limits = section(root, "limits"), logging = section(root, "logging");
        return new ServerConfig(text(server,"bind","127.0.0.1"), number(server,"port",8085),
                number(server,"request_timeout_seconds",10), text(ollama,"url","http://127.0.0.1:11434"),
                text(ollama,"model","aether-custom:8b"), number(ollama,"timeout_seconds",30),
                number(ollama,"max_output_tokens",256), number(limits,"max_concurrent_generations",2),
                number(limits,"max_queue_size",20), requestLimit(limits),
                number(limits,"max_request_bytes",65536), number(limits,"http_workers",8),
                flag(logging,"log_requests"), flag(logging,"log_player_messages"), flag(logging,"log_responses"),
                text(root,"prompts_file",""));
    }

    /** Safe YAML data loader shared by configuration and the server-owned prompt catalog. */
    public static Map<?, ?> yaml(InputStream input) {
        require(input != null);
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setCodePointLimit(1_048_576);
        Object parsed = new Yaml(new SafeConstructor(options)).load(input);
        require(parsed instanceof Map<?, ?>);
        return (Map<?, ?>) parsed;
    }

    private static Map<?, ?> section(Map<?, ?> root, String key) {
        Object value = root.get(key);
        return value instanceof Map<?, ?> map ? map : Map.of();
    }
    // Older limits remain effective, now shared by the public service instead of credentials.
    private static int requestLimit(Map<?, ?> limits) {
        return limits.containsKey("requests_per_minute") ? number(limits,"requests_per_minute",60)
                : number(limits,"requests_per_minute_per_server",60);
    }
    private static int number(Map<?, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value == null) { return fallback; }
        require(value instanceof Number);
        double number = ((Number) value).doubleValue();
        require(Double.isFinite(number) && number == Math.rint(number) && number >= 0 && number <= Integer.MAX_VALUE);
        return (int) number;
    }
    private static String text(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        if (value == null) { return fallback; }
        require(value instanceof String);
        return (String) value;
    }
    private static boolean flag(Map<?, ?> values, String key) {
        Object value = values.get(key);
        require(value == null || value instanceof Boolean);
        return Boolean.TRUE.equals(value);
    }
    private static void range(int value, int low, int high) { require(value >= low && value <= high); }
    private static void require(boolean valid) {
        if (!valid) { throw new IllegalArgumentException("INVALID_CONFIGURATION"); }
    }
    @Override public String toString() { return "ServerConfig[endpoints redacted]"; }
}
