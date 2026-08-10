package com.thunder.wildernessodysseyapi.debugoverlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugPresentationModelTest {
    @Test
    void semanticFactoriesKeepToneSeparateFromText() {
        assertEquals(DebugValue.Tone.GOOD, DebugValue.good("healthy").tone());
        assertEquals(DebugValue.Tone.WARNING, DebugValue.warning("slow").tone());
        assertEquals(DebugValue.Tone.ERROR, DebugValue.error("failed").tone());
        assertEquals(DebugValue.Tone.UNAVAILABLE, DebugValue.unavailable().tone());
    }

    @Test
    void sectionsExposeImmutableOrderedEntries() {
        DebugSection section = DebugSection.builder("WORLD")
                .add("Dimension", "minecraft:overworld")
                .add("Light", DebugValue.good(15))
                .build();

        assertEquals("Dimension", section.entries().getFirst().label());
        assertEquals("Light", section.entries().getLast().label());
        assertThrows(UnsupportedOperationException.class,
                () -> section.entries().add(DebugEntry.of("Extra", "value")));
    }

    @Test
    void rawLinesRemainDistinctFromAlignedEntries() {
        DebugEntry line = DebugEntry.raw("Minecraft 1.21.1");

        assertTrue(line.raw());
        assertEquals("", line.label());
        assertEquals("Minecraft 1.21.1", line.value().text());
    }
}
