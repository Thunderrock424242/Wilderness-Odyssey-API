package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import java.nio.file.*;
import java.util.Optional;

/** Resolves common instance layouts without searching drives or unrelated launcher profiles. */
public final class ModpackLocation {
    private ModpackLocation() {}

    /** Accepts an instance root, its mods folder, or a launcher instance containing .minecraft. */
    public static Path normalize(Path selected) {
        Path root = selected.toAbsolutePath().normalize();
        if (root.getFileName() != null && root.getFileName().toString().equalsIgnoreCase("mods")) return root.getParent();
        if (Files.isDirectory(root.resolve(".minecraft/mods"))) return root.resolve(".minecraft");
        if (Files.isDirectory(root.resolve("minecraft/mods"))) return root.resolve("minecraft");
        return root;
    }

    /** Checks at most three ancestors, allowing the app folder to sit inside a modpack. */
    public static Optional<Path> nearby(Path directory) {
        Path candidate = directory.toAbsolutePath().normalize();
        for (int i = 0; i < 4 && candidate != null; i++, candidate = candidate.getParent()) {
            Path root = normalize(candidate);
            if (Files.isDirectory(root.resolve("mods"))) return Optional.of(root);
        }
        return Optional.empty();
    }
}
