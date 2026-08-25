package com.thunder.wildernessodysseyapi.ecosystem.debug.map.client;

import com.thunder.wildernessodysseyapi.ecosystem.debug.map.EcosystemDebugMapPayload;

/** Client-owned handoff for one server-authorized debug-map snapshot. */
public final class EcosystemDebugMapClientState {
    private static EcosystemDebugMapPayload snapshot;
    private static boolean openRequested;

    private EcosystemDebugMapClientState() {
    }

    /** Replaces the prior map atomically and requests a screen open or refresh. */
    public static void accept(EcosystemDebugMapPayload payload) {
        snapshot = payload;
        openRequested = true;
    }

    public static EcosystemDebugMapPayload snapshot() {
        return snapshot;
    }

    /** Consumes one pending screen-open request. */
    public static boolean consumeOpenRequest() {
        if (!openRequested) {
            return false;
        }
        openRequested = false;
        return true;
    }

    /** Clears server-scoped map data on disconnect. */
    public static void clear() {
        snapshot = null;
        openRequested = false;
    }
}
