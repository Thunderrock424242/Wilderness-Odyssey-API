package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Client installation state machine. Blocking methods run on the single feature I/O worker. */
public final class DiagnosticCollector {
    public interface SparkCapture {
        void start();
        byte[] finish();
        void cancel();
        String status();
    }

    /** Immutable game-thread snapshot. No live player, world or connection crosses into I/O. */
    public record Snapshot(boolean playing, String mode, String playerName, String minecraftUuid, String modVersion) {
        public static Snapshot menu() { return new Snapshot(false, "menu", "", "", ""); }
    }

    private final Path root;
    private final DiagnosticHttp http;
    private final DiagnosticStore store;
    private final DiagnosticLease lease = new DiagnosticLease();
    private final DiagnosticLogBuffer logs = new DiagnosticLogBuffer(256 * 1024);
    private final SparkCapture spark;
    private volatile DiagnosticProtocol.Enrollment enrollment;
    private volatile String status = "Not enrolled";
    private volatile String sessionId;
    private JsonObject session;
    private JsonObject recovery;
    private long sparkStarted;
    private long snapshotAt;
    private boolean logEnabled;
    private boolean sparkEnabled;
    private long sessionStarted;
    private final java.util.function.LongSupplier clock;
    private volatile boolean expired;
    private volatile boolean cleanupNeeded;

    public DiagnosticCollector(Path root, DiagnosticHttp http, SparkCapture spark) throws IOException {
        this(root, http, spark, System::nanoTime);
    }

    DiagnosticCollector(Path root, DiagnosticHttp http, SparkCapture spark, java.util.function.LongSupplier clock) throws IOException {
        this.root = root;
        this.http = http;
        this.spark = spark;
        this.clock = clock;
        this.store = new DiagnosticStore(root.resolve("pending"));
        try {
            JsonObject saved = read("enrollment.json", 8192);
            if (saved != null && saved.has("consentVersion") && "1".equals(saved.get("consentVersion").getAsString())) {
                var candidate = DiagnosticProtocol.JSON.fromJson(saved.get("enrollment"), DiagnosticProtocol.Enrollment.class);
                if (candidate != null) { enrollment = candidate; status = "Waiting for playtest permission"; }
            }
            recovery = read("active-session.json", 128 * 1024);
        } catch (RuntimeException error) { enrollment = null; status = "Enrollment needs attention"; }
        if (enrollment == null) store.discard();
    }

    public String status() {
        if (enrollment != null && sessionId != null && !lease.active(clock.getAsLong())) return "Recording stopped; permission expired";
        return status;
    }
    public String origin() { var current = enrollment; return current == null ? "" : current.origin(); }
    public String sparkStatus() { return spark.status(); }
    public long dropped() { return store.dropped(); }
    public boolean enrolled() { return enrollment != null; }
    public boolean recording() { return sessionId != null && lease.active(clock.getAsLong()); }

    public void enroll(String origin, String invitation) throws IOException, InterruptedException {
        long generation = lease.generation();
        var redeemed = http.enroll(origin, UUID.randomUUID().toString(), invitation);
        if (lease.generation() != generation) return;
        stopCapture();
        store.discard();
        JsonObject saved = new JsonObject();
        saved.addProperty("consentVersion", "1");
        saved.add("enrollment", DiagnosticProtocol.JSON.toJsonTree(redeemed));
        DiagnosticStore.write(root.resolve("enrollment.json"), saved.toString());
        // A concurrent local stop must win both in memory and on disk.
        if (lease.generation() != generation) { Files.deleteIfExists(root.resolve("enrollment.json")); return; }
        synchronized (lease) {
            if (lease.generation() != generation) { Files.deleteIfExists(root.resolve("enrollment.json")); return; }
            enrollment = redeemed;
            cleanupNeeded = false;
            status = "Enrolled; waiting for playtest permission";
        }
    }

    /** Immediate no-I/O stop, safe from the UI or independent expiry watchdog. */
    public void localStop() {
        synchronized (lease) {
            lease.stop();
            enrollment = null;
            cleanupNeeded = true;
            status = "Stopped locally";
        }
        logs.clear();
    }

    /** Worker cleanup after localStop; credentials never survive a completed withdrawal. */
    public void eraseStopped() throws IOException {
        if (enrollment != null) return;
        stopCapture();
        store.discard();
        Files.deleteIfExists(root.resolve("enrollment.json"));
        Files.deleteIfExists(root.resolve("active-session.json"));
        recovery = null;
        cleanupNeeded = false;
    }

    public void append(String level, String logger, String message) {
        var policy = lease.policy();
        if (sessionId != null && lease.active(clock.getAsLong()) && policy != null && policy.captureLogs()) logs.append(level, logger, message);
    }

    /** No disk/network work; cancellation must still run while the client is stalled. */
    public void expire() {
        if (!lease.active(clock.getAsLong())) {
            if (sessionId != null) expired = true;
            logs.clear();
            spark.cancel();
        }
    }

