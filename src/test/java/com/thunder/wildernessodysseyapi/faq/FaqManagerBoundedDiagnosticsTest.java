package com.thunder.wildernessodysseyapi.faq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies no-result diagnostics cannot retain unbounded user-controlled strings. */
class FaqManagerBoundedDiagnosticsTest {

    @AfterEach
    void clearFaqState() {
        FaqManager.clear();
    }

    @Test
    void capsUniqueQueriesAndTruncatesStoredKeys() {
        for (int index = 0; index < 400; index++) {
            FaqManager.recordNoResultQuery("query-" + index + "-" + "x".repeat(300));
        }

        assertEquals(256, FaqManager.getNoResultQueries().size());
        assertTrue(FaqManager.getNoResultQueries().keySet().stream().allMatch(key -> key.length() <= 160));
    }
}
