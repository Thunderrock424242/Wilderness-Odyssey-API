package com.thunder.wildernessodysseyapi.debugoverlay;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DebugPageRegistryTest {
    @Test
    void registersNineBuiltInPagesInNavigationOrder() {
        List<String> ids = DebugPageRegistry.pages().stream()
                .map(page -> page.id().getPath())
                .toList();

        assertEquals(List.of(
                "general",
                "world",
                "performance",
                "rendering",
                "system",
                "network",
                "data_engine_debug_metrics",
                "target",
                "vanilla_raw"
        ), ids);
    }

    @Test
    void rejectsDuplicatePageIdentifiers() {
        DebugPage duplicate = new DebugPage() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "general");
            }

            @Override
            public String displayName() {
                return "DUPLICATE";
            }

            @Override
            public List<DebugSection> sections(DebugContext context) {
                return List.of();
            }
        };

        assertThrows(IllegalArgumentException.class, () -> DebugPageRegistry.register(duplicate));
        assertEquals(9, DebugPageRegistry.size());
    }
}
