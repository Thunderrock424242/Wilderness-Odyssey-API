package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One installation's bounded retry store, deliberately separate from the server-owned telemetry spool. */
public final class DiagnosticStore {
    private final Path directory;
    private final ArrayList<DiagnosticProtocol.Artifact> queue = new ArrayList<>();
    private long dropped;

    public DiagnosticStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        int scanned = 0;
        long scannedBytes = 0;
        try (var paths = Files.newDirectoryStream(directory, "artifact-*.json")) {
            for (Path path : paths) {
                if (++scanned > 64) break;
                try {
                    long size = Files.size(path);
                    if (Files.isSymbolicLink(path) || size > DiagnosticProtocol.MAX_ARTIFACT_BYTES) continue;
                    scannedBytes += size;
                    if (scannedBytes > 8L * 1024 * 1024) break;
                    var artifact = DiagnosticProtocol.JSON.fromJson(Files.readString(path), DiagnosticProtocol.Artifact.class);
                    if (artifact == null || !path.getFileName().toString().equals("artifact-" + artifact.artifactId() + ".json")) continue;
                    queue.add(artifact);
                } catch (RuntimeException error) { /* Invalid row does not prevent valid recovery. */ }
            }
        }
        queue.sort(java.util.Comparator.comparing(DiagnosticProtocol.Artifact::capturedAt));
        trim();
    }

    public synchronized List<DiagnosticProtocol.Artifact> pending() { return List.copyOf(queue); }
    public synchronized long dropped() { return dropped; }

    public synchronized void enqueue(DiagnosticProtocol.Artifact artifact) throws IOException {
        if (queue.stream().anyMatch(item -> item.artifactId().equals(artifact.artifactId()))) return;
        String json = DiagnosticProtocol.JSON.toJson(artifact);
        if (json.getBytes(StandardCharsets.UTF_8).length > DiagnosticProtocol.MAX_ARTIFACT_BYTES) { dropped++; return; }
        write(path(artifact.artifactId()), json);
        queue.add(artifact);
        trim();
    }

    private void trim() throws IOException {
        while (queue.size() > 16 || queue.stream().mapToLong(item -> DiagnosticProtocol.JSON.toJson(item).getBytes(StandardCharsets.UTF_8).length).sum() > 8L * 1024 * 1024) {
            var oldest = queue.removeFirst();
            Files.deleteIfExists(path(oldest.artifactId()));
            dropped++;
        }
    }

    public synchronized void acknowledge(String artifactId) throws IOException {
        Files.deleteIfExists(path(artifactId));
        queue.removeIf(item -> item.artifactId().equals(artifactId));
    }

    public synchronized void discard() throws IOException {
        // Only files with our exact UUID naming convention under this feature directory are deleted.
        try (var paths = Files.newDirectoryStream(directory, "artifact-*.json")) {
            for (Path path : paths) if (path.getFileName().toString().matches("artifact-[0-9a-f-]{36}\\.json")) Files.deleteIfExists(path);
        }
        queue.clear();
    }

    private Path path(String id) { return directory.resolve("artifact-" + UUID.fromString(id) + ".json"); }

    public static void write(Path target, String content) throws IOException {
        if (Files.isSymbolicLink(target) || Files.isSymbolicLink(target.getParent())) throw new IOException("Unsafe diagnostic storage path.");
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        if (Files.isSymbolicLink(temporary)) throw new IOException("Unsafe diagnostic storage path.");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException error) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
