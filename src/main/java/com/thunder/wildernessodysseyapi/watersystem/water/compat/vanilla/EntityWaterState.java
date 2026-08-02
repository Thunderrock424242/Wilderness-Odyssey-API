package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cached custom-water state for one entity.
 *
 * <p>The adapter updates this mutable object at most once per game tick. Hot
 * callers should use primitive current accessors; {@link #current()} is a
 * convenience method that allocates a vector.</p>
 */
public final class EntityWaterState {

    private Level sampledLevel;
    private long sampledGameTime = Long.MIN_VALUE;
    private boolean authoritativeContactKnown;
    private boolean touchingWater;
    private boolean bodySubmerged;
    private boolean eyesSubmerged;
    private double surfaceHeight = Double.NaN;
    private double depth;
    private double currentX;
    private double currentY;
    private double currentZ;
    private int ticksInWater;
    private int ticksSinceWater;
    private final WaterSample queryScratch = new WaterSample();
    private final EntityWaterContactSampler.ContactAccumulator contactAccumulator =
            new EntityWaterContactSampler.ContactAccumulator();

    void update(
            Entity entity,
            boolean authoritativeContactKnown,
            boolean touchingWater,
            boolean bodySubmerged,
            boolean eyeSubmerged,
            double surfaceHeight,
            double depth,
            double currentX,
            double currentY,
            double currentZ
    ) {
        boolean wasTouching = this.touchingWater;
        this.authoritativeContactKnown = authoritativeContactKnown;
        this.touchingWater = touchingWater;
        this.bodySubmerged = bodySubmerged;
        this.eyesSubmerged = eyeSubmerged;
        this.surfaceHeight = surfaceHeight;
        this.depth = Math.max(0.0, depth);
        this.currentX = currentX;
        this.currentY = currentY;
        this.currentZ = currentZ;
        sampledLevel = entity.level();
        sampledGameTime = sampledLevel.getGameTime();

        if (this.touchingWater) {
            ticksInWater = wasTouching ? ticksInWater + 1 : 1;
            ticksSinceWater = 0;
        } else {
            ticksSinceWater = wasTouching ? 1 : Math.min(Integer.MAX_VALUE, ticksSinceWater + 1);
            ticksInWater = 0;
        }
    }

    void clear(Entity entity) {
        update(entity, false, false, false, false, Double.NaN, 0.0, 0.0, 0.0, 0.0);
    }

    WaterSample queryScratch() {
        return queryScratch;
    }

    EntityWaterContactSampler.ContactAccumulator contactAccumulator() {
        return contactAccumulator;
    }

    boolean sampledAt(Level level, long gameTime) {
        return sampledLevel == level && sampledGameTime == gameTime;
    }

    /**
     * Returns whether authority supplied a nearby surface for this decision.
     *
     * <p>Vanilla hooks only replace their result while this is true. Ordinary
     * vanilla and third-party tagged water therefore keep their original path.</p>
     */
    public boolean authoritativeContactKnown() {
        return authoritativeContactKnown;
    }

    /** Returns whether any entity bounds currently intersect custom water. */
    public boolean touchingWater() {
        return touchingWater;
    }

    /** Returns whether the complete entity bounds are below the surface. */
    public boolean bodySubmerged() {
        return bodySubmerged;
    }

    /** Returns whether the entity eye position is below the custom surface. */
    public boolean eyesSubmerged() {
        return eyesSubmerged;
    }

    /** Returns the submerged-footprint-weighted animated surface Y. */
    public double surfaceHeight() {
        return surfaceHeight;
    }

    /** Returns depth above the bottom of the entity bounds. */
    public double depth() {
        return depth;
    }

    /** Returns current X without allocating a vector. */
    public double currentX() {
        return currentX;
    }

    /** Returns current Y without allocating a vector. */
    public double currentY() {
        return currentY;
    }

    /** Returns current Z without allocating a vector. */
    public double currentZ() {
        return currentZ;
    }

    /** Returns the current as a convenience vector. */
    public Vec3 current() {
        return new Vec3(currentX, currentY, currentZ);
    }

    /** Returns consecutive ticks spent touching custom water. */
    public int ticksInWater() {
        return ticksInWater;
    }

    /** Returns consecutive ticks spent outside custom water. */
    public int ticksSinceWater() {
        return ticksSinceWater;
    }
}
