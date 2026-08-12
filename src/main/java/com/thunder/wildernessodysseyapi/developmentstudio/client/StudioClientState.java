package com.thunder.wildernessodysseyapi.developmentstudio.client;

import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioPayload;

/** Client-side cache for the latest server-authorized Studio snapshot. */
public final class StudioClientState {
    private static OpenStudioPayload snapshot;
    private static boolean openRequested;

    private StudioClientState() {
    }

    /** Accepts one complete server snapshot and requests a safe screen refresh. */
    public static void accept(OpenStudioPayload payload) {
        snapshot = payload;
        openRequested = true;
    }

    public static OpenStudioPayload snapshot() {
        return snapshot;
    }

    /** Consumes one pending open/refresh request. */
    public static boolean consumeOpenRequest() {
        if (!openRequested) {
            return false;
        }
        openRequested = false;
        return true;
    }

    /** Clears server-scoped metadata on disconnect. */
    public static void clear() {
        snapshot = null;
        openRequested = false;
    }
}
