package com.thunder.wildernessodysseyapi.weather.simulation;

/** Shared conversions at the physical atmosphere / legacy presentation boundary. */
public final class AtmosphericUnits {
    public static final double SECONDS_PER_TICK = 0.05;
    public static final double REFERENCE_STEP_SECONDS = 2.0;
    public static final double REFERENCE_CELL_METRES = 256.0;
    public static final double REFERENCE_PRESSURE_HPA = 1013.25;
    public static final double PRESSURE_SCALE_HPA = 100.0;
    public static final double WIND_SCALE_METRES_PER_SECOND = 40.0;
    public static final double VAPOR_SCALE_KG_PER_SQUARE_METRE = 25.0;
    public static final double CLOUD_SCALE_KG_PER_SQUARE_METRE = 2.0;
    public static final double RAIN_SCALE_MM_PER_HOUR = 50.0;
    public static final double MAX_WIND_METRES_PER_SECOND = 60.0;

    private AtmosphericUnits() { }

    /** One block is one metre; one kg/m2 of liquid water is exactly one mm SWE. */
    public static double waterVolumeCubicMetres(double millimetres, double areaSquareMetres) {
        return millimetres * areaSquareMetres / 1000.0;
    }

    /** Exponential response avoids a per-update rate changing with scheduler cadence. */
    public static double response(double seconds, double timeConstantSeconds) {
        return -Math.expm1(-Math.max(0.0, seconds) / Math.max(0.001, timeConstantSeconds));
    }

    /** Converts an old nominal response fraction into an elapsed-time response. */
    public static double legacyResponse(double fraction, double seconds) {
        return fraction >= 1.0 ? (seconds > 0.0 ? 1.0 : 0.0)
                : -Math.expm1(Math.log1p(-unit(fraction)) * Math.max(0.0, seconds) / REFERENCE_STEP_SECONDS);
    }

    public static double unit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    public static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, Double.isFinite(value) ? value : low));
    }
}
