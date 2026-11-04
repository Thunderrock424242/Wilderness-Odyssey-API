package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/** Read-only local jet and impact arithmetic; quantities remain in the canonical water owner. */
public final class WaterfallHydraulics {
    private WaterfallHydraulics() { }

    /** Samples metres/second relative to a verified wet fall's center and receiving surface. */
    public static Motion sample(double top, double pool, double x, double y, double z,
                                float inflowX, float inflowZ, float fallingY, double tick) {
        if (!Double.isFinite(top + pool + x + y + z + tick)
                || !Float.isFinite(inflowX) || !Float.isFinite(inflowZ) || !Float.isFinite(fallingY)
                || top - pool < .5 || top - pool > 16 || y > top || y < pool - 2) return Motion.STILL;
        double inflow = Math.max(Math.hypot(inflowX, inflowZ), Math.max(0, -fallingY));
        if (inflow < .02) return Motion.STILL;
        double drop = top - pool;
        double speed = Math.min(8, Math.sqrt(inflow * inflow + 2 * 9.81 * Math.max(0, top - y)));
        if (y > pool + .4) {
            if (Math.abs(x) > .5 || Math.abs(z) > .5) return Motion.STILL;
            return new Motion(0, (float) -speed, 0, (float) Math.min(1, speed / 8));
        }
        double radius = Math.min(2, .65 + Math.sqrt(drop) * .25);
        double distance = Math.hypot(x, z);
        if (distance >= radius) return Motion.STILL;
        double weight = Math.pow(1 - distance / radius, 2) * Math.exp(-Math.abs(y - pool) / .8);
        double impact = Math.min(1.5, Math.sqrt(inflow * inflow + 2 * 9.81 * drop) * .12);
        // A small rotating component produces an eddy beside the outward jet.
        // Reduce time before trigonometry so long-lived worlds retain precision.
        double phase = (tick % 2400) * (Math.PI * 2 / 2400);
        double swirl = Math.sin(phase + x * 2.1 + z * 1.7) * .25;
        double inverse = distance > .001 ? 1 / (distance * Math.sqrt(1 + swirl * swirl)) : 0;
        return new Motion((float) ((x - z * swirl) * inverse * impact * weight),
                (float) (-Math.min(.6, impact * .4) * weight),
                (float) ((z + x * swirl) * inverse * impact * weight),
                (float) Math.min(1, impact * weight));
    }

    public record Motion(float x, float y, float z, float foam) {
        public static final Motion STILL = new Motion(0, 0, 0, 0);
    }
}
