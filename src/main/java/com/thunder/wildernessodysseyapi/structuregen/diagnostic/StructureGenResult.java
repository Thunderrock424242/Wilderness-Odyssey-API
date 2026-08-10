package com.thunder.wildernessodysseyapi.structuregen.diagnostic;

import java.util.List;

/**
 * Immutable value plus all diagnostics produced while creating it.
 *
 * @param value completed value, or {@code null} when errors prevented completion
 * @param diagnostics warnings and errors in discovery order
 */
public record StructureGenResult<T>(T value, List<StructureDiagnostic> diagnostics) {

    public StructureGenResult {
        diagnostics = List.copyOf(diagnostics);
    }

    /** Returns whether at least one diagnostic prevents generation. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    /** Returns the number of warnings in this result. */
    public long warningCount() {
        return diagnostics.stream().filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.WARNING).count();
    }

    /** Returns the number of errors in this result. */
    public long errorCount() {
        return diagnostics.stream().filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR).count();
    }
}
