package com.thunder.wildernessodysseyapi.tools.filesecurity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Reversibly transforms local text files with a simple XOR operation.
 *
 * <p>This developer tool is obfuscation, not encryption. It writes to a
 * sibling temporary file and replaces the original only after the transform
 * completes, reducing the chance of corrupting a source file.</p>
 */
public final class SaferInPlaceObfuscateMulti {

    private static final List<Path> DEFAULT_FILES = List.of(
            Path.of("Modpack_Checklist.txt"),
            Path.of("changelog.txt")
    );
    private static final byte XOR_KEY = (byte) 0x5A;

    private SaferInPlaceObfuscateMulti() {
    }

    /**
     * Transforms command-line paths, or the legacy default files when no paths are supplied.
     *
     * @param args optional paths to transform in place
     */
    public static void main(String[] args) {
        List<Path> files = args.length == 0
                ? DEFAULT_FILES
                : java.util.Arrays.stream(args).map(Path::of).toList();
        files.forEach(SaferInPlaceObfuscateMulti::processFile);
    }

    private static void processFile(Path originalPath) {
        Path tempPath = originalPath.resolveSibling(originalPath.getFileName() + ".tmp");
        System.out.println("Processing: " + originalPath);

        try {
            transform(originalPath, tempPath);
            replaceOriginal(tempPath, originalPath);
            System.out.println("Successfully transformed: " + originalPath);
        } catch (IOException exception) {
            System.err.println("Unable to transform " + originalPath + ": " + exception.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
        }
    }

    // Streams the transform so large files do not need to be retained in memory.
    private static void transform(Path source, Path destination) throws IOException {
        try (InputStream input = Files.newInputStream(source);
             OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                for (int index = 0; index < bytesRead; index++) {
                    buffer[index] = (byte) (buffer[index] ^ XOR_KEY);
                }
                output.write(buffer, 0, bytesRead);
            }
        }
    }

    // Uses an atomic replacement when the filesystem supports it, with a safe fallback otherwise.
    private static void replaceOriginal(Path tempPath, Path originalPath) throws IOException {
        try {
            Files.move(tempPath, originalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
