package com.thunder.wildernessodysseyapi.watersystem.water.api;

/**
 * Describes a server-authoritative water mutation or a simulated mutation.
 *
 * @param outcome reason the request succeeded or was rejected
 * @param requestedUnits requested fixed-point authority units
 * @param transferredUnits units that were or could be transferred
 * @param simulated whether no world state was changed
 */
public record WaterInteractionResult(
        Outcome outcome,
        long requestedUnits,
        long transferredUnits,
        boolean simulated
) {

    /** Creates a validated result for an authority operation. */
    public WaterInteractionResult {
        if (requestedUnits < 0L) {
            throw new IllegalArgumentException("requestedUnits must not be negative");
        }
        if (transferredUnits < 0L || transferredUnits > requestedUnits) {
            throw new IllegalArgumentException("transferredUnits must be within the requested amount");
        }
    }

    /** Returns whether at least some water was accepted or removed. */
    public boolean successful() {
        return transferredUnits > 0L;
    }

    /** Creates the correct complete/partial outcome for a transfer. */
    public static WaterInteractionResult transferred(long requested, long transferred, boolean simulated) {
        Outcome outcome = transferred <= 0L
                ? Outcome.REJECTED
                : transferred >= requested ? Outcome.SUCCESS : Outcome.PARTIAL;
        return new WaterInteractionResult(outcome, requested, transferred, simulated);
    }

    /** Reasons a water interaction may complete or stop. */
    public enum Outcome {
        SUCCESS,
        PARTIAL,
        REJECTED,
        DISABLED,
        CLIENT_READ_ONLY,
        POSITION_UNAVAILABLE
    }
}
