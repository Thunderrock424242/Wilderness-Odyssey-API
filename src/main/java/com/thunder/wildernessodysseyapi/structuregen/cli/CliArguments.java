package com.thunder.wildernessodysseyapi.structuregen.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Minimal strict parser for StructureGen's Gradle-facing command-line options. */
final class CliArguments {

    private final String command;
    private final Map<String, String> options;

    private CliArguments(String command, Map<String, String> options) {
        this.command = command;
        this.options = Map.copyOf(options);
    }

    static CliArguments parse(String[] arguments) {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("Missing StructureGen command. Expected generate, inspect, export, or compare.");
        }
        String command = arguments[0];
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 1; index < arguments.length; index += 2) {
            String option = arguments[index];
            if (!option.startsWith("--") || option.length() == 2) {
                throw new IllegalArgumentException("Expected --option at argument " + index + ", got '" + option + "'.");
            }
            if (index + 1 >= arguments.length) {
                throw new IllegalArgumentException("Missing value for option " + option + ".");
            }
            String previous = options.put(option.substring(2), arguments[index + 1]);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate option " + option + ".");
            }
        }
        return new CliArguments(command, options);
    }

    String command() {
        return command;
    }

    String require(String option) {
        String value = options.get(option);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + option + ".");
        }
        return value;
    }

    String optional(String option) {
        return options.get(option);
    }

    void requireOnly(Set<String> allowed) {
        for (String option : options.keySet()) {
            if (!allowed.contains(option)) {
                throw new IllegalArgumentException("Unsupported option --" + option + " for command " + command + ".");
            }
        }
    }
}
