package com.thunder.wildernessodysseyapi.FileSecurity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The type Safer in place obfuscate multi.
 */
public class SaferInPlaceObfuscateMulti {

    // List all the files you want to XOR in-place
    private static final String FILE_PATH_1 = "Modpack_Checklist";
    private static final String FILE_PATH_2 = "changelog.txt";

    // The XOR key – same key is used for obfuscation & deobfuscation
    private static final byte XOR_KEY = (byte) 0x5A;

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
        // We’ll process each file one by one
        processFile(FILE_PATH_1);
        processFile(FILE_PATH_2);
    }

    private static void processFile(String filePathString) {
        Path originalPath = Path.of(filePathString);
        // We’ll create a temp file in the same folder, adding ".tmp"
        Path tempPath = originalPath.resolveSibling(originalPath.getFileName() + ".tmp");

        System.out.println("Processing: " + originalPath);

        // Step 1: Write XOR-obfuscated data to the temp file
        try (
                InputStream in = Files.newInputStream(originalPath);
                OutputStream out = Files.newOutputStream(tempPath)
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                // XOR each byte in the buffer
                for (int i = 0; i < bytesRead; i++) {
                    buffer[i] = (byte) (buffer[i] ^ XOR_KEY);
                }
                // Write the transformed bytes to the temp file
                out.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            System.err.println("Error obfuscating file: " + e.getMessage());
            // Clean up the temp file if something fails
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
            return;
        }

        // Step 2: If we reached here, the temp file was written successfully.
        // Replace (move) the original with the temp file.
        try {
            Files.delete(originalPath); // delete the original
            Files.move(tempPath, originalPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("Successfully obfuscated/deobfuscated: " + originalPath);
            System.out.println("Run this program again to revert (XOR again).");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error replacing original file: " + e.getMessage());
        }
    }
}
