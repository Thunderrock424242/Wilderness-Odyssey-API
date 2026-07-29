package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShorelineWaterManager;
import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.EntityWaterCompat;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterSurfaceDisplacement;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveAnimator;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Couples boats and floating entities to the shared wave surface.
 *
 * <p>The client computes only boat pitch, roll, and render bobbing. The logical
 * server consumes the shared authority sample to integrate buoyancy and
 * fluid-relative drag, preventing client movement from fighting corrections.
 * Dry entities retain their vanilla movement unchanged.</p>
 */
@EventBusSubscriber(modid = "wildernessodysseyapi")
public final class WaveEntityPhysics {

    private static final float TICKS_PER_SECOND = 20.0f;
    private static Level tuningLevel;
    private static long tuningGameTime = Long.MIN_VALUE;
    private static HydrodynamicForces.RuntimeTuning cachedRuntimeTuning =
            HydrodynamicForces.RuntimeTuning.DEFAULT;

    private WaveEntityPhysics() {
    }

    /**
     * Updates visual boat response on the client and authoritative water forces
     * on the server after vanilla finishes each entity tick.
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();

        if (!WildernessWaterRules.isEnabled(level)) {
            if (level.isClientSide() && entity instanceof Boat boat) {
                BoatTiltStore.remove(boat.getId());
            }
            return;
        }

        if (!(entity instanceof Boat)
                && !(entity instanceof ItemEntity)
                && !(entity instanceof LivingEntity)) {
            return;
        }
        if (level.isClientSide() && entity instanceof ItemEntity) {
            return;
        }
        if (!level.isClientSide()
                && entity instanceof Boat
                && !WaterSimulationConfig.vanillaBoatCompatEnabled()) {
            return;
        }
        HydrodynamicForces.RuntimeTuning runtimeTuning = level.isClientSide()
                ? HydrodynamicForces.RuntimeTuning.DEFAULT
                : runtimeTuning(level);
        if (!level.isClientSide() && !runtimeTuning.enabled()) {
            return;
        }

        boolean cachedTouchingWater = (entity instanceof Boat boat && boat.isInWater())
                || EntityWaterCompat.stateFor(entity).touchingWater();
        var mobileWater = SPHSimulationManager.get().sampleAt(
                level,
                entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.35,
                entity.getZ()
        );

        // Rendering retains the cached compatibility state; authoritative
        // movement samples the shared water service directly on the server.
        if (level.isClientSide()) {
            if (entity instanceof Boat boat
                    && (!WaterSimulationConfig.vanillaBoatCompatEnabled()
                    || (!cachedTouchingWater && !mobileWater.wet()))) {
                BoatTiltStore.remove(boat.getId());
                return;
            }
            if (!cachedTouchingWater && !mobileWater.wet()) {
                return;
            }
            WaterBodyClassifier.WaterType type =
                    WaterBodyClassifier.classify(level, entity.blockPosition());
            if (entity instanceof Boat boat) {
                updateBoatVisuals(boat, type);
                WaterSurfaceDisplacement.spawnEntityWake(boat);
            } else if (entity instanceof LivingEntity) {
                WaterSurfaceDisplacement.spawnEntityWake(entity);
            }
            return;
        }

        if (!cachedTouchingWater && !mobileWater.wet()) {
            return;
        }

        BuoyancySample buoyancy = cachedTouchingWater
                ? WaterServices.buoyancy().sample(
                        level,
                        entity.getBoundingBox(),
                        entity.getDeltaMovement()
                )
                : BuoyancySample.DRY;
        if (!buoyancy.touchingWater() && !mobileWater.wet()) {
            return;
        }
        if (buoyancy.touchingWater()) {
            buoyancy = withLocalCurrents(level, entity, buoyancy, mobileWater);
        }

        if (entity instanceof Boat boat) {
            applyBoatForces(boat, buoyancy, mobileWater, runtimeTuning);
        } else if (entity instanceof ItemEntity item) {
            applyItemForces(item, buoyancy, mobileWater, runtimeTuning);
        } else if (entity instanceof LivingEntity livingEntity) {
            applyLivingForces(livingEntity, buoyancy, mobileWater, runtimeTuning);
        }
    }

    // Client rendering follows the analytic surface normal in boat-local axes.

    private static void updateBoatVisuals(Boat boat, WaterBodyClassifier.WaterType type) {
        float worldX = (float) boat.getX();
        float worldZ = (float) boat.getZ();
        GerstnerWaveProfile profile = profileFor(type);
        Level level = boat.level();
        float yawRadians = (float) Math.toRadians(boat.getYRot());
        float forwardX = -(float) Math.sin(yawRadians);
        float forwardZ = (float) Math.cos(yawRadians);
        float rightX = (float) Math.cos(yawRadians);
        float rightZ = (float) Math.sin(yawRadians);
        float hullLength = Math.max(1.4f, boat.getBbWidth() * 1.45f);
        float hullWidth = Math.max(0.9f, boat.getBbWidth() * 0.90f);

        // Match the renderer's height field by sampling the hull footprint
        // instead of tilting from a single center normal. This keeps boats
        // aligned with the visible crest/trough under their bow and sides.
        float centerHeight = renderedSurfaceHeight(level, type, worldX, worldZ);
        float frontHeight = renderedSurfaceHeight(
                level,
                type,
                worldX + forwardX * hullLength * 0.5f,
                worldZ + forwardZ * hullLength * 0.5f
        );
        float backHeight = renderedSurfaceHeight(
                level,
                type,
                worldX - forwardX * hullLength * 0.5f,
                worldZ - forwardZ * hullLength * 0.5f
        );
        float rightHeight = renderedSurfaceHeight(
                level,
                type,
                worldX + rightX * hullWidth * 0.5f,
                worldZ + rightZ * hullWidth * 0.5f
        );
        float leftHeight = renderedSurfaceHeight(
                level,
                type,
                worldX - rightX * hullWidth * 0.5f,
                worldZ - rightZ * hullWidth * 0.5f
        );

        float forwardSlope = (frontHeight - backHeight) / hullLength;
        float rightSlope = (rightHeight - leftHeight) / hullWidth;
        float pitch = clamp((float) Math.toDegrees(Math.atan(forwardSlope)), -25.0f, 25.0f);
        float roll = clamp((float) Math.toDegrees(Math.atan(rightSlope)), -20.0f, 20.0f);
        float bob = clamp(centerHeight * profile.boatBobStrength, -0.55f, 0.55f);
        BoatTiltStore.set(boat.getId(), pitch, roll, bob);
    }

    private static float renderedSurfaceHeight(
            Level level,
            WaterBodyClassifier.WaterType type,
            float worldX,
            float worldZ
    ) {
        WaveSurfaceSample surface = GerstnerWaveAnimator.getSurfaceSampleAt(worldX, worldZ, type);
        float sampleTick = GerstnerWaveAnimator.getTime() * TICKS_PER_SECOND;
        return surface.height() + WaterSurfaceDisplacement.sampleHeight(
                level,
                worldX,
                worldZ,
                sampleTick
        );
    }

    // Server movement consumes the same authority sample used by adapters.

    private static void applyBoatForces(
            Boat boat,
            BuoyancySample buoyancy,
            SPHSimulationManager.MobileWaterSample mobileWater,
            HydrodynamicForces.RuntimeTuning runtimeTuning
    ) {
        applyForces(
                boat,
                buoyancy,
                mobileWater,
                HydrodynamicForces.BOAT_PROFILE,
                0,
                runtimeTuning
        );
    }

    private static void applyItemForces(
            ItemEntity item,
            BuoyancySample buoyancy,
            SPHSimulationManager.MobileWaterSample mobileWater,
            HydrodynamicForces.RuntimeTuning runtimeTuning
    ) {
        applyForces(
                item,
                buoyancy,
                mobileWater,
                HydrodynamicForces.ITEM_PROFILE,
                item.getItem().getCount(),
                runtimeTuning
        );
    }

    private static void applyLivingForces(
            LivingEntity entity,
            BuoyancySample buoyancy,
            SPHSimulationManager.MobileWaterSample mobileWater,
            HydrodynamicForces.RuntimeTuning runtimeTuning
    ) {
        applyForces(
                entity,
                buoyancy,
                mobileWater,
                HydrodynamicForces.LIVING_PROFILE,
                0,
                runtimeTuning
        );
    }

    private static void applyForces(
            Entity entity,
            BuoyancySample buoyancy,
            SPHSimulationManager.MobileWaterSample mobileWater,
            HydrodynamicForces.ForceProfile profile,
            int payloadUnits,
            HydrodynamicForces.RuntimeTuning runtimeTuning
    ) {
        Vec3 delta = HydrodynamicForces.velocityDelta(
                buoyancy,
                entity.getBoundingBox(),
                entity.getDeltaMovement(),
                profile,
                payloadUnits,
                HydrodynamicForces.FIXED_DELTA_SECONDS,
                runtimeTuning
        );

        // Mobile SPH water has no stable free surface for buoyancy, but its
        // velocity still contributes drag when spray or a pour is the only hit.
        if (!buoyancy.touchingWater() && mobileWater.wet()) {
            delta = delta.add(HydrodynamicForces.dragOnlyVelocityDelta(
                    mobileCurrent(mobileWater),
                    entity.getBoundingBox(),
                    entity.getDeltaMovement(),
                    HydrodynamicForces.MOBILE_DRAG_FRACTION,
                    profile,
                    payloadUnits,
                    HydrodynamicForces.FIXED_DELTA_SECONDS,
                    runtimeTuning
            ));
        }

        if (delta.lengthSqr() > 0.0) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(delta));
        }
    }

    private static BuoyancySample withLocalCurrents(
            Level level,
            Entity entity,
            BuoyancySample buoyancy,
            SPHSimulationManager.MobileWaterSample mobileWater
    ) {
        Vec3 additionalCurrent = mobileWater.wet() ? mobileCurrent(mobileWater) : Vec3.ZERO;
        if (level instanceof ServerLevel serverLevel) {
            ShorelineWaterManager.FlowSample shoreline =
                    ShorelineWaterManager.get().sample(serverLevel, entity.getX(), entity.getZ());
            if (shoreline.wet()) {
                additionalCurrent = additionalCurrent.add(
                        shoreline.velocityX() * HydrodynamicForces.SHORELINE_CURRENT_SCALE,
                        0.0,
                        shoreline.velocityZ() * HydrodynamicForces.SHORELINE_CURRENT_SCALE
                );
            }
        }
        if (additionalCurrent.lengthSqr() <= 0.0) {
            return buoyancy;
        }
        return new BuoyancySample(
                buoyancy.touchingWater(),
                buoyancy.submerged(),
                buoyancy.surfaceHeight(),
                buoyancy.submergedFraction(),
                buoyancy.current().add(additionalCurrent),
                buoyancy.surfaceNormal()
        );
    }

    private static Vec3 mobileCurrent(
            SPHSimulationManager.MobileWaterSample mobileWater
    ) {
        return new Vec3(
                mobileWater.velocityX() * HydrodynamicForces.MOBILE_CURRENT_SCALE,
                mobileWater.velocityY() * HydrodynamicForces.MOBILE_CURRENT_SCALE,
                mobileWater.velocityZ() * HydrodynamicForces.MOBILE_CURRENT_SCALE
        );
    }

    static HydrodynamicForces.RuntimeTuning runtimeTuning(Level level) {
        long gameTime = level.getGameTime();
        if (tuningLevel == level && tuningGameTime == gameTime) {
            return cachedRuntimeTuning;
        }

        // Entity ticks are grouped by level, so sampling once per level tick
        // avoids allocating a tuning record for every wet or dry mob.
        cachedRuntimeTuning = new HydrodynamicForces.RuntimeTuning(
                WaterSimulationConfig.entityHydrodynamicsEnabled(),
                WaterSimulationConfig.entityBuoyancyScale(),
                WaterSimulationConfig.entityDragScale(),
                WaterSimulationConfig.entityMaxAddedVelocityScale()
        );
        tuningLevel = level;
        tuningGameTime = gameTime;
        return cachedRuntimeTuning;
    }

    private static GerstnerWaveProfile profileFor(WaterBodyClassifier.WaterType type) {
        return switch (type) {
            case OCEAN -> GerstnerWaveProfile.OCEAN;
            case RIVER -> GerstnerWaveProfile.RIVER;
            case POND -> GerstnerWaveProfile.POND;
        };
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
