package com.thunder.wildernessodysseyapi.structuregen.pipeline;

import com.thunder.wildernessodysseyapi.structuregen.StructureGenConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Normalized project-owned paths used by StructureGen generation.
 *
 * <p>Generated resources are required to remain beneath this checkout's {@code build/}
 * tree. That hard boundary prevents a command-line typo from targeting a world, save,
 * source resource, or external Minecraft installation.</p>
 */
public final class StructureGenPaths {

    private static final Pattern SAFE_STRUCTURE_NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private final Path projectRoot;
    private final Path blueprintRoot;
    private final Path outputResourceRoot;
    private final Path generatedStructureRoot;
    private final Path legacyGeneratedStructureRoot;
    private final Path handAuthoredResourceRoot;
    private final List<Path> packagedResourceRoots;

    /** Creates and validates a complete generation path configuration. */
    public StructureGenPaths(Path projectRoot, Path blueprintRoot, Path outputResourceRoot) {
        this.projectRoot = normalize(projectRoot);
        this.blueprintRoot = normalize(blueprintRoot);
        this.outputResourceRoot = normalize(outputResourceRoot);
        this.generatedStructureRoot = this.outputResourceRoot
                .resolve("data")
                .resolve(StructureGenConstants.NAMESPACE)
                .resolve("structure")
                .normalize();
        this.legacyGeneratedStructureRoot = this.outputResourceRoot
                .resolve("data")
                .resolve(StructureGenConstants.NAMESPACE)
                .resolve("structures")
                .normalize();
        this.handAuthoredResourceRoot = this.projectRoot.resolve("src/main/resources").normalize();
        this.packagedResourceRoots = List.of(
                this.handAuthoredResourceRoot,
                this.projectRoot.resolve("src/generated/resources").normalize()
        );

        requireContained(this.projectRoot.resolve("src/main/structure_blueprints").normalize(), this.blueprintRoot,
                "Blueprint source directory");
        requireContained(this.projectRoot.resolve("build").normalize(), this.outputResourceRoot,
                "Generated resource directory");
        requireContained(this.outputResourceRoot, this.generatedStructureRoot,
                "Generated structure directory");
        requireContained(this.outputResourceRoot, this.legacyGeneratedStructureRoot,
                "Legacy generated structure directory");
        if (this.outputResourceRoot.startsWith(this.handAuthoredResourceRoot)
                || this.handAuthoredResourceRoot.startsWith(this.outputResourceRoot)) {
            throw new IllegalArgumentException("Generated resources must not overlap hand-authored resources: "
                    + this.outputResourceRoot);
        }
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path blueprintRoot() {
        return blueprintRoot;
    }

    public Path outputResourceRoot() {
        return outputResourceRoot;
    }

    public Path generatedStructureRoot() {
        return generatedStructureRoot;
    }

    /**
     * Returns every StructureGen-owned structure directory included by the generated
     * resource source set, including the historical plural directory.
     */
    public List<Path> generatedStructureRoots() {
        return List.of(generatedStructureRoot, legacyGeneratedStructureRoot);
    }

    /**
     * Resolves one already-validated simple name and repeats containment checks at the final file boundary.
     */
    public Path generatedStructure(String name) {
        requireSafeStructureName(name);
        Path target = generatedStructureRoot.resolve(name + ".nbt").normalize();
        requireContained(generatedStructureRoot, target, "Generated structure target");
        return target;
    }

    /** Returns any singular or legacy-plural hand-authored resource collisions. */
    public List<Path> handAuthoredCollisions(String name) {
        requireSafeStructureName(name);
        List<Path> collisions = new ArrayList<>();
        for (Path resourceRoot : packagedResourceRoots) {
            Path namespaceRoot = resourceRoot.resolve("data")
                    .resolve(StructureGenConstants.NAMESPACE)
                    .normalize();
            for (String directory : List.of("structure", "structures")) {
                Path candidate = namespaceRoot.resolve(directory).resolve(name + ".nbt").normalize();
                requireContained(resourceRoot, candidate, "Packaged structure collision candidate");
                if (Files.exists(candidate)) {
                    collisions.add(candidate);
                }
            }
        }
        // The existing empty GameTest template is re-namespaced into the mod's
        // singular structure directory by processResources, so it is also a
        // packaged hand-authored collision even though its source path differs.
        if ("empty".equals(name)) {
            Path gameTestTemplate = handAuthoredResourceRoot
                    .resolve("data/minecraft/structures/empty.nbt")
                    .normalize();
            requireContained(handAuthoredResourceRoot, gameTestTemplate,
                    "Hand-authored GameTest collision candidate");
            if (Files.exists(gameTestTemplate)) {
                collisions.add(gameTestTemplate);
            }
        }
        return List.copyOf(collisions);
    }

    /** Rejects any generated name that would collide with a manual resource. */
    public void requireNoHandAuthoredCollision(String name) {
        List<Path> collisions = handAuthoredCollisions(name);
        if (!collisions.isEmpty()) {
            throw new IllegalArgumentException("Generated structure '" + name
                    + "' would overwrite or shadow a hand-authored structure: " + collisions.getFirst());
        }
    }

    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private void requireSafeStructureName(String name) {
        if (name == null || !SAFE_STRUCTURE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe generated structure name '" + name
                    + "'; expected 1-64 lowercase letters, digits, '_' or '-'.");
        }
    }

    private void requireContained(Path root, Path target, String description) {
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException(description + " escapes its allowed root " + root + ": " + target);
        }
    }
}
