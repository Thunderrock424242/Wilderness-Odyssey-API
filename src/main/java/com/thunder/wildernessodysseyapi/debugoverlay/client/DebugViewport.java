package com.thunder.wildernessodysseyapi.debugoverlay.client;

/**
 * Describes the clamped window of flattened debug lines visible on one frame.
 *
 * <p>The renderer recalculates this model because GUI scale, window dimensions,
 * column count, and live provider output can all change the available capacity.</p>
 */
record DebugViewport(int offset, int capacity, int totalLines) {
    /** Creates a viewport with its requested offset clamped to the current content. */
    static DebugViewport calculate(int requestedOffset, int totalLines, int capacity) {
        int safeCapacity = Math.max(1, capacity);
        int safeTotalLines = Math.max(0, totalLines);
        int maxOffset = Math.max(0, safeTotalLines - safeCapacity);
        int safeOffset = Math.max(0, Math.min(requestedOffset, maxOffset));
        return new DebugViewport(safeOffset, safeCapacity, safeTotalLines);
    }

    /** Returns the exclusive end index used for the visible line sub-list. */
    int endExclusive() {
        return Math.min(totalLines, offset + capacity);
    }

    /** Returns whether some lines exist outside the current viewport. */
    boolean scrollable() {
        return totalLines > capacity;
    }

    /** Returns the one-based first line number shown in the footer. */
    int firstVisibleLine() {
        return totalLines == 0 ? 0 : offset + 1;
    }

    /** Returns the one-based final line number shown in the footer. */
    int lastVisibleLine() {
        return endExclusive();
    }
}
