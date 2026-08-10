package com.thunder.wildernessodysseyapi.structuregen.pipeline;

import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparator;
import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparisonReport;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.nbt.MinecraftStructureNbtReader;
import com.thunder.wildernessodysseyapi.structuregen.nbt.MinecraftStructureNbtWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stages, re-reads, verifies, and publishes one generated structure under the build tree.
 *
 * <p>The existing generated destination is not touched until a complete temporary NBT has
 * passed semantic verification. Hand-authored resources are never legal destinations.</p>
 */
public final class SafeStructureOutput {

    private final StructureGenPaths paths;
    private final MinecraftStructureNbtWriter writer;
    private final MinecraftStructureNbtReader reader;
    private final StructureComparator comparator;

    /** Creates a safe output boundary using the production Minecraft NBT codec. */
    public SafeStructureOutput(StructureGenPaths paths) {
        this(paths, new MinecraftStructureNbtWriter(), new MinecraftStructureNbtReader(), new StructureComparator());
    }

    SafeStructureOutput(
            StructureGenPaths paths,
            MinecraftStructureNbtWriter writer,
            MinecraftStructureNbtReader reader,
            StructureComparator comparator
    ) {
        this.paths = paths;
        this.writer = writer;
        this.reader = reader;
        this.comparator = comparator;
    }

    /** Performs target containment, symlink, and hand-authored collision checks without writing. */
    public Path preflight(StructureModel model) throws IOException {
        paths.requireNoHandAuthoredCollision(model.name());
        Path destination = paths.generatedStructure(model.name());
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("Refusing symbolic-link generated destination: " + destination);
        }
        rejectExistingSymbolicLinks(paths.projectRoot().resolve("build"), destination.getParent());
        return destination;
    }

    /** Writes a verified temporary file and safely publishes it as generated output. */
    public GeneratedStructure writeVerified(StructureModel model) throws IOException {
        Path destination = preflight(model);
        Files.createDirectories(destination.getParent());
        rejectExistingSymbolicLinks(paths.projectRoot().resolve("build"), destination.getParent());

        Path temporary = Files.createTempFile(
                destination.getParent(),
                "." + model.name() + "-",
                ".nbt.tmp"
        );
        boolean published = false;
        try {
            writer.writeCompressed(model, temporary);
            StructureModel reread = reader.read(temporary, model.name());
            StructureComparisonReport verification = comparator.compare(model, reread);
            if (!verification.semanticallyMatches()) {
                throw new IOException("Generated NBT failed semantic re-read verification: "
                        + comparator.format(verification).replace(System.lineSeparator(), " | "));
            }
            SafeFilePublisher.publish(temporary, destination);
            published = true;
            return new GeneratedStructure(model.name(), destination, verification);
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void rejectExistingSymbolicLinks(Path allowedRoot, Path targetParent) throws IOException {
        Path normalizedRoot = allowedRoot.toAbsolutePath().normalize();
        Path normalizedParent = targetParent.toAbsolutePath().normalize();
        if (!normalizedParent.startsWith(normalizedRoot)) {
            throw new IOException("Generated parent escapes output root: " + normalizedParent);
        }
        Path current = normalizedRoot;
        if (Files.exists(current) && Files.isSymbolicLink(current)) {
            throw new IOException("Refusing symbolic-link output root: " + current);
        }
        for (Path segment : normalizedRoot.relativize(normalizedParent)) {
            current = current.resolve(segment);
            if (Files.exists(current) && Files.isSymbolicLink(current)) {
                throw new IOException("Refusing symbolic-link output directory: " + current);
            }
        }
    }
}
