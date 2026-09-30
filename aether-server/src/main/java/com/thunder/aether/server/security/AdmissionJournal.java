package com.thunder.aether.server.security;

import com.google.gson.JsonObject;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.bootstrap.DataDirectory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Durable admission state and mutation audit. No player messages or model controls are stored here. */
public final class AdmissionJournal implements AutoCloseable {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RECORDS = 4096;
    private final DataDirectory directory;
    private final FileChannel file;
    private final Map<String, JsonObject> applied = new HashMap<>();
    private long revision;
    private boolean paused;
    private boolean healthy = true;

    public AdmissionJournal(Path path) throws IOException {
        directory = DataDirectory.open(path);
        FileChannel opened = null;
        try {
            Path journal = directory.path().resolve("admission.jsonl");
            opened = FileChannel.open(journal, StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            if (opened.size() > MAX_BYTES) { throw new IOException("ADMISSION_JOURNAL_FULL"); }
            ByteBuffer bytes = ByteBuffer.allocate((int) opened.size());
            while (bytes.hasRemaining() && opened.read(bytes) >= 0) { }
            String text = new String(bytes.array(), java.nio.charset.StandardCharsets.UTF_8);
            if (!text.isEmpty() && !text.endsWith("\n")) { throw new IOException("ADMISSION_JOURNAL_INCOMPLETE"); }
            for (String line : text.split("\n")) {
                if (line.isEmpty()) { continue; }
                JsonObject entry = JsonHttp.object(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                JsonObject command = entry.getAsJsonObject("command");
                validate(command, Instant.parse(entry.get("appliedAt").getAsString()));
                if (entry.get("revision").getAsLong() != revision + 1
                        || command.get("expectedRevision").getAsLong() != revision
                        || applied.containsKey(command.get("requestId").getAsString())
                        || applied.size() >= MAX_RECORDS) { throw new IOException("ADMISSION_JOURNAL_INVALID"); }
                accept(entry);
            }
            opened.position(opened.size());
            file = opened;
        } catch (Exception failure) {
            if (opened != null) { opened.close(); }
            directory.close();
            throw new IOException("ADMISSION_JOURNAL_UNAVAILABLE");
        }
    }

    public synchronized boolean allowsInference() { return healthy && !paused; }

    public synchronized Map<String, Object> snapshot() {
        return Map.of("paused", paused || !healthy, "revision", revision, "healthy", healthy);
    }

    /** Validates replay, revision and expiry before fsync; state changes only after the audit is durable. */
    public synchronized Map<String, Object> apply(JsonObject command) throws IOException {
        // Evaluate expiry after acquiring the writer lock, including time spent waiting for another fsync.
        return apply(command, Instant.now());
    }

    synchronized Map<String, Object> apply(JsonObject command, Instant now) throws IOException {
        validate(command, now);
        if (!healthy) { throw new IOException("ADMISSION_JOURNAL_UNAVAILABLE"); }
        String id = command.get("requestId").getAsString();
        JsonObject previous = applied.get(id);
        if (previous != null) {
            if (!previous.getAsJsonObject("command").equals(command)) { throw new Conflict(); }
            return Map.of("revision", previous.get("revision").getAsLong(), "paused",
                    previous.getAsJsonObject("command").get("paused").getAsBoolean(), "replayed", true);
        }
        if (command.get("expectedRevision").getAsLong() != revision) { throw new Conflict(); }
        JsonObject entry = new JsonObject();
        entry.add("command", command.deepCopy());
        entry.addProperty("appliedAt", now.toString());
        entry.addProperty("service", "kinetic-administration");
        entry.addProperty("revision", revision + 1);
        byte[] bytes = (entry + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (applied.size() >= MAX_RECORDS || file.size() + bytes.length > MAX_BYTES) {
            throw new IOException("ADMISSION_JOURNAL_FULL");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) { file.write(buffer); }
            file.force(true);
            accept(entry);
        } catch (IOException failure) {
            healthy = false;
            throw new IOException("ADMISSION_JOURNAL_UNAVAILABLE");
        }
        return Map.of("revision", revision, "paused", paused, "replayed", false);
    }

    private void accept(JsonObject entry) {
        JsonObject command = entry.getAsJsonObject("command");
        applied.put(command.get("requestId").getAsString(), entry);
        revision = entry.get("revision").getAsLong();
        paused = command.get("paused").getAsBoolean();
    }

    private static void validate(JsonObject command, Instant now) {
        if (!command.keySet().equals(Set.of("requestId", "expectedRevision", "expiresAt", "paused", "actor"))) {
            throw new IllegalArgumentException("INVALID_COMMAND");
        }
        for (String key : List.of("requestId", "expiresAt", "actor")) {
            if (!command.get(key).isJsonPrimitive() || !command.getAsJsonPrimitive(key).isString()) {
                throw new IllegalArgumentException("INVALID_COMMAND");
            }
        }
        String id = command.get("requestId").getAsString();
        if (!UUID.fromString(id).toString().equals(id) || !command.get("actor").getAsString().matches("[A-Za-z0-9._:@-]{1,128}")
                || !command.get("paused").isJsonPrimitive() || !command.getAsJsonPrimitive("paused").isBoolean()
                || !command.get("expectedRevision").isJsonPrimitive()
                || !command.getAsJsonPrimitive("expectedRevision").isNumber()
                || !command.get("expectedRevision").getAsString().matches("0|[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException("INVALID_COMMAND");
        }
        Instant expiry = Instant.parse(command.get("expiresAt").getAsString());
        if (!expiry.isAfter(now) || expiry.isAfter(now.plusSeconds(300))) {
            throw new IllegalArgumentException("EXPIRED_COMMAND");
        }
    }

    public static final class Conflict extends IllegalArgumentException { }

    @Override public synchronized void close() throws IOException {
        try { file.close(); } finally { directory.close(); }
    }
}
