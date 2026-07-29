package com.thunder.wildernessodysseyapi.changelog.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangelogGeneratorTest {

    private static final Clock JUNE_21_2026 = Clock.fixed(
            Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void classifiesCommitsAndAddsAffectedProjectAreas() {
        Map<ChangeCategory, List<String>> changes = ChangelogClassifier.classify(List.of(
                commit("Add GPU profiler", "src/main/java/com/thunder/wildernessodysseyapi/gpuprofiler/GpuProfiler.java"),
                commit("fixed cloak issue", "src/main/java/com/thunder/wildernessodysseyapi/cloak/CloakState.java"),
                commit("performance tweaks", "src/main/java/com/thunder/wildernessodysseyapi/watersystem/SPH.java"),
                commit("Remove old AI backend", "src/main/java/com/thunder/wildernessodysseyapi/ai/LegacyBackend.java")
        ));

        assertEquals(List.of("Add GPU profiler"), changes.get(ChangeCategory.ADDED));
        assertEquals(List.of("Fixed cloak issue [Cloak]"), changes.get(ChangeCategory.FIXED));
        assertEquals(List.of("Performance tweaks [Water system]"), changes.get(ChangeCategory.CHANGED));
        assertEquals(List.of("Remove old AI backend [AI]"), changes.get(ChangeCategory.REMOVED));
    }

    @Test
    void readsTopLevelBuildGradleVersionByDefault(@TempDir Path repository) throws IOException {
        Files.writeString(repository.resolve("build.gradle"), """
                plugins {
                    id 'java'
                }

                version = "4.2.0"

                publishing {
                    version = 'stale-publication-version'
                }
                """);

        GeneratorOptions options = GeneratorOptions.parse(new String[]{"--repo", repository.toString()});

        assertEquals("4.2.0", options.version());
    }

    @Test
    void explicitVersionStillOverridesBuildGradle(@TempDir Path repository) throws IOException {
        Files.writeString(repository.resolve("build.gradle"), "version = \"4.2.0\"\n");

        GeneratorOptions options = GeneratorOptions.parse(new String[]{
                "--repo", repository.toString(),
                "--version", "4.2.1-rc.1"
        });

        assertEquals("4.2.1-rc.1", options.version());
    }

    @Test
    void firstAutomaticRunUsesThePreviousThirtyDays(@TempDir Path repository) throws IOException {
        Path output = createChangelog(repository, """
                # Wilderness Odyssey Changelog
                ## 0.0.4
                Changed:
                - Manual placeholder

                ## 0.0.3
                Added:
                - Earlier release
                """);
        FakeGitHistory history = new FakeGitHistory("head-commit", List.of(
                commit("Add changelog automation", "src/main/java/com/thunder/wildernessodysseyapi/changelog/Tool.java")
        ));
        GeneratorOptions options = new GeneratorOptions(repository, output, "0.0.4", 30, false, false);

        ChangelogGenerator.GenerationResult result = ChangelogGenerator.generate(options, history, JUNE_21_2026);

        assertEquals("since:2026-05-22", history.requestedBase);
        assertTrue(result.document().contains("Initial automatic snapshot (committed changes since 2026-05-22):"));
        assertTrue(result.document().contains("## 0.0.3"));
        assertFalse(result.document().contains("Manual placeholder"));
        assertTrue(result.document().contains("base=since:2026-05-22 head=head-commit"));
    }

    @Test
    void nextVersionUsesThePreviousGeneratedHead(@TempDir Path repository) throws IOException {
        Path output = createChangelog(repository, """
                # Wilderness Odyssey Changelog
                <!-- changelog-generator version=1.0 previous=0.9 base=since:2026-05-01 head=release-one generated=2026-06-01 -->

                ## 1.0
                Added:
                - First release
                """);
        FakeGitHistory history = new FakeGitHistory("release-two", List.of(
                commit("Fix water rendering", "src/main/java/com/thunder/wildernessodysseyapi/watersystem/Renderer.java")
        ));
        GeneratorOptions options = new GeneratorOptions(repository, output, "1.1", 30, false, false);

        ChangelogGenerator.GenerationResult result = ChangelogGenerator.generate(options, history, JUNE_21_2026);

        assertEquals("release-one", history.requestedBase);
        assertTrue(result.document().contains("Changes from 1.0 to 1.1:"));
        assertTrue(result.document().indexOf("## 1.1") < result.document().indexOf("## 1.0"));
        assertTrue(result.document().contains("base=release-one head=release-two"));
    }

    @Test
    void rerunningTheSameVersionKeepsItsOriginalBaseline(@TempDir Path repository) throws IOException {
        Path output = createChangelog(repository, """
                # Wilderness Odyssey Changelog
                <!-- changelog-generator version=1.1 previous=1.0 base=release-one head=old-head generated=2026-06-10 -->

                ## 1.1
                Changed:
                - Old generated content

                ## 1.0
                Added:
                - First release
                """);
        FakeGitHistory history = new FakeGitHistory("new-head", List.of());
        GeneratorOptions options = new GeneratorOptions(repository, output, "1.1", 30, false, false);

        ChangelogGenerator.GenerationResult result = ChangelogGenerator.generate(options, history, JUNE_21_2026);

        assertEquals("release-one", history.requestedBase);
        assertTrue(result.document().contains("Changes from 1.0 to 1.1:"));
        assertFalse(result.document().contains("Old generated content"));
        assertTrue(result.document().contains("No committed project changes were found"));
    }

    private static ChangelogCommit commit(String subject, String path) {
        return new ChangelogCommit("abcdef", LocalDate.of(2026, 6, 20), subject, List.of(path));
    }

    private static Path createChangelog(Path repository, String content) throws IOException {
        Path output = repository.resolve("changelog.txt");
        Files.writeString(output, content);
        return output;
    }

    private static final class FakeGitHistory implements GitHistory {

        private final String head;
        private final List<ChangelogCommit> commits;
        private String requestedBase;

        private FakeGitHistory(String head, List<ChangelogCommit> commits) {
            this.head = head;
            this.commits = commits;
        }

        @Override
        public String currentHead() {
            return head;
        }

        @Override
        public List<ChangelogCommit> readCommits(String baseReference, String headCommit) {
            requestedBase = baseReference;
            return commits;
        }

        @Override
        public boolean hasTrackedChanges() {
            return false;
        }
    }
}
