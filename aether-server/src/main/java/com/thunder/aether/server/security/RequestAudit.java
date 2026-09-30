package com.thunder.aether.server.security;

import com.thunder.aether.server.api.JsonHttp;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;

/** Bounded metadata-only audit, separate from the durable admission transaction journal. */
public final class RequestAudit implements AutoCloseable {
    private final Path path;
    private FileChannel file;
    private boolean healthy = true;

    public RequestAudit(Path directory) throws IOException {
        path = directory.resolve("requests.jsonl");
        file = open();
    }

    private FileChannel open() throws IOException {
        return FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
    }

    public synchronized boolean record(String operation, String scope, int status) {
        if (!healthy) { return false; }
        try {
            if (file.size() >= 1_048_576) {
                file.close();
                Files.move(path, path.resolveSibling("requests.previous.jsonl"), StandardCopyOption.REPLACE_EXISTING);
                file = open();
            }
            byte[] bytes = (JsonHttp.JSON.toJson(Map.of("at", Instant.now().toString(),
                    "operation", operation, "scope", scope, "status", status)) + "\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) { file.write(buffer); }
            return true;
        } catch (IOException failure) { healthy = false; return false; }
    }

    @Override public synchronized void close() throws IOException { file.close(); }
}
