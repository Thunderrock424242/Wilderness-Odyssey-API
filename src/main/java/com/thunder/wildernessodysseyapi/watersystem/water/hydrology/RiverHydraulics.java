package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/** Lightweight rectangular-channel Manning hydraulics; metres, seconds and cubic metres. */
public final class RiverHydraulics {
    private RiverHydraulics() { }

    public static Result evaluate(double volume, double contributingArea, double bedSlope,
                                  double roughness, double bankfullDepth) {
        double width = Math.max(1.0, Math.min(16.0, Math.sqrt(Math.max(256.0, contributingArea)) / 16.0));
        double depth = Math.max(0, volume) / (16.0 * width);
        double area = width * depth;
        double radius = area / Math.max(0.001, width + 2.0 * depth);
        double velocity = Math.min(8.0, Math.pow(radius, 2.0 / 3.0)
                * Math.sqrt(Math.max(0.0, bedSlope)) / Math.max(0.015, roughness));
        return new Result(width, depth, velocity, area * velocity, width * 16.0 * bankfullDepth);
    }

    public record Result(double width, double depth, double velocity,
                         double discharge, double bankfullVolume) { }
}
