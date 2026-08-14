package com.thunder.wildernessodysseyapi.lorebook.client.codex;

/**
 * Centralizes Field Codex geometry so the book and its tabs remain inside the
 * viewport at high Minecraft GUI scales.
 */
final class CodexLayout {
    static final int VIEWPORT_MARGIN = 8;
    static final int BOOK_WIDTH = 388;
    static final int BOOK_HEIGHT = 218;
    static final int PAGE_WIDTH = 152;
    static final int PAGE_HEIGHT = 170;
    static final int PAGE_TOP = 34;
    static final int LEFT_PAGE_X = 34;
    static final int RIGHT_PAGE_X = 207;
    static final int TAB_WIDTH = 62;
    static final int TAB_HEIGHT = 20;
    static final int TAB_GAP = 4;
    static final int TAB_COUNT = 3;
    static final int TAB_TOP = 4;

    private CodexLayout() {
    }

    static int bookX(int viewportWidth) {
        return Math.max(VIEWPORT_MARGIN, (viewportWidth - BOOK_WIDTH) / 2);
    }

    static int bookY(int viewportHeight) {
        return Math.max(VIEWPORT_MARGIN, (viewportHeight - BOOK_HEIGHT) / 2);
    }

    static int tabX(int bookX, int tabIndex) {
        int totalWidth = TAB_COUNT * TAB_WIDTH + (TAB_COUNT - 1) * TAB_GAP;
        return bookX + (BOOK_WIDTH - totalWidth) / 2 + tabIndex * (TAB_WIDTH + TAB_GAP);
    }

    static int tabY(int bookY) {
        return bookY + TAB_TOP;
    }
}
