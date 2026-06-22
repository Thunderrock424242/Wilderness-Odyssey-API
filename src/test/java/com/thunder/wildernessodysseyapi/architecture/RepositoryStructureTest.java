package com.thunder.wildernessodysseyapi.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the package and public-type naming rules documented in AGENTS.md.
 */
class RepositoryStructureTest {

    private static final String BASE_PACKAGE = "com.thunder.wildernessodysseyapi";
    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("(?m)^package\\s+([A-Za-z0-9_.]+);");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "\\bpublic\\s+(?:(?:abstract|final|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );

    @Test
    void productionSourcesFollowPackageAndTypeNamingConventions() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java").toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path sourceFile : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                inspectSource(sourceRoot, sourceFile, violations);
            }
        }

        assertTrue(violations.isEmpty(), () -> "Repository structure violations:\n" + String.join("\n", violations));
    }

    private static void inspectSource(Path sourceRoot, Path sourceFile, List<String> violations) throws IOException {
        String source = Files.readString(sourceFile);
        Path relativePath = sourceRoot.relativize(sourceFile);
        Matcher packageMatcher = PACKAGE_DECLARATION.matcher(source);
        if (!packageMatcher.find()) {
            violations.add(relativePath + ": production source has no package declaration");
            return;
        }

        String packageName = packageMatcher.group(1);
        if (!packageName.startsWith(BASE_PACKAGE)) {
            violations.add(relativePath + ": package must start with " + BASE_PACKAGE);
        }
        if (!packageName.equals(packageName.toLowerCase(Locale.ROOT))) {
            violations.add(relativePath + ": package segments must be lowercase");
        }

        String expectedPackage = relativePath.getParent().toString()
                .replace('\\', '.')
                .replace('/', '.');
        if (!packageName.equals(expectedPackage)) {
            violations.add(relativePath + ": path does not match package " + packageName);
        }

        Matcher typeMatcher = PUBLIC_TYPE.matcher(source);
        if (typeMatcher.find()) {
            String typeName = typeMatcher.group(1);
            if (!Character.isUpperCase(typeName.charAt(0))) {
                violations.add(relativePath + ": public type must use PascalCase");
            }
            if (!relativePath.getFileName().toString().equals(typeName + ".java")) {
                violations.add(relativePath + ": filename must match public type " + typeName);
            }
        }
    }
}
