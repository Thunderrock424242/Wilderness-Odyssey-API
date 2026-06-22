package com.thunder.wildernessodysseyapi.changelog.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class GitHistoryReader implements GitHistory {

    private static final String GENERATED_CHANGELOG_PATH =
            "src/main/resources/config/wildernessodysseyapi/changelog.txt";

    private final Path repository;

    GitHistoryReader(Path repository) throws IOException {
        this.repository = repository;
        String root = runGit("rev-parse", "--show-toplevel").trim();
        if (!Path.of(root).toAbsolutePath().normalize().equals(repository.toAbsolutePath().normalize())) {
            throw new IOException("--repo must point to the Git repository root: " + root);
        }
    }

    @Override
    public String currentHead() throws IOException {
        return runGit("rev-parse", "HEAD").trim();
    }

    @Override
    public List<ChangelogCommit> readCommits(String baseReference, String headCommit) throws IOException {
        List<String> arguments = new ArrayList<>(List.of(
                "log",
                "--no-merges",
                "--date=short",
                "--pretty=format:%H%x1f%cs%x1f%s%x1e"
        ));
        if (baseReference.startsWith("since:")) {
            arguments.add("--since=" + baseReference.substring("since:".length()) + "T00:00:00Z");
            arguments.add(headCommit);
        } else {
            arguments.add(baseReference + ".." + headCommit);
        }

        String output = runGit(arguments.toArray(String[]::new));
        List<ChangelogCommit> commits = new ArrayList<>();
        for (String record : output.split("\\u001e")) {
            String cleaned = record.strip();
            if (cleaned.isEmpty()) {
                continue;
            }
            String[] fields = cleaned.split("\\u001f", 3);
            if (fields.length != 3) {
                throw new IOException("Unexpected Git log record: " + cleaned);
            }

            List<String> paths = readChangedPaths(fields[0]);
            if (paths.size() == 1 && paths.getFirst().equals(GENERATED_CHANGELOG_PATH)) {
                continue;
            }
            commits.add(new ChangelogCommit(fields[0], LocalDate.parse(fields[1]), fields[2].trim(), paths));
        }
        return List.copyOf(commits);
    }

    @Override
    public boolean hasTrackedChanges() throws IOException {
        return !runGit("status", "--porcelain", "--untracked-files=no").isBlank();
    }

    private List<String> readChangedPaths(String commit) throws IOException {
        String output = runGit("diff-tree", "--root", "--no-commit-id", "--name-only", "-r", commit);
        return output.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    private String runGit(String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));

        Process process = new ProcessBuilder(command)
                .directory(repository.toFile())
                .redirectErrorStream(true)
                .start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git command failed (" + String.join(" ", command) + "): " + output.trim());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Git", exception);
        }
        return output;
    }
}
