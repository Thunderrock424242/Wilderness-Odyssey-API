package com.thunder.wildernessodysseyapi.weather.client.cloud;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Tracks the brief presentation-only glow from an actual synchronized lightning bolt. */
public final class WeatherLightningIllumination {

    private static final long DURATION_NANOS = 240_000_000L;
    private static final double CAMERA_FALLOFF_BLOCKS = 640.0D;

    private static ClientLevel activeLevel;
    private static Vec3 strikePosition = Vec3.ZERO;
    private static long strikeNanos = Long.MIN_VALUE;

    private WeatherLightningIllumination() {
    }

    /** Starts illumination at the authoritative lightning entity position. */
    public static void strike(ClientLevel level, Vec3 position) {
        if (level == null || position == null
                || !Double.isFinite(position.x)
                || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)) {
            return;
        }
        activeLevel = level;
        strikePosition = position;
        strikeNanos = System.nanoTime();
    }

    /** Returns spatial data and a fast 240 ms decay for cloud shaders. */
    public static State current(ClientLevel level) {
        if (level == null || level != activeLevel || strikeNanos == Long.MIN_VALUE) {
            return State.INACTIVE;
        }
        double progress = (System.nanoTime() - strikeNanos) / (double) DURATION_NANOS;
        if (progress >= 1.0D || progress < 0.0D) {
            clear();
            return State.INACTIVE;
        }
        float illumination = (float) ((1.0D - progress) * (1.0D - progress));
        return new State(strikePosition, illumination);
    }

    /** Returns camera-local illumination without making the entire sky flash uniformly. */
    public static float cameraIllumination(ClientLevel level, Vec3 cameraPosition) {
        State state = current(level);
        if (!state.active() || cameraPosition == null) {
            return 0.0F;
        }
        double distance = Math.sqrt(state.position().distanceToSqr(cameraPosition));
        double attenuation = Math.max(0.0D, 1.0D - distance / CAMERA_FALLOFF_BLOCKS);
        return (float) (state.illumination() * attenuation * attenuation);
    }

    /** Clears temporal light history on dimension changes, teleports, and disconnect. */
    public static void clear() {
        activeLevel = null;
        strikePosition = Vec3.ZERO;
        strikeNanos = Long.MIN_VALUE;
    }

    /** Immutable shader-facing lightning state. */
    public record State(Vec3 position, float illumination) {
        public static final State INACTIVE = new State(Vec3.ZERO, 0.0F);

        public State {
            position = position == null ? Vec3.ZERO : position;
            illumination = Float.isFinite(illumination)
                    ? Math.max(0.0F, Math.min(1.0F, illumination))
                    : 0.0F;
        }

        public boolean active() {
            return illumination > 0.001F;
        }
    }
}
