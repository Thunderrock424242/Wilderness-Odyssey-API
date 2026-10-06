package com.thunder.wildernessodysseyapi.loading;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadingModAttributionTest {
    @Test
    void resolvesAnOwnerWithoutLoadingTheNamedClass() {
        var resolver = new LoadingModAttribution(Map.of("terrain.module", "terrainmod"));
        var frame = new StackTraceElement("loader", "terrain.module", "1.0",
                "not.loaded.or.present.Generator", "generate", "Generator.java", 12);
        assertEquals("terrainmod", resolver.apply(frame));
    }

    @Test
    void preservesSharedJarAttributionAndUnknownFrames() {
        var resolver = new LoadingModAttribution(Map.of("shared.module", "mod_a, mod_b (shared module)"));
        var frame = new StackTraceElement("loader", "shared.module", "1.0",
                "example.Generator", "generate", "Generator.java", 12);
        assertEquals("mod_a, mod_b (shared module)", resolver.apply(frame));
        assertEquals("unresolved", resolver.apply(new StackTraceElement("example.Unknown", "run", "Unknown.java", 1)));
        assertEquals("minecraft", resolver.apply(new StackTraceElement("net.minecraft.Example", "run", "Example.java", 1)));
    }
}
