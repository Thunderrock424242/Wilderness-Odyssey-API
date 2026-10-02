package com.thunder.wildernessodysseyapi.tools.structureviewer.assets;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import java.util.*;

/** Detached model snapshot: layer changes reuse textures and geometry without reopening any archives. */
public record ResolvedModels(Map<StructureData.BlockState, BlockModel> models, List<String> diagnostics,
                             int resolved, int missing, List<String> sources) {
    public ResolvedModels {
        models = Map.copyOf(models); diagnostics = List.copyOf(diagnostics); sources = List.copyOf(sources);
    }
    public static ResolvedModels capture(StructureData data, BlockModelResolver resolver, List<String> sources) {
        Map<StructureData.BlockState, BlockModel> models = new HashMap<>();
        for (var palette : data.palettes()) for (var state : palette) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            models.computeIfAbsent(state,resolver::resolve);
        }
        return new ResolvedModels(models,resolver.diagnostics(),resolver.resolvedCount(),resolver.missingCount(),sources);
    }
}
