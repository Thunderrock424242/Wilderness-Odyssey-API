package com.thunder.wildernessodysseyapi.ocean.events;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;

public class WaterSystem {
    private static float waveTime = 0.0f;
    private static float tideTime = 0.0f;

    /** Call once per client-tick. */
    public static void tick(float delta) {
        waveTime += delta * 2.0f;    // fast ripples
        tideTime += delta * 0.01f;   // slow tide cycle
    }

    /** A sinusoidal ±1-block tide offset. */
    public static float getTideOffset() {
        return (float) Math.sin(tideTime * Math.PI * 2.0);
    }

    /** Combined wave+tide height at world (x,z). */
    public static double getCombinedHeight(double x, double z) {
        double base = getTideOffset();
        double w1 = Math.sin(x * 0.1 + waveTime * 0.05) * 0.5;
        double w2 = Math.sin(z * 0.15 + waveTime * 0.08) * 0.3;
        double w3 = Math.sin((x + z) * 0.2 + waveTime * 0.1) * 0.2;
        return base + w1 + w2 + w3;
    }

    /** Only boats ride waves/tides; fish/squid are unaffected. */
    public static void applyWaveForces(Entity e) {
        if (!(e instanceof Boat boat)) return;
        double x = boat.getX(), z = boat.getZ(), y = boat.getY();
        double target = Math.floor(y) + 1 + getCombinedHeight(x, z);
        double delta = target - y;
        var vel = boat.getDeltaMovement();
        boat.setDeltaMovement(vel.x, vel.y + delta * 0.2, vel.z);
    }

    // Exposed for WaveRenderer to set its uniforms.
    public static float getWaveTime() { return waveTime; }
}