    /** One bounded poll/capture/delivery pass. A transient request failure never renews authorization. */
    public void cycle(Snapshot snapshot) {
        var current = enrollment;
        if (current == null) {
            if (cleanupNeeded) try { eraseStopped(); } catch (IOException ignored) { status = "Stopped; local cleanup will retry"; }
            return;
        }
        long generation = lease.generation();
        try {
            if (expired || (sessionId != null && !lease.active(clock.getAsLong()))) { discard(); expired = false; }
            var policy = http.policy(current);
            if (!same(generation, current)) return;
            if (policy.enabled() && (!current.campaignId().equals(policy.campaignId()) || !current.testerId().equals(policy.testerId()))) throw new DiagnosticHttp.Revoked();
            var previous = lease.policy();
            if (policy.enabled() && previous != null && !previous.collectionId().equals(policy.collectionId())) discard();
            if (!lease.accept(generation, policy, clock.getAsLong())) { discard(); status = "Collection is off"; return; }
            recover(current);
            // A policy can disable individual categories without ending the campaign.
            if (!policy.captureLogs() && logEnabled) {
                logs.clear(); removeKind("log"); removeKind("crash");
                if (session != null) session.remove("lastCapturedLog");
            }
            if (!policy.captureSpark() && sparkEnabled) { spark.cancel(); removeKind("spark"); }
            logEnabled = policy.captureLogs();
            sparkEnabled = policy.captureSpark();
            if (snapshot.playing() && sessionId == null) begin(snapshot, current);
            if (sessionId != null) {
                captureLogs(current);
                if (sparkEnabled && snapshot.playing()) {
                    long now = clock.getAsLong();
                    if (sparkStarted == 0 || now - sparkStarted >= java.util.concurrent.TimeUnit.SECONDS.toNanos(120)) {
                        if (sparkStarted != 0) finishSpark(current);
                        if (authorized(current)) { spark.start(); sparkStarted = now; }
                    }
                }
                if (!snapshot.playing()) finish(current, "ended");
                else {
                    session.addProperty("sparkStatus", spark.status());
                    DiagnosticStore.write(root.resolve("active-session.json"), session.toString());
                    // Acknowledgements make the dashboard distinguish desired state from observed recording.
                    if (clock.getAsLong() - snapshotAt >= java.util.concurrent.TimeUnit.SECONDS.toNanos(30)) {
                        enqueueSession(current);
                        snapshotAt = clock.getAsLong();
                    }
                    status = "Recording playtest diagnostics";
                }
            }
            deliver(current, generation);
        } catch (DiagnosticHttp.Revoked revoked) {
            localStop();
            try { eraseStopped(); } catch (IOException ignored) { }
            status = "Playtest invitation was revoked or expired";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            expire();
        } catch (IOException | RuntimeException error) {
            expire();
            if (!lease.active(clock.getAsLong())) {
                try { discard(); } catch (IOException ignored) { }
            }
            status = "Service unavailable; " + (recording() ? "permission expires shortly" : "recording stopped");
        }
    }

