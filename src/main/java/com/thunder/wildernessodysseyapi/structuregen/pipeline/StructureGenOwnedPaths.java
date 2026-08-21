package com.thunder.wildernessodysseyapi.structuregen.pipeline;

import java.nio.file.Path;
import java.util.regex.Pattern;

/** Resolves the normal or isolated project-owned build root used by StructureGen. */
public final class StructureGenOwnedPaths {
    private static final Pattern ISOLATED_BUILD_NAME = Pattern.compile("\\.[a-z0-9][a-z0-9-]*-build");

    private StructureGenOwnedPaths() {
    }

    /**
     * Returns the direct child build directory containing {@code candidate}.
     *
     * <p>Only {@code build/} and hidden task-specific names ending in {@code -build} are accepted;
     * arbitrary absolute output roots and source directories remain forbidden.</p>
     */
    public static Path requireBuildRoot(Path projectRoot, Path candidate, String description) {
        Path normalizedProject = projectRoot.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedProject)) {
            throw new IllegalArgumentException(description + " escapes project root "
                    + normalizedProject + ": " + normalizedCandidate);
        }
        Path relative = normalizedProject.relativize(normalizedCandidate);
        if (relative.getNameCount() == 0) {
            throw new IllegalArgumentException(description + " may not use the project root directly");
        }
        String buildName = relative.getName(0).toString();
        if (!"build".equals(buildName) && !ISOLATED_BUILD_NAME.matcher(buildName).matches()) {
            throw new IllegalArgumentException(description + " must use build/ or a hidden *-build directory: "
                    + normalizedCandidate);
        }
        return normalizedProject.resolve(buildName).normalize();
    }
}
