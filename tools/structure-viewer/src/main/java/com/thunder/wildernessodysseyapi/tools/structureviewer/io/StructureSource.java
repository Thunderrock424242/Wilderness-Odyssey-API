package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/** Stable identity for a loose template or one exact, read-only archive entry. */
public record StructureSource(Path container, String entry) {
    public StructureSource {
        container = container.toAbsolutePath().normalize();
        entry = entry == null ? "" : entry;
        if (!entry.isEmpty() && (!entry.startsWith("data/") || entry.contains("\\")
                || entry.contains(":") || java.util.Arrays.asList(entry.split("/")).contains("..")))
            throw new IllegalArgumentException("Invalid template archive entry: " + entry);
    }

    /** Creates the same identity used by ordinary Open-file actions. */
    public static StructureSource file(Path path) { return new StructureSource(path, ""); }
    public boolean archived() { return !entry.isEmpty(); }
    public String description() { return container + (archived() ? "!/" + entry : ""); }
    public String filename() { return archived() ? entry.substring(entry.lastIndexOf('/') + 1) : container.getFileName().toString(); }

    /** Closes archive handles after decoding; never extracts files or runs mod code. */
    public StructureData read() throws IOException {
        if (!archived()) return StructureReaders.read(container);
        try (var archive = FileSystems.newFileSystem(container, Map.of())) {
            var parsed = StructureReaders.read(archive.getPath("/" + entry));
            return new StructureData(parsed.name(), container, parsed.size(), parsed.blocks(), parsed.palettes(),
                    parsed.entities(), parsed.metadata(), parsed.diagnostics());
        }
    }
}
