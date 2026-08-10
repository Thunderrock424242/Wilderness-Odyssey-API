package com.thunder.wildernessodysseyapi.structuregen.pipeline;

import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparisonReport;

import java.nio.file.Path;

/** Successfully published and re-read generated structure. */
public record GeneratedStructure(
        String name,
        Path output,
        StructureComparisonReport verification
) {
}
