package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Discovers authored and StructureGen resources without generating files or scanning worlds/caches. */
public final class StructureDiscovery {
    private StructureDiscovery() {}

    /** Roots mirror the existing main resources and StructureGen output conventions. */
    public static List<Path> roots(Path project, Path selectedBuild) {
        return List.copyOf(new LinkedHashSet<>(List.of(
                project.resolve("src/main/resources"), project.resolve("src/generated/resources"),
                selectedBuild.resolve("generated/structuregen/resources"),
                project.resolve("build/generated/structuregen/resources"),
                project.resolve(".codex-build/generated/structuregen/resources"))));
    }

    /** Finds NBT beneath structure/structures directories, retaining source labels for duplicate IDs. */
    public static List<Path> discover(List<Path> roots) throws IOException {
        var files = new LinkedHashSet<Path>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (var paths = Files.walk(root, 24)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".nbt"))
                        .filter(path -> {
                            for (Path part : root.relativize(path)) {
                                if (part.toString().equals("structure") || part.toString().equals("structures")) return true;
                            }
                            return false;
                        }).map(path -> path.toAbsolutePath().normalize()).sorted().forEach(files::add);
            }
        }
        return new ArrayList<>(files);
    }
}

