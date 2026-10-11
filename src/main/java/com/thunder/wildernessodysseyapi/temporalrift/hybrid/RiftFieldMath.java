package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

/** Pure, shared presentation/physics math. Clients never apply the gameplay results. */
public final class RiftFieldMath {
    private RiftFieldMath() { }

    public static double intensity(double distance, double radius) {
        if (!Double.isFinite(distance) || !Double.isFinite(radius) || radius <= 0) return 0;
        double t = Math.clamp(1 - Math.max(0, distance) / radius, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Sparse smooth pulses, separated by several seconds of calmer ambience. */
    public static double pulse(double ticks, long offset) {
        double phase = ((ticks + Math.floorMod(offset, 100)) % 100 + 100) % 100;
        return phase < 18 ? Math.pow(Math.sin(Math.PI * phase / 18), 2) : 0;
    }

    /** A personal hit remains briefly visible after a fast crossing or delayed packet leaves the proximity field. */
    public static double presentationIntensity(double proximity, double impact) {
        double nearby = Double.isFinite(proximity) ? Math.clamp(proximity, 0, 1) : 0;
        double hit = Double.isFinite(impact) ? Math.clamp(impact, 0, 1) : 0;
        return Math.max(nearby, hit * 0.25);
    }

    /** Damage is expressed in health points and leaves at least one point before mitigation. */
    public static float impactDamage(double speed, double health, double base, double velocityScale, double maximum) {
        double safeSpeed = Double.isFinite(speed) ? Math.clamp(speed, 0, 20) : 0;
        double damage = Math.min(Math.max(0, maximum), Math.max(0, base) + safeSpeed * Math.max(0, velocityScale));
        return (float) Math.max(0, Math.min(damage, health - 1));
    }
}
