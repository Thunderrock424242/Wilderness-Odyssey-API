package com.thunder.aether.server.bootstrap;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Holds exclusive ownership of an application directory without changing global OS permissions. */
public final class DataDirectory implements AutoCloseable {
    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;

    private DataDirectory(Path path, FileChannel channel, FileLock lock) {
        this.path = path; this.channel = channel; this.lock = lock;
    }

    /** Locks a normalized application directory and rejects linked paths before writing. */
    public static DataDirectory open(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        rejectLinks(root);
        Files.createDirectories(root);
        Path lockPath = root.resolve(".aether.lock");
        rejectLinks(lockPath);
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) { throw new IOException("Another Aether launcher owns this data directory."); }
            return new DataDirectory(root, channel, lock);
        } catch (IOException | OverlappingFileLockException failure) {
            channel.close();
            throw new IOException("Another launcher owns the data directory, or locking is unavailable.");
        }
    }

    /** Rejects redirection through existing symbolic links or other filesystem reparse objects. */
    static void rejectLinks(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path part = absolute.getRoot();
        for (Path segment : absolute) {
            part = part.resolve(segment);
            if (Files.exists(part, LinkOption.NOFOLLOW_LINKS)) {
                var attributes = Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    throw new IOException("Application paths must not contain symbolic links or reparse points.");
                }
            }
        }
    }

    /** Returns the absolute application-owned directory. */
    public Path path() { return path; }

    /** Releases directory ownership and its file channel. */
    @Override public void close() throws IOException {
        try { if (lock.isValid()) { lock.release(); } }
        finally { channel.close(); }
    }
}
