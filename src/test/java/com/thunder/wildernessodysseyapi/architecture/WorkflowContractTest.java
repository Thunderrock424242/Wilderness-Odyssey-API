package com.thunder.wildernessodysseyapi.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects the repository's build, security-analysis, and publishing gates. */
class WorkflowContractTest {
    private static final Path PROJECT_ROOT = Path.of(System.getProperty(
            "wildernessodysseyapi.projectDir", ".")).toAbsolutePath().normalize();

    @Test
    void workflowYamlParsesWithoutDuplicateMappings() throws IOException {
        assertValidYaml("build.yml");
        assertValidYaml("codeql.yml");
        assertValidYaml("publish.yml");
    }

    @Test
    void buildAndPublishRoutesRequireTheFullVerificationGate() throws IOException {
        String build = workflow("build.yml");
        assertTrue(build.contains("pull_request:"));
        assertTrue(build.contains("./gradlew build -PcodexBuildDir=.ci-build"));
        assertTrue(build.contains("git diff --exit-code"));

        String publish = workflow("publish.yml");
        int buildGate = publish.indexOf("./gradlew build -PcodexBuildDir=.release-build");
        int publishStep = publish.indexOf("./gradlew publish -PcodexBuildDir=.release-build");
        assertTrue(publish.contains("release:"));
        assertTrue(buildGate >= 0 && publishStep > buildGate);
        assertTrue(publish.contains("secrets.GITHUB_TOKEN"));
        assertFalse(publish.contains("secrets.GIT_TOKEN"));
    }

    @Test
    void securityWorkflowUsesCurrentActionsAndNoMissingConfigReference() throws IOException {
        String codeql = workflow("codeql.yml");
        assertTrue(codeql.contains("github/codeql-action/init@v4"));
        assertTrue(codeql.contains("github/codeql-action/analyze@v4"));
        assertTrue(codeql.contains("github/codeql-action/upload-sarif@v4"));
        assertTrue(codeql.contains("JetBrains/qodana-action@v2026.2"));
        assertFalse(codeql.contains("config-file:"));
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
