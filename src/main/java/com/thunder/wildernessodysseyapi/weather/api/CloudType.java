package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Meteorological cloud genus derived from continuous atmospheric state.
 *
 * <p>The genus is intentionally not persisted or synchronized. Both the
 * server diagnostics and client renderer derive it from the same immutable
 * weather fields, so cloud labels cannot become stale or contradict the
 * authoritative atmosphere.</p>
 */
public enum CloudType {
    CLEAR("Clear", Shape.CLEAR),
    CIRRUS("Cirrus", Shape.WISPY),
    CIRROSTRATUS("Cirrostratus", Shape.WISPY),
    CIRROCUMULUS("Cirrocumulus", Shape.CELLULAR),
    ALTOSTRATUS("Altostratus", Shape.LAYERED),
    ALTOCUMULUS("Altocumulus", Shape.CELLULAR),
    STRATUS("Stratus", Shape.LAYERED),
    STRATOCUMULUS("Stratocumulus", Shape.CELLULAR),
    CUMULUS("Cumulus", Shape.CELLULAR),
    NIMBOSTRATUS("Nimbostratus", Shape.LAYERED),
    CUMULONIMBUS("Cumulonimbus", Shape.CONVECTIVE);

    private final String displayName;
    private final Shape shape;

    CloudType(String displayName, Shape shape) {
        this.displayName = displayName;
        this.shape = shape;
    }

    /** Returns a player-facing meteorological name. */
    public String displayName() {
        return displayName;
    }

    /** Returns the broad morphology used by client cloud rendering. */
    public Shape shape() {
        return shape;
    }

    /** Broad visual morphology shared by related cloud genera. */
    public enum Shape {
        CLEAR,
        WISPY,
        LAYERED,
        CELLULAR,
        CONVECTIVE
    }
}
