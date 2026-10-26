package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

/** Fixed-origin, bounded worker-only transport; no redirected credentials or arbitrary upload destinations. */
public final class DiagnosticHttp implements AutoCloseable {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public DiagnosticProtocol.Enrollment enroll(String origin, String installationId, String invitation) throws IOException, InterruptedException {
        if (invitation == null || !invitation.matches("[A-Za-z0-9_-]{16,256}")) throw new IllegalArgumentException("Use the invitation code from your playtest invitation.");
        java.util.UUID.fromString(installationId);
        JsonObject body = new JsonObject();
        body.addProperty("schemaVersion", "1.0");
        body.addProperty("installationId", installationId);
        body.addProperty("invitation", invitation);
        body.addProperty("consentVersion", "1");
        var response = send(origin, "/enroll", null, body.toString());
        DiagnosticProtocol.version(response);
        return new DiagnosticProtocol.Enrollment(origin, installationId, DiagnosticProtocol.text(response, "testerId"),
                DiagnosticProtocol.text(response, "campaignId"), DiagnosticProtocol.text(response, "accessToken"));
    }

    public DiagnosticProtocol.Policy policy(DiagnosticProtocol.Enrollment enrollment) throws IOException, InterruptedException {
        return DiagnosticProtocol.parsePolicy(send(enrollment.origin(), "/policy", enrollment.accessToken(), null).toString());
    }

    public boolean upload(DiagnosticProtocol.Enrollment enrollment, DiagnosticProtocol.Artifact artifact) throws IOException, InterruptedException {
        if (!artifact.campaignId().equals(enrollment.campaignId()) || !artifact.testerId().equals(enrollment.testerId())) throw new IllegalArgumentException("Artifact ownership mismatch.");
        JsonObject response = send(enrollment.origin(), "/artifacts", enrollment.accessToken(), DiagnosticProtocol.JSON.toJson(artifact));
        DiagnosticProtocol.version(response);
        var accepted = response.get("accepted");
        return accepted != null && accepted.isJsonPrimitive() && accepted.getAsJsonPrimitive().isBoolean()
                && accepted.getAsBoolean() && artifact.artifactId().equals(DiagnosticProtocol.text(response, "artifactId"));
    }

    private JsonObject send(String origin, String path, String token, String body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(DiagnosticProtocol.origin(origin).resolve("/api/playtest/v1" + path))
                .timeout(Duration.ofSeconds(5)).header("Accept", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.GET();
        else {
            if (body.getBytes(StandardCharsets.UTF_8).length > DiagnosticProtocol.MAX_ARTIFACT_BYTES) throw new IOException("Diagnostic request too large.");
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = client.send(request.build(), PlaytestWebhookClient.responseBodyHandler());
        int status = response.statusCode();
        if (status == 401 || status == 403 || status == 410) throw new Revoked();
        if (status / 100 != 2) throw new IOException("Diagnostic service unavailable (HTTP " + status + ").");
        try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException error) { throw new IOException("Invalid diagnostic response."); }
    }

    public static final class Revoked extends IOException {
        public Revoked() { super("Playtest permission has ended."); }
    }

    @Override public void close() { client.shutdownNow(); }
}
