package com.thunder.wildernessodysseyapi.FileSecurity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The type Hardcoded in place obfuscate.
 */
public class HardcodedInPlaceObfuscate {

    // 1) The path to the file you want to XOR-obfuscate
    private static final String FILE_PATH = "Modpack_Checklist";

    // 2) The XOR key – must be the same if you want to deobfuscate
    private static final byte XOR_KEY = (byte) 0x5A;

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
        Path filePath = Path.of(FILE_PATH);

        try {
            // Read entire file into memory
            byte[] fileBytes = Files.readAllBytes(filePath);

            // XOR each byte with XOR_KEY
            for (int i = 0; i < fileBytes.length; i++) {
                fileBytes[i] = (byte) (fileBytes[i] ^ XOR_KEY);
            }

            // Write back to the same file
            Files.write(filePath, fileBytes);

            System.out.println("File obfuscated (in-place) successfully!");
            System.out.println("Run this program again on the same file to deobfuscate.");
        } catch (IOException e) {
            System.err.println("Error during in-place obfuscation: " + e.getMessage());
        }
    }
}
