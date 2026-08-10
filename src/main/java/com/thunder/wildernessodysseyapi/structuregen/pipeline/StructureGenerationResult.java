package com.thunder.wildernessodysseyapi.structuregen.pipeline;

import com.thunder.wildernessodysseyapi.structuregen.diagnostic.DiagnosticSeverity;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;

import java.util.List;

/** Aggregate blueprint discovery, validation, generation, and diagnostic totals. */
public record StructureGenerationResult(
        int blueprintsFound,
        int validated,
        List<GeneratedStructure> generated,
        List<StructureDiagnostic> diagnostics
) {

    public StructureGenerationResult {
        generated = List.copyOf(generated);
        diagnostics = List.copyOf(diagnostics);
    }

    public long warningCount() {
        return diagnostics.stream().filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.WARNING).count();
    }

    public long errorCount() {
        return diagnostics.stream().filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR).count();
    }

    public boolean successful() {
        return errorCount() == 0L && generated.size() == validated;
    }
}
