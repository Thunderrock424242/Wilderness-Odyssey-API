package com.thunder.aether.server.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;

/** Streams immutable bundled files into an owned directory, publishing only verified complete files. */
public final class BundleInstaller {
    private BundleInstaller() {}
    @FunctionalInterface public interface Resources { InputStream open(String path) throws IOException; }

    /** Verifies or repairs bundled assets while the caller holds exclusive directory ownership. */
    public static void install(Path directory, BundleManifest manifest, Resources resources) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        DataDirectory.rejectLinks(root);
        Files.createDirectories(root);
        // The launcher holds DataDirectory's exclusive lock throughout setup and service lifetime.
        var parents = new java.util.HashSet<Path>();
        for (var asset : manifest.assets()) { parents.add(resolve(root, asset.path()).getParent()); }
        for (Path parent : parents) {
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) { continue; }
            try (var entries = Files.newDirectoryStream(parent, ".aether-extract-*.part")) {
                for (Path entry : entries) {
                    if (!entry.getFileName().toString().matches("\\.aether-extract-[0-9]+\\.part")) { continue; }
                    DataDirectory.rejectLinks(entry);
                    if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("An abandoned extraction path is not a regular file.");
                    }
                    Files.delete(entry);
                }
            }
        }
        var missing = new ArrayList<BundleManifest.Asset>();
        long needed = 64L * 1024 * 1024;
        for (var asset : manifest.assets()) {
            Path target = resolve(root, asset.path());
            if (!matches(target, asset)) { missing.add(asset); needed = Math.addExact(needed, asset.size()); }
        }
        if (!missing.isEmpty() && Files.getFileStore(root).getUsableSpace() < needed) {
            throw new IOException("Not enough free disk space to extract the bundled runtime and model.");
        }
        for (var asset : missing) {
            if (Thread.currentThread().isInterrupted()) { throw new IOException("Setup interrupted."); }
            Path target = resolve(root, asset.path());
            Files.createDirectories(target.getParent());
            Path partial = Files.createTempFile(target.getParent(), ".aether-extract-", ".part");
            try {
                MessageDigest digest = digest();
                long written = 0;
                try (InputStream input = resources.open(asset.path()); var output = Files.newOutputStream(partial)) {
                    if (input == null) { throw new IOException("A bundled file is missing."); }
                    byte[] buffer = new byte[1024 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) { throw new IOException("Setup interrupted."); }
                        written += count;
                        if (written > asset.size()) { throw new IOException("Bundled file exceeds its declared size."); }
                        output.write(buffer, 0, count); digest.update(buffer, 0, count);
                    }
                }
                if (written != asset.size() || !HexFormat.of().formatHex(digest.digest()).equals(asset.sha256())) {
                    throw new IOException("Bundled file failed integrity verification.");
                }
                DataDirectory.rejectLinks(target);
                try { Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(partial); }
        }
        Path executable = resolve(root, manifest.executable());
        if (!manifest.platform().startsWith("windows") && !executable.toFile().setExecutable(true, true)) {
            throw new IOException("Host does not permit marking the bundled Ollama binary executable.");
        }
    }

    private static Path resolve(Path root, String name) throws IOException {
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root) || target.equals(root)) { throw new IOException("Invalid installation path."); }
        DataDirectory.rejectLinks(target);
        return target;
    }

    private static boolean matches(Path path, BundleManifest.Asset asset) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { return false; }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("An asset path is not a regular file."); }
        if (Files.size(path) != asset.size()) { return false; }
        MessageDigest digest = digest();
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[1024 * 1024];
            int count;
            while ((count = input.read(bytes)) != -1) {
                if (Thread.currentThread().isInterrupted()) { throw new IOException("Setup interrupted."); }
                digest.update(bytes, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).equals(asset.sha256());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