    /** Finalize without waiting for another policy poll; still requires an unexpired authorization. */
    public void disconnected() {
        var current = enrollment;
        if (current == null) return;
        long generation = lease.generation();
        try {
            if (authorized(current) && sessionId != null) {
                captureLogs(current);
                finish(current, "ended");
                deliver(current, generation);
            } else discard();
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (IOException | RuntimeException error) { status = "Session saved; upload pending"; }
    }

    private void begin(Snapshot snapshot, DiagnosticProtocol.Enrollment current) throws IOException {
        sessionId = UUID.randomUUID().toString();
        sessionStarted = clock.getAsLong();
        session = new JsonObject();
        session.addProperty("sessionId", sessionId);
        session.addProperty("campaignId", current.campaignId());
        session.addProperty("testerId", current.testerId());
        session.addProperty("collectionId", lease.policy().collectionId());
        session.addProperty("startedAt", Instant.now().toString());
        session.addProperty("state", "recording");
        session.addProperty("mode", snapshot.mode());
        session.addProperty("playerName", snapshot.playerName());
        session.addProperty("reportedMinecraftUuid", snapshot.minecraftUuid());
        session.addProperty("minecraftVersion", "1.21.1");
        session.addProperty("modVersion", snapshot.modVersion());
        session.addProperty("sparkStatus", spark.status());
        DiagnosticStore.write(root.resolve("active-session.json"), session.toString());
        enqueueSession(current);
        snapshotAt = clock.getAsLong();
    }

    private void captureLogs(DiagnosticProtocol.Enrollment current) throws IOException {
        String text = logs.drain();
        if (logEnabled && !text.isEmpty() && authorized(current)) {
            enqueue(current, "log", "text/plain", "utf8", text);
            session.addProperty("lastCapturedLog", text.substring(Math.max(0, text.length() - 16000)));
        }
    }

    private void finishSpark(DiagnosticProtocol.Enrollment current) throws IOException {
        byte[] data = spark.finish();
        sparkStarted = 0;
        if (data != null && authorized(current) && lease.policy().captureSpark()) enqueue(current, "spark", "application/x-spark-sampler", "base64", Base64.getEncoder().encodeToString(data));
    }

    private void finish(DiagnosticProtocol.Enrollment current, String state) throws IOException {
        if (sparkEnabled) finishSpark(current);
        else spark.cancel();
        session.addProperty("state", state);
        session.addProperty("endedAt", Instant.now().toString());
        session.addProperty("sparkStatus", spark.status());
        session.remove("lastCapturedLog");
        enqueueSession(current);
        Files.deleteIfExists(root.resolve("active-session.json"));
        sessionId = null;
        session = null;
        status = "Session ended; waiting for next game";
    }

    private void recover(DiagnosticProtocol.Enrollment current) throws IOException {
        if (recovery == null) return;
        if (current.campaignId().equals(recovery.get("campaignId").getAsString()) && current.testerId().equals(recovery.get("testerId").getAsString())
                && recovery.has("collectionId") && lease.policy().collectionId().equals(recovery.get("collectionId").getAsString())) {
            String previousSession = DiagnosticProtocol.identifier(recovery.get("sessionId").getAsString());
            if (lease.policy().captureLogs()) {
                String text = "Previous session ended without a final acknowledgement. This is recovered evidence, not a complete crash report.\n"
                        + (recovery.has("lastCapturedLog") ? recovery.get("lastCapturedLog").getAsString() : "No saved log tail is available.");
                store.enqueue(new DiagnosticProtocol.Artifact("1.0", recoveryId(previousSession, "crash"), current.campaignId(), current.testerId(), lease.policy().collectionId(), previousSession,
                        "crash", "client", Instant.now().toString(), "text/plain", "utf8", text));
            }
            recovery.remove("lastCapturedLog");
            recovery.addProperty("state", "interrupted");
            recovery.addProperty("sparkStatus", "unavailable");
            store.enqueue(new DiagnosticProtocol.Artifact("1.0", recoveryId(previousSession, "session"), current.campaignId(), current.testerId(), lease.policy().collectionId(), previousSession,
                    "session", "client", Instant.now().toString(), "application/json", "utf8", recovery.toString()));
        } else store.discard();
        recovery = null;
        Files.deleteIfExists(root.resolve("active-session.json"));
    }

    private void enqueue(DiagnosticProtocol.Enrollment current, String kind, String contentType, String encoding, String content) throws IOException {
        if (sessionId != null && authorized(current)) store.enqueue(new DiagnosticProtocol.Artifact("1.0", UUID.randomUUID().toString(),
                current.campaignId(), current.testerId(), lease.policy().collectionId(), sessionId, kind, "client", Instant.now().toString(), contentType, encoding, content));
    }

    private static String recoveryId(String session, String kind) {
        return UUID.nameUUIDFromBytes((session + ":interrupted:" + kind).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private void deliver(DiagnosticProtocol.Enrollment current, long generation) throws IOException, InterruptedException {
        int attempted = 0;
        for (var artifact : store.pending()) {
            if (!same(generation, current) || !authorized(current)) return;
            var policy = lease.policy();
            if (!artifact.campaignId().equals(current.campaignId()) || !artifact.testerId().equals(current.testerId())
                    || !artifact.collectionId().equals(policy.collectionId())
                    || ((artifact.kind().equals("log") || artifact.kind().equals("crash")) && !policy.captureLogs())
                    || (artifact.kind().equals("spark") && !policy.captureSpark())) { store.acknowledge(artifact.artifactId()); continue; }
            if (++attempted > 4) return;
            if (http.upload(current, artifact)) store.acknowledge(artifact.artifactId());
            else { status = "Upload pending; acknowledgement not received"; return; }
        }
    }

    private boolean same(long generation, DiagnosticProtocol.Enrollment current) { return generation == lease.generation() && current == enrollment; }
    private boolean authorized(DiagnosticProtocol.Enrollment current) { return current == enrollment && lease.active(clock.getAsLong()); }

    private void enqueueSession(DiagnosticProtocol.Enrollment current) throws IOException {
        JsonObject metadata = session.deepCopy();
        metadata.remove("lastCapturedLog");
        metadata.addProperty("durationSeconds", Math.max(0, (clock.getAsLong() - sessionStarted) / 1_000_000_000L));
        metadata.addProperty("droppedArtifacts", store.dropped());
        enqueue(current, "session", "application/json", "utf8", metadata.toString());
    }

    /** Game shutdown stops the HTTP client's owned machinery without withdrawing next-launch consent. */
    public void closeTransport() { lease.clear(); spark.cancel(); http.close(); }

    private void stopCapture() { lease.clear(); logs.clear(); spark.cancel(); sessionId = null; session = null; sparkStarted = 0; }
    private void discard() throws IOException { stopCapture(); store.discard(); Files.deleteIfExists(root.resolve("active-session.json")); recovery = null; }
    private void removeKind(String kind) throws IOException { for (var artifact : store.pending()) if (artifact.kind().equals(kind)) store.acknowledge(artifact.artifactId()); }

    private JsonObject read(String name, long limit) throws IOException {
        Path path = root.resolve(name);
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path) || Files.size(path) > limit) return null;
        return com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
