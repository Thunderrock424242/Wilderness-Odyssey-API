package com.thunder.wildernessodysseyapi.lorebook;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Client-side cache for server-owned Codex writing and lore-journal unlocks. */
public final class CodexClientState {
    private static final Set<String> COLLECTED_LORE_IDS = new HashSet<>();
    private static String journalText = "";
    private static boolean openRequested;

    private CodexClientState() {
    }

    /** Adds one server-confirmed lore journal id to the local collection index. */
    public static void markCollected(String bookId) {
        if (bookId != null && !bookId.isBlank()) {
            COLLECTED_LORE_IDS.add(bookId);
        }
    }

    /** Returns whether the server has confirmed that this lore journal was collected. */
    public static boolean hasCollected(String bookId) {
        return bookId != null && COLLECTED_LORE_IDS.contains(bookId);
    }

    /** Returns the immutable set of lore journal ids currently known by the client. */
    public static Set<String> collectedLoreIds() {
        return Collections.unmodifiableSet(COLLECTED_LORE_IDS);
    }

    /** Replaces the local editable journal with the server-owned value. */
    public static void syncJournal(String text) {
        journalText = CodexJournalText.sanitize(text);
    }

    /** Returns the journal text received from the server. */
    public static String journalText() {
        return journalText;
    }

    /** Requests that the client open the Codex on its next safe client tick. */
    public static void requestOpen() {
        openRequested = true;
    }

    /** Consumes one pending screen-open request. */
    public static boolean consumeOpenRequest() {
        if (!openRequested) {
            return false;
        }
        openRequested = false;
        return true;
    }

    /** Clears all server-scoped Codex state after disconnecting. */
    public static void clear() {
        COLLECTED_LORE_IDS.clear();
        journalText = "";
        openRequested = false;
    }
}
