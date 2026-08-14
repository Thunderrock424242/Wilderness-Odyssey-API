package com.thunder.wildernessodysseyapi.lorebook.client.codex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects the compact Codex layout used by high Minecraft GUI scales. */
class CodexLayoutTest {

    @Test
    void bookAndTabsFitInsideHighScaleViewport() {
        int viewportWidth = 410;
        int viewportHeight = 234;
        int bookX = CodexLayout.bookX(viewportWidth);
        int bookY = CodexLayout.bookY(viewportHeight);

        assertTrue(bookX >= CodexLayout.VIEWPORT_MARGIN);
        assertTrue(bookY >= CodexLayout.VIEWPORT_MARGIN);
        assertTrue(bookX + CodexLayout.BOOK_WIDTH <= viewportWidth - CodexLayout.VIEWPORT_MARGIN);
        assertTrue(bookY + CodexLayout.BOOK_HEIGHT <= viewportHeight - CodexLayout.VIEWPORT_MARGIN);

        for (int tabIndex = 0; tabIndex < CodexLayout.TAB_COUNT; tabIndex++) {
            int tabX = CodexLayout.tabX(bookX, tabIndex);
            assertTrue(tabX >= bookX);
            assertTrue(tabX + CodexLayout.TAB_WIDTH <= bookX + CodexLayout.BOOK_WIDTH);
        }
        assertTrue(CodexLayout.tabY(bookY) + CodexLayout.TAB_HEIGHT <= bookY + CodexLayout.PAGE_TOP);
    }
}
