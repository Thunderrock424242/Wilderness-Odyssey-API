package com.thunder.wildernessodysseyapi.WorldGen.structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Simple abstraction around starter structure schematics so Create, WorldEdit, and vanilla
 * placement logic can share the same metadata.
 */
public record StarterStructureSchematic(Path path) {

    /** Returns {@code true} when the schematic file exists on disk. */
    public boolean exists() {
        return path != null && Files.isRegularFile(path);
    }

    /** Returns the schematic file name, or an empty string when the path is missing. */
    public String fileName() {
        return path == null ? "" : path.getFileName().toString();
    }

    /** Returns the lowercase extension (including the leading dot) or an empty string. */
    public String extension() {
        if (path == null) {
            return "";
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
    }
}
