package com.thunder.wildernessodysseyapi.lorebook;

import com.thunder.wildernessodysseyapi.lorebook.map.CodexMapPoi;
import com.thunder.wildernessodysseyapi.lorebook.map.CodexMapSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Small client-side cache for codex unlocks delivered by the server.
 */
public final class CodexClientState {
    private static final Set<String> COLLECTED_LORE_IDS = new HashSet<>();
    private static final List<CodexMapPoi> MAP_POIS = new ArrayList<>();
    private static CodexMapSettings mapSettings;
    private static boolean openRequested;

    private CodexClientState() {
    }

    public static void markCollected(String bookId) {
        if (bookId != null && !bookId.isBlank()) {
            COLLECTED_LORE_IDS.add(bookId);
        }
    }

    public static boolean hasCollected(String bookId) {
        return bookId != null && COLLECTED_LORE_IDS.contains(bookId);
    }

    public static Set<String> collectedLoreIds() {
        return Collections.unmodifiableSet(COLLECTED_LORE_IDS);
    }

    public static void syncMap(CodexMapSettings settings, List<CodexMapPoi> pois) {
        mapSettings = settings;
        MAP_POIS.clear();
        MAP_POIS.addAll(pois);
    }

    public static Optional<CodexMapSettings> mapSettings() {
        return Optional.ofNullable(mapSettings);
    }

    public static List<CodexMapPoi> mapPois() {
        return List.copyOf(MAP_POIS);
    }

    public static void requestOpen() {
        openRequested = true;
    }

    public static boolean consumeOpenRequest() {
        if (!openRequested) {
            return false;
        }
        openRequested = false;
        return true;
    }

    public static void clear() {
        COLLECTED_LORE_IDS.clear();
        MAP_POIS.clear();
        mapSettings = null;
        openRequested = false;
    }
}
