package com.thunder.wildernessodysseyapi.structure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import net.minecraft.resources.ResourceLocation;

public class SchematicUtils {

    public static void copySchematicToWorldEdit(String namespace, String resourcePath) {
        // Define the source and destination
        ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, resourcePath);
        String worldEditSchematicDir = "plugins/WorldEdit/schematics";

        // Ensure the WorldEdit schematics directory exists
        File destinationDir = new File(worldEditSchematicDir);
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            System.out.println("Could not create WorldEdit schematics directory!");
            return;
        }

        // Destination file
        File destinationFile = new File(destinationDir, new File(resourcePath).getName());

        try (InputStream schemStream = SchematicUtils.class.getResourceAsStream(
                "/assets/" + resourceLocation.getNamespace() + "/" + resourceLocation.getPath())) {

            if (schemStream == null) {
                System.out.println("Schematic file not found in resources: " + resourceLocation);
                return;
            }

            // Copy the schematic to the WorldEdit directory
            Files.copy(schemStream, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Schematic copied to WorldEdit schematics folder: " + destinationFile.getAbsolutePath());

        } catch (IOException e) {
            System.out.println("Error while copying schematic to WorldEdit directory:");
            e.printStackTrace();
        }
    }
}
