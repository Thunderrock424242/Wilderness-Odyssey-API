package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/** Exact high-precision water movements recorded during one regional step. */
public record HydrologicFlux(
        long precipitation,
        long snowAccumulation,
        long snowMelt,
        long infiltration,
        long surfaceRunoff,
        long evapotranspiration,
        long groundwaterRecharge,
        long groundwaterDischarge,
        long riverInflow,
        long riverOutflow,
        long floodplainExchange,
        long lakeExchange
) {
    public static final HydrologicFlux ZERO = new HydrologicFlux(
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L
    );

    public HydrologicFlux {
        precipitation = nonNegative(precipitation);
        snowAccumulation = nonNegative(snowAccumulation);
        snowMelt = nonNegative(snowMelt);
        infiltration = nonNegative(infiltration);
        surfaceRunoff = nonNegative(surfaceRunoff);
        evapotranspiration = nonNegative(evapotranspiration);
        groundwaterRecharge = nonNegative(groundwaterRecharge);
        groundwaterDischarge = nonNegative(groundwaterDischarge);
        riverInflow = nonNegative(riverInflow);
        riverOutflow = nonNegative(riverOutflow);
        // Net lake/floodplain exchange is signed; recession must remain visible.
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }
}
