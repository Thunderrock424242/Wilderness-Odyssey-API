package com.thunder.wildernessodysseyapi.changelog.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Generates the bundled in-game changelog from committed Git history.
 *
 * <p>The first automatic run includes the configured lookback window, which
 * defaults to 30 days. Generated metadata records the baseline and HEAD commit
 * so later versions can describe exactly what changed between releases.</p>
 */
public final class ChangelogGenerator {

    private ChangelogGenerator() {
    }

    /**
     * Parses command-line options and updates the configured changelog resource.
     *
     * @param args generator options; use {@code --help} to print supported arguments
     */
    public static void main(String[] args) {
        try {
            GeneratorOptions options = GeneratorOptions.parse(args);
            if (options.help()) {
                System.out.println(GeneratorOptions.usage());
                return;
            }

            GenerationResult result = generate(options, new GitHistoryReader(options.repository()),
                    Clock.systemDefaultZone());
            if (options.dryRun()) {
                System.out.print(result.document());
                return;
            }

            Files.createDirectories(options.output().getParent());
            Files.writeString(options.output(), result.document(), StandardCharsets.UTF_8);
            System.out.println("Generated changelog " + options.version() + " from " + result.commitCount()
                    + " committed change(s) at " + options.output());
            System.out.println("Range: " + result.rangeDescription());
        } catch (IllegalArgumentException | IOException exception) {
            System.err.println("Unable to generate changelog: " + exception.getMessage());
            System.exit(1);
        }
    }

    // Kept package-private so the release-range behavior can be tested without invoking Git.
    static GenerationResult generate(GeneratorOptions options, GitHistory history, Clock clock) throws IOException {
        String existingDocument = Files.exists(options.output())
                ? Files.readString(options.output(), StandardCharsets.UTF_8)
                : "# Wilderness Odyssey Changelog\n";
        ChangelogDocument document = ChangelogDocument.parse(existingDocument);
        ChangelogState previousState = document.state().orElse(null);
        LocalDate generatedDate = LocalDate.now(clock);

        String previousVersion = document.previousVersion(options.version(), previousState).orElse(null);
        String baseReference = resolveBaseReference(options, previousState, generatedDate);
        String headCommit = history.currentHead();
        List<ChangelogCommit> commits = history.readCommits(baseReference, headCommit);
        Map<ChangeCategory, List<String>> changes = ChangelogClassifier.classify(commits);

        String rangeDescription = describeRange(baseReference, previousVersion, options.version());
        String section = ChangelogDocument.renderSection(options.version(), rangeDescription, changes);
        ChangelogState newState = new ChangelogState(
                options.version(),
                previousVersion,
                baseReference,
                headCommit,
                generatedDate
        );

        if (history.hasTrackedChanges()) {
            System.err.println("Warning: tracked working-tree changes are not included; commit them before a release run.");
        }

        return new GenerationResult(document.withGeneratedSection(section, options.version(), newState),
                commits.size(), rangeDescription);
    }

    private static String resolveBaseReference(GeneratorOptions options, ChangelogState state, LocalDate generatedDate) {
        if (state == null) {
            return "since:" + generatedDate.minusDays(options.firstRunDays());
        }
        if (state.version().equals(options.version())) {
            return state.baseReference();
        }
        return state.headCommit();
    }

    private static String describeRange(String baseReference, String previousVersion, String currentVersion) {
        if (baseReference.startsWith("since:")) {
            return "Initial automatic snapshot (committed changes since "
                    + baseReference.substring("since:".length()) + "):";
        }
        if (previousVersion != null) {
            return "Changes from " + previousVersion + " to " + currentVersion + ":";
        }
        return "Committed changes included in " + currentVersion + ":";
    }

    record GenerationResult(String document, int commitCount, String rangeDescription) {
    }
}
