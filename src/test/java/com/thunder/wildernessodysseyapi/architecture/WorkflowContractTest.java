package com.thunder.wildernessodysseyapi.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects the repository's build, security-analysis, and publishing gates. */
class WorkflowContractTest {
    private static final Path PROJECT_ROOT = Path.of(System.getProperty(
            "wildernessodysseyapi.projectDir", ".")).toAbsolutePath().normalize();

    @Test
    void workflowYamlParsesWithoutDuplicateMappings() throws IOException {
        try (Stream<Path> files = Files.list(PROJECT_ROOT.resolve(".github/workflows"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                assertValidYaml(file.getFileName().toString());
            }
        }
    }

    @Test
    void buildAndPublishRoutesRequireTheFullVerificationGate() throws IOException {
        String build = workflow("build.yml");
        assertTrue(build.contains("pull_request:"));
        assertTrue(build.contains("./gradlew build -PcodexBuildDir=.ci-build"));
        assertTrue(build.contains("git diff --exit-code"));

        int testGate = build.indexOf("./gradlew test ");
        assertTrue(testGate >= 0 && build.indexOf("./gradlew build ") > testGate);

        // This checkout currently has no publishing workflow. Any future route
        // that invokes publish must still perform its release build first.
        try (Stream<Path> files = Files.list(PROJECT_ROOT.resolve(".github/workflows"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                String publish = Files.readString(file);
                int publishStep = publish.indexOf("./gradlew publish ");
                if (publishStep < 0) continue;
                int buildGate = publish.indexOf("./gradlew build -PcodexBuildDir=.release-build");
                assertTrue(buildGate >= 0 && publishStep > buildGate, file.toString());
                assertTrue(publish.contains("secrets.GITHUB_TOKEN"));
                assertFalse(publish.contains("secrets.GIT_TOKEN"));
            }
        }
    }

    @Test
    void securityWorkflowUsesCurrentActionsAndNoMissingConfigReference() throws IOException {
        String codeql = workflow("codeql.yml");
        assertTrue(codeql.contains("github/codeql-action/init@v4"));
        assertTrue(codeql.contains("github/codeql-action/analyze@v4"));
        assertTrue(codeql.contains("github/codeql-action/upload-sarif@v4"));
        assertTrue(codeql.contains("JetBrains/qodana-action@v2026.2"));
        assertConfigReferencesExist(new Yaml(new SafeConstructor(new LoaderOptions())).load(codeql));
    }

    private static void assertConfigReferencesExist(Object node) {
        if (node instanceof Map<?, ?> mapping) {
            mapping.forEach((key, value) -> {
                if ("config-file".equals(key)) {
                    Path config = PROJECT_ROOT.resolve(value.toString()).normalize();
                    assertTrue(config.startsWith(PROJECT_ROOT) && Files.isRegularFile(config),
                            () -> "Missing local analysis configuration: " + value);
                }
                assertConfigReferencesExist(value);
            });
        } else if (node instanceof List<?> sequence) {
            sequence.forEach(WorkflowContractTest::assertConfigReferencesExist);
        }
    }

    private static void assertValidYaml(String fileName) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document = new Yaml(new SafeConstructor(options)).load(workflow(fileName));
        assertNotNull(document, () -> fileName + " must contain one YAML document");
    }

    private static String workflow(String fileName) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(".github/workflows").resolve(fileName));
    }
}
