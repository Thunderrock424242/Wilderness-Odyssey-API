package com.thunder.wildernessodysseyapi.developmentstudio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Verifies that client-provided Studio text remains bounded and formatting-safe. */
class StudioTextTest {

    @Test
    void singleLineRemovesFormattingControlsAndBoundsSurrogatePairs() {
        String result = StudioText.singleLine("  River\nTest\u00A7c\u0000😀😀  ", 18);

        assertFalse(result.contains("\n"));
        assertFalse(result.contains("\u00A7"));
        assertFalse(result.contains("\u0000"));
        assertFalse(Character.isHighSurrogate(result.charAt(result.length() - 1)));
    }

    @Test
    void tagsAreTrimmedDeduplicatedAndLimited() {
        List<String> tags = StudioText.tags(List.of(
                " river ", "river", "chunk-border", "one", "two", "three", "four", "five", "six", "seven"
        ));

        assertEquals(StudioText.MAX_TAGS, tags.size());
        assertEquals("river", tags.getFirst());
        assertEquals(1, tags.stream().filter("river"::equals).count());
    }
}
