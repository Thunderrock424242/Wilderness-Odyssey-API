package com.thunder.wildernessodysseyapi.weather.api;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Immutable wind result shared by server gameplay and client effects.
 *
 * <p>Direction is a normalized Minecraft-space vector: positive X is east,
 * positive Z is south, and Y carries bounded convective or turbulent motion.
 * Speeds use blocks per second. The current gust is additive to the sustained
 * speed, so consumers can choose stable motion or the full effective wind.</p>
 *
 * @param direction normalized three-dimensional wind direction
 * @param speed sustained wind speed in blocks per second
 * @param gust current additive gust speed in blocks per second
 * @param weatherContribution sustained speed contributed by localized weather
 * @param gustFactor normalized current regional gust envelope
 * @param gustPhase normalized phase of the containing region's gust cycle
 * @param gustCycle deterministic containing-region gust cycle number
 * @param region containing atmospheric cell used for diagnostics
 */
public record WindSample(
        Vec3 direction,
        float speed,
        float gust,
        float weatherContribution,
        float gustFactor,
        float gustPhase,
        long gustCycle,
        AtmosphereCellKey region
) {
    public WindSample {
        direction = sanitizeDirection(direction);
        speed = nonNegative(speed);
        gust = nonNegative(gust);
        weatherContribution = Math.min(speed, nonNegative(weatherContribution));
        gustFactor = unit(gustFactor);
        gustPhase = unit(gustPhase);
        gustCycle = Math.max(0L, gustCycle);
        region = Objects.requireNonNullElse(region, new AtmosphereCellKey(0, 0));
        if (speed + gust <= 1.0E-5F) {
            direction = Vec3.ZERO;
        }
    }

    /** Returns a zero-speed sample retaining the requested diagnostic region. */
    public static WindSample calm(AtmosphereCellKey region) {
        return new WindSample(Vec3.ZERO, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0L, region);
    }

    /** Returns sustained speed plus the currently active coherent gust. */
    public float effectiveSpeed() {
        return speed + gust;
    }

    /** Returns the full wind velocity in blocks per second. */
    public Vec3 velocity() {
        return direction.scale(effectiveSpeed());
    }

    /** Returns the full wind velocity converted to blocks per game tick. */
    public Vec3 velocityPerTick() {
        return direction.scale(effectiveSpeed() / 20.0F);
    }

    private static Vec3 sanitizeDirection(Vec3 direction) {
        Vec3 value = Objects.requireNonNullElse(direction, Vec3.ZERO);
        if (!Double.isFinite(value.x)
                || !Double.isFinite(value.y)
                || !Double.isFinite(value.z)
                || value.lengthSqr() <= 1.0E-12D) {
            return Vec3.ZERO;
        }
        return value.normalize();
    }

    private static float nonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
    }

    private static float unit(float value) {
        return Math.max(0.0F, Math.min(1.0F, Float.isFinite(value) ? value : 0.0F));
    }
}
