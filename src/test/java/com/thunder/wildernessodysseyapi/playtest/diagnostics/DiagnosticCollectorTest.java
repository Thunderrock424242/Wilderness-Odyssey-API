package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticCollectorTest {
    @TempDir Path directory;
    private static final DiagnosticCollector.Snapshot PLAYING = new DiagnosticCollector.Snapshot(true, "singleplayer", "Tester", "reported-uuid", "4.2.0");

    @Test
    void retriesAmbiguousEnrollmentWithTheSameInstallationAndClearsPendingIdentityOnSuccess() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            backend.loseEnrollmentResponse.set(true);
            assertThrows(java.io.IOException.class, () -> collector.enroll(backend.origin(), "invitation-12345678"));
            assertFalse(collector.enrolled());
            backend.loseEnrollmentResponse.set(false);
            collector.enroll(backend.origin() + "/", "invitation-12345678");
            assertEquals(backend.enrollments.getFirst().get("installationId"), backend.enrollments.getLast().get("installationId"));
            assertTrue(collector.enrolled());
            assertFalse(Files.exists(directory.resolve("pending-enrollment.json")));
            collector.closeTransport();
        }
    }

    @Test
    void ambiguousEnrollmentIdentitySurvivesRestartWithoutPersistingTheInvitationOrCredential() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            backend.loseEnrollmentResponse.set(true);
            assertThrows(java.io.IOException.class, () -> collector.enroll(backend.origin(), "invitation-12345678"));
            String pending = Files.readString(directory.resolve("pending-enrollment.json"));
            assertFalse(pending.contains("invitation-12345678"));
            assertFalse(pending.contains("a".repeat(40)));
            assertFalse(pending.contains("accessToken"));
            assertFalse(Files.exists(directory.resolve("enrollment.json")));
            collector.closeTransport();
            var restarted = backend.collector(directory);
            assertFalse(restarted.enrolled());
            backend.loseEnrollmentResponse.set(false);
            restarted.enroll(backend.origin(), "invitation-12345678");
            assertEquals(backend.enrollments.getFirst().get("installationId"), backend.enrollments.getLast().get("installationId"));
            assertFalse(Files.exists(directory.resolve("pending-enrollment.json")));
            restarted.closeTransport();
        }
    }

    @Test
    void explicitlyChangingInvitationOrOriginStartsASeparateEnrollmentIdentity() throws Exception {
        try (var backend = new Backend(); var other = new Backend()) {
            var collector = backend.collector(directory);
            backend.loseEnrollmentResponse.set(true);
            other.loseEnrollmentResponse.set(true);
            assertThrows(java.io.IOException.class, () -> collector.enroll(backend.origin(), "invitation-12345678"));
            assertThrows(java.io.IOException.class, () -> collector.enroll(backend.origin(), "invitation-87654321"));
            assertNotEquals(backend.enrollments.getFirst().get("installationId"), backend.enrollments.getLast().get("installationId"));
            assertThrows(java.io.IOException.class, () -> collector.enroll(other.origin(), "invitation-87654321"));
            assertNotEquals(backend.enrollments.getLast().get("installationId"), other.enrollments.getFirst().get("installationId"));
            collector.closeTransport();
        }
    }

    @Test
    void localWithdrawalClearsAmbiguousRetryIdentityAndCancelsLateEnrollment() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            backend.delayEnrollment.set(true);
            var request = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { collector.enroll(backend.origin(), "invitation-12345678"); }
                catch (java.io.IOException | InterruptedException error) { throw new java.util.concurrent.CompletionException(error); }
            });
            assertTrue(backend.enrollmentArrived.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(Files.exists(directory.resolve("pending-enrollment.json")));
            collector.localStop();
            collector.eraseStopped();
            assertFalse(Files.exists(directory.resolve("pending-enrollment.json")));
            backend.releaseEnrollment.countDown();
            request.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertFalse(collector.enrolled());
            assertFalse(Files.exists(directory.resolve("enrollment.json")));
            assertFalse(Files.exists(directory.resolve("pending-enrollment.json")));
            collector.closeTransport();
            var restarted = backend.collector(directory);
            restarted.enroll(backend.origin(), "invitation-12345678");
            assertNotEquals(backend.enrollments.getFirst().get("installationId"), backend.enrollments.getLast().get("installationId"));
            restarted.closeTransport();
        }
    }

    @Test
    void invalidPendingIdentityDoesNotInvalidateAnExistingCredential() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            collector.enroll(backend.origin(), "invitation-12345678");
            collector.closeTransport();
            String credential = Files.readString(directory.resolve("enrollment.json"));
            Files.writeString(directory.resolve("pending-enrollment.json"), "{invalid");
            var restarted = backend.collector(directory);
            assertTrue(restarted.enrolled());
            assertEquals(credential, Files.readString(directory.resolve("enrollment.json")));
            restarted.closeTransport();
        }
    }

    @Test
    void invalidOrOversizedRetryStateCannotSupplyAnInstallationIdentity() throws Exception {
        try (var backend = new Backend()) {
            backend.loseEnrollmentResponse.set(true);
            var collector = backend.collector(directory);
            assertThrows(java.io.IOException.class, () -> collector.enroll(backend.origin(), "invitation-12345678"));
            collector.closeTransport();
            Path pendingFile = directory.resolve("pending-enrollment.json");
            String valid = Files.readString(pendingFile);
            String firstId = backend.enrollments.getFirst().get("installationId").getAsString();
            JsonObject wrongSchema = JsonParser.parseString(valid).getAsJsonObject();
            wrongSchema.addProperty("schemaVersion", "9.0");
            JsonObject wrongId = JsonParser.parseString(valid).getAsJsonObject();
            wrongId.addProperty("installationId", "invalid");
            JsonObject wrongDigest = JsonParser.parseString(valid).getAsJsonObject();
            wrongDigest.addProperty("invitationSha256", "invalid");
            for (String invalid : List.of("{invalid", valid + " ".repeat(5000), wrongSchema.toString(), wrongId.toString(), wrongDigest.toString())) {
                Files.writeString(pendingFile, invalid);
                var restarted = backend.collector(directory);
                assertFalse(restarted.enrolled());
                assertThrows(java.io.IOException.class, () -> restarted.enroll(backend.origin(), "invitation-12345678"));
                String nextId = backend.enrollments.getLast().get("installationId").getAsString();
                assertNotEquals(firstId, nextId);
                assertEquals(nextId, java.util.UUID.fromString(nextId).toString());
                restarted.closeTransport();
            }
        }
    }

    @Test
    void enrollmentDoesNotContactBackendWhenTheRetryIdentityCannotBePersisted() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            Files.createDirectory(directory.resolve("pending-enrollment.json"));
            assertThrows(java.io.IOException.class, () -> collector.enroll(backend.origin(), "invitation-12345678"));
            assertTrue(backend.enrollments.isEmpty());
            assertFalse(collector.enrolled());
            collector.closeTransport();
        }
    }

    @Test
    void successfulResponseKeepsRetryIdentityUntilCredentialPersistenceSucceeds() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            Path credentialFile = directory.resolve("enrollment.json");
            Files.createDirectory(credentialFile);
            assertThrows(java.io.IOException.class, () -> collector.enroll(backend.origin(), "invitation-12345678"));
            assertTrue(Files.exists(directory.resolve("pending-enrollment.json")));
            assertFalse(collector.enrolled());
            Files.delete(credentialFile);
            collector.enroll(backend.origin(), "invitation-12345678");
            assertEquals(backend.enrollments.getFirst().get("installationId"), backend.enrollments.getLast().get("installationId"));
            assertFalse(Files.exists(directory.resolve("pending-enrollment.json")));
            collector.closeTransport();
        }
    }

    @Test
    void uploadsLogsSeparatelyWithoutDuplicatingLogContentIntoSessionDetailsAndStopsOnOff() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            collector.enroll(backend.origin(), "invitation-12345678");
            collector.cycle(PLAYING);
            assertTrue(collector.recording());
            collector.append("ERROR", "example", "distinct diagnostic message");
            collector.cycle(PLAYING);
            collector.disconnected();
            assertTrue(backend.artifacts.stream().anyMatch(item -> item.get("kind").getAsString().equals("log") && item.get("content").getAsString().contains("distinct diagnostic message")));
            assertFalse(backend.artifacts.stream().filter(item -> item.get("kind").getAsString().equals("session"))
                    .anyMatch(item -> item.get("content").getAsString().contains("distinct diagnostic message")));
            backend.enabled.set(false);
            int uploaded = backend.artifacts.size();
            collector.cycle(PLAYING);
            collector.append("ERROR", "example", "after stop");
            collector.disconnected();
            assertFalse(collector.recording());
            assertEquals(uploaded, backend.artifacts.size());
            assertFalse(Files.exists(directory.resolve("active-session.json")));
        }
    }

    @Test
    void recoversInterruptedEvidenceOnlyAfterMatchingPermissionAndLocalStopErasesCredential() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            collector.enroll(backend.origin(), "invitation-12345678");
            collector.cycle(PLAYING);
            collector.append("ERROR", "example", "saved before interruption");
            collector.cycle(PLAYING);
            collector = backend.collector(directory);
            assertFalse(collector.recording());
            collector.cycle(DiagnosticCollector.Snapshot.menu());
            assertTrue(backend.artifacts.stream().anyMatch(item -> item.get("kind").getAsString().equals("crash") && item.get("content").getAsString().contains("saved before interruption")));
            collector.localStop();
            collector.eraseStopped();
            assertFalse(Files.exists(directory.resolve("enrollment.json")));
            assertFalse(backend.collector(directory).enrolled());
        }
    }

    @Test
    void categoryOffRemovesSavedLogTailBeforeItCanBeRecovered() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            collector.enroll(backend.origin(), "invitation-12345678");
            collector.cycle(PLAYING);
            collector.append("ERROR", "example", "private saved tail");
            collector.cycle(PLAYING);
            backend.logs.set(false);
            collector.cycle(PLAYING);
            assertFalse(Files.readString(directory.resolve("active-session.json")).contains("private saved tail"));
        }
    }

    @Test
    void heartbeatKeepsLogsSeparateAndExpiredCaptureStartsAFreshSession() throws Exception {
        try (var backend = new Backend()) {
            var clock = new java.util.concurrent.atomic.AtomicLong(1);
            var collector = new DiagnosticCollector(directory, new DiagnosticHttp(), backend.noSpark(), clock::get);
            collector.enroll(backend.origin(), "invitation-12345678");
            collector.cycle(PLAYING);
            String firstSession = backend.artifacts.getFirst().get("sessionId").getAsString();
            collector.append("ERROR", "example", "do not mix into session details");
            clock.addAndGet(31_000_000_000L);
            collector.cycle(PLAYING);
            assertFalse(backend.artifacts.stream().filter(item -> item.get("kind").getAsString().equals("session"))
                    .anyMatch(item -> item.get("content").getAsString().contains("do not mix")));
            clock.addAndGet(61_000_000_000L);
            collector.expire();
            assertFalse(collector.recording());
            collector.cycle(PLAYING);
            assertTrue(collector.recording());
            assertTrue(backend.artifacts.stream().anyMatch(item -> !firstSession.equals(item.get("sessionId").getAsString())));
        }
    }

    @Test
    void lateEnabledPolicyCannotRestartAfterLocalWithdrawal() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            collector.enroll(backend.origin(), "invitation-12345678");
            backend.delayPolicy.set(true);
            var request = java.util.concurrent.CompletableFuture.runAsync(() -> collector.cycle(PLAYING));
            assertTrue(backend.policyArrived.await(5, java.util.concurrent.TimeUnit.SECONDS));
            collector.localStop();
            backend.releasePolicy.countDown();
            request.get(5, java.util.concurrent.TimeUnit.SECONDS);
            collector.eraseStopped();
            assertFalse(collector.enrolled());
            assertFalse(collector.recording());
            assertTrue(backend.artifacts.isEmpty());
            assertFalse(backend.collector(directory).enrolled());
        }
    }

    @Test
    void newCollectionPeriodDiscardsPendingDataFromBeforeTheWebsiteStop() throws Exception {
        try (var backend = new Backend()) {
            var collector = backend.collector(directory);
            collector.enroll(backend.origin(), "invitation-12345678");
            collector.cycle(PLAYING);
            collector.append("ERROR", "example", "old collection data");
            backend.rejectUploads.set(true);
            collector.cycle(PLAYING);
            assertFalse(new DiagnosticStore(directory.resolve("pending")).pending().isEmpty());
            backend.collection.set("collection-2");
            backend.rejectUploads.set(false);
            int before = backend.artifacts.size();
            collector.cycle(PLAYING);
            assertTrue(backend.artifacts.size() > before);
            assertTrue(backend.artifacts.subList(before, backend.artifacts.size()).stream()
                    .allMatch(item -> item.get("collectionId").getAsString().equals("collection-2")));
            assertFalse(backend.artifacts.subList(before, backend.artifacts.size()).stream()
                    .anyMatch(item -> item.get("content").getAsString().contains("old collection data")));
        }
    }

    private static final class Backend implements AutoCloseable {
        final HttpServer server;
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final AtomicBoolean logs = new AtomicBoolean(true);
        final AtomicBoolean delayPolicy = new AtomicBoolean(false);
        final AtomicBoolean rejectUploads = new AtomicBoolean(false);
        final AtomicBoolean loseEnrollmentResponse = new AtomicBoolean(false);
        final AtomicBoolean delayEnrollment = new AtomicBoolean(false);
        final java.util.concurrent.CountDownLatch enrollmentArrived = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch releaseEnrollment = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<String> collection = new java.util.concurrent.atomic.AtomicReference<>("collection-1");
        final java.util.concurrent.CountDownLatch policyArrived = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch releasePolicy = new java.util.concurrent.CountDownLatch(1);
        final List<JsonObject> artifacts = new CopyOnWriteArrayList<>();
        final List<JsonObject> enrollments = new CopyOnWriteArrayList<>();
        Backend() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/playtest/v1/enroll", exchange -> {
                enrollments.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                if (delayEnrollment.get()) {
                    enrollmentArrived.countDown();
                    try { releaseEnrollment.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
                if (loseEnrollmentResponse.get()) { respond(exchange, "{"); return; }
                respond(exchange, "{\"schemaVersion\":\"1.0\",\"testerId\":\"tester-1\",\"campaignId\":\"campaign-1\",\"accessToken\":\"" + "a".repeat(40) + "\"}");
            });
            server.createContext("/api/playtest/v1/policy", exchange -> {
                if (delayPolicy.get()) {
                    policyArrived.countDown();
                    try { releasePolicy.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
                respond(exchange, "{\"schemaVersion\":\"1.0\",\"enabled\":" + enabled.get() + ",\"testerId\":\"tester-1\",\"campaignId\":\"campaign-1\",\"collectionId\":\"" + collection.get() + "\",\"captureLogs\":" + logs.get() + ",\"captureSpark\":false,\"leaseSeconds\":60}");
            });
            server.createContext("/api/playtest/v1/artifacts", exchange -> {
                JsonObject artifact = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (rejectUploads.get()) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
                artifacts.add(artifact);
                respond(exchange, "{\"schemaVersion\":\"1.0\",\"accepted\":true,\"artifactId\":\"" + artifact.get("artifactId").getAsString() + "\"}");
            });
            server.start();
        }
        String origin() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        DiagnosticCollector collector(Path path) throws Exception {
            return new DiagnosticCollector(path, new DiagnosticHttp(), noSpark());
        }
        DiagnosticCollector.SparkCapture noSpark() {
            return new DiagnosticCollector.SparkCapture() {
                public void start() { }
                public byte[] finish() { return null; }
                public void cancel() { }
                public String status() { return "unavailable"; }
            };
        }
        private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }
        @Override public void close() { server.stop(0); }
    }
}
