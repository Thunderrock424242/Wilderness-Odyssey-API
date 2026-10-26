package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Installation-scoped public wire contract. Reported Minecraft identity never grants authority. */
public final class DiagnosticProtocol {
    public static final Gson JSON = new Gson();
    public static final int MAX_ARTIFACT_BYTES = 1536 * 1024;
    private static final Set<String> KINDS = Set.of("session", "log", "crash", "spark");

    private DiagnosticProtocol() { }

    public static URI origin(String value) {
        URI uri = URI.create(value.trim());
        String host = uri.getHost();
        boolean local = "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host);
        if (host == null || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath())) || uri.getPort() == 0) {
            throw new IllegalArgumentException("Use an HTTPS API origin without a path, query or credentials.");
        }
        return URI.create(value.trim().replaceAll("/$", ""));
    }

    public static Policy parsePolicy(String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            version(object);
            boolean enabled = bool(object, "enabled");
            if (!enabled) return new Policy(false, "", "", "", false, false, 0);
            int seconds = object.get("leaseSeconds").getAsBigDecimal().intValueExact();
            if (seconds < 1 || !object.get("leaseSeconds").getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            return new Policy(true, identifier(text(object, "campaignId")), identifier(text(object, "testerId")),
                    identifier(text(object, "collectionId")), bool(object, "captureLogs"), bool(object, "captureSpark"), Math.min(60, seconds));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid playtest policy.");
        }
    }

    public static void version(JsonObject object) {
        if (!"1.0".equals(text(object, "schemaVersion"))) throw new IllegalArgumentException("Unsupported diagnostic protocol.");
    }

    public static String text(JsonObject object, String name) {
        var value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid diagnostic field.");
        return value.getAsString();
    }

    private static boolean bool(JsonObject object, String name) {
        var value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
        return value.getAsBoolean();
    }

    public static String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid diagnostic identifier.");
        return value;
    }

    public record Policy(boolean enabled, String campaignId, String testerId, String collectionId, boolean captureLogs, boolean captureSpark, int leaseSeconds) { }

    /** Sensitive local credential; deliberately omits values from toString. */
    public record Enrollment(String origin, String installationId, String testerId, String campaignId, String accessToken) {
        public Enrollment {
            origin = DiagnosticProtocol.origin(origin).toString();
            UUID.fromString(installationId);
            identifier(testerId);
            identifier(campaignId);
            if (accessToken == null || !accessToken.matches("[A-Za-z0-9_.~-]{32,512}")) throw new IllegalArgumentException("Invalid installation credential.");
        }
        @Override public String toString() { return "Playtest enrollment [private]"; }
    }

    public record Artifact(String schemaVersion, String artifactId, String campaignId, String testerId, String collectionId,
                           String sessionId, String kind, String scope, String capturedAt,
                           String contentType, String encoding, String content) {
        public Artifact {
            if (!"1.0".equals(schemaVersion) || !KINDS.contains(kind) || !"client".equals(scope)) throw new IllegalArgumentException("Invalid artifact.");
            UUID.fromString(artifactId);
            identifier(sessionId);
            identifier(campaignId);
            identifier(testerId);
            identifier(collectionId);
            Instant.parse(capturedAt);
            if (content == null || content.length() > MAX_ARTIFACT_BYTES) throw new IllegalArgumentException("Artifact exceeds limit.");
            if ("spark".equals(kind)) {
                if (!"base64".equals(encoding) || !"application/x-spark-sampler".equals(contentType)
                        || java.util.Base64.getDecoder().decode(content).length > 1024 * 1024) throw new IllegalArgumentException("Invalid Spark artifact.");
            } else if (!"utf8".equals(encoding) || !("text/plain".equals(contentType) || "application/json".equals(contentType))
                    || content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256 * 1024) throw new IllegalArgumentException("Invalid text artifact.");
        }
    }
}
