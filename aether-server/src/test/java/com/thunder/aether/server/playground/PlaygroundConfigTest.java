package com.thunder.aether.server.playground;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaygroundConfigTest {
    @Test void emptyConfigurationDoesNotClaimReadiness() {
        var c=PlaygroundConfig.load(Map.of(),Path.of("."));
        assertFalse(c.sandboxEnabled()); assertFalse(c.trainingEnabled()); assertTrue(c.models().isEmpty());
    }
    @Test void rejectsUnknownPresetAndDuplicateModels() {
        assertThrows(IllegalArgumentException.class,()->PlaygroundConfig.load(Map.of("presets",List.of("arbitrary-shell")),Path.of(".")));
        var model=Map.of("id","tiny:local","label","Tiny");
        assertThrows(IllegalArgumentException.class,()->PlaygroundConfig.load(Map.of("models",List.of(model,model)),Path.of(".")));
    }
}
