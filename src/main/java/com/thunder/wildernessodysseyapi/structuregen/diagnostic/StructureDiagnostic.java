package com.thunder.wildernessodysseyapi.structuregen.diagnostic;

import java.nio.file.Path;

/**
 * One actionable StructureGen diagnostic with source and field/entry context.
 *
 * @param severity diagnostic severity
 * @param source source blueprint or structure, when known
 * @param location JSON field, block index, or structure section
 * @param message human-readable explanation
 */
public record StructureDiagnostic(
        DiagnosticSeverity severity,
        Path source,
        String location,
        String message
) {

    /** Formats this diagnostic for Gradle and command-line output. */
    public String format() {
        String file = source == null ? "<unknown>" : source.toString();
        String context = location == null || location.isBlank() ? "" : System.lineSeparator() + location + ": ";
        return "[StructureGen] " + severity + " " + file + context + message;
    }
}
