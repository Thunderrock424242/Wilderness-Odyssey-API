package com.thunder.wildernessodysseyapi.changelog.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record GeneratorOptions(
        Path repository,
        Path output,
        String version,
        int firstRunDays,
        boolean dryRun,
        boolean help
) {

    private static final Path DEFAULT_OUTPUT = Path.of(
            "src", "main", "resources", "config", "wildernessodysseyapi", "changelog.txt"
    );
    private static final Pattern VERSION_CONSTANT = Pattern.compile(
            "public\\s+static\\s+final\\s+String\\s+VERSION\\s*=\\s*\"([^\"]+)\""
    );
    private static final Pattern VALID_VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z._+-]*");

    static GeneratorOptions parse(String[] args) throws IOException {
        Path repository = Path.of("").toAbsolutePath().normalize();
        Path output = null;
        String version = null;
        int firstRunDays = 30;
        boolean dryRun = false;
        boolean help = false;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--repo" -> repository = Path.of(requireValue(args, ++index, argument)).toAbsolutePath().normalize();
                case "--output" -> output = Path.of(requireValue(args, ++index, argument));
                case "--version" -> version = requireValue(args, ++index, argument);
                case "--first-run-days" -> firstRunDays = parseDays(requireValue(args, ++index, argument));
                case "--dry-run" -> dryRun = true;
                case "--help", "-h" -> help = true;
                default -> throw new IllegalArgumentException("Unknown option: " + argument + "\n" + usage());
            }
        }

        Path resolvedOutput = output == null ? repository.resolve(DEFAULT_OUTPUT) : repository.resolve(output).normalize();
        String resolvedVersion = version == null ? readPackVersion(repository) : version.trim();
        if (!VALID_VERSION.matcher(resolvedVersion).matches()) {
            throw new IllegalArgumentException("Invalid changelog version: " + resolvedVersion);
        }
        return new GeneratorOptions(repository, resolvedOutput, resolvedVersion, firstRunDays, dryRun, help);
    }

    static String usage() {
        return """
                Generates src/main/resources/config/wildernessodysseyapi/changelog.txt from Git commits.

                Options:
                  --version <version>       Version heading; defaults to ModConstants.VERSION.
                  --first-run-days <days>   First automatic lookback window; defaults to 30.
                  --output <path>           Output path relative to the repository.
                  --repo <path>             Repository root; defaults to the current directory.
                  --dry-run                 Print the generated document without writing it.
                  --help                    Show this help text.
                """;
    }

    private static String readPackVersion(Path repository) throws IOException {
        Path constantsFile = repository.resolve(Path.of(
                "src", "main", "java", "com", "thunder", "wildernessodysseyapi", "core", "ModConstants.java"
        ));
        String source = Files.readString(constantsFile);
        Matcher matcher = VERSION_CONSTANT.matcher(source);
        if (!matcher.find()) {
            throw new IOException("Could not find ModConstants.VERSION in " + constantsFile);
        }
        return matcher.group(1);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static int parseDays(String value) {
        try {
            int days = Integer.parseInt(value);
            if (days < 1 || days > 3650) {
                throw new IllegalArgumentException("--first-run-days must be between 1 and 3650");
            }
            return days;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid --first-run-days value: " + value, exception);
        }
    }
}
