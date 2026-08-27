package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShorelineWaterManager;
import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterPhysicsProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterPhysicsProfileRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.EntityWaterCompat;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.lang.ref.WeakReference;

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

    // The integrated server can unload and recreate its Level in one JVM. A
    // weak reference preserves the once-per-tick cache without pinning the old
    // world after returning to the title screen.
    private static WeakReference<Level> tuningLevel = new WeakReference<>(null);
    private static long tuningGameTime = Long.MIN_VALUE;
    private static HydrodynamicForces.RuntimeTuning cachedRuntimeTuning =
            HydrodynamicForces.RuntimeTuning.DEFAULT;

    private WaveEntityPhysics() {
    }

    /**
     * Applies authoritative water forces after vanilla finishes each server
     * entity tick. Client pitch, roll, bobbing, and wakes are isolated in the
     * client-only presentation subscriber.
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();

        // Client presentation lives in a Dist.CLIENT subscriber so this common
        // authority class has no client constant-pool references on a server.
        if (level.isClientSide()) {
            return;
        }

        if (!WildernessWaterRules.isEnabled(level)) {
            return;
        }

        WaterPhysicsProfile physicsProfile = WaterPhysicsProfileRegistry.resolve(entity);
        if (physicsProfile == null) {
            return;
        }
        if (entity instanceof Boat
                && !WaterSimulationConfig.vanillaBoatCompatEnabled()) {
            return;
        }
        HydrodynamicForces.RuntimeTuning runtimeTuning = runtimeTuning(level);
        if (!runtimeTuning.enabled()) {
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
        BlockPos hydrologyPosition = BlockPos.containing(
                entity.getX(), entity.getY(), entity.getZ());
        WatershedConditions watershed = WaterServices.access().getWatershedConditions(
                level, hydrologyPosition);
        boolean raisedWatershedSurface = watershed.hasSurfaceWater()
                && (watershed.waterLevelOffset() > 0.02f || watershed.flooding());

        if (!cachedTouchingWater
                && !mobileWater.wet()
                && !physicsProfile.rigidWatercraft()
                && !raisedWatershedSurface) {
            return;
        }

        // Rigid craft always execute the multi-point sample. A center-only dry
        // prefilter can otherwise miss a bow or corner entering a crest.
        BuoyancySample buoyancy = (cachedTouchingWater
                || physicsProfile.rigidWatercraft()
                || raisedWatershedSurface)
                ? sampleBuoyancy(level, entity, physicsProfile)
                : BuoyancySample.DRY;
        if (!buoyancy.touchingWater() && !mobileWater.wet()) {
            if (physicsProfile.rigidWatercraft()) {
                WatercraftDynamicsState.leaveWater(entity);
            }
            return;
        }
        if (buoyancy.touchingWater()) {
            buoyancy = withLocalCurrents(
                    level, entity, hydrologyPosition, watershed, buoyancy, mobileWater);
        }

        int payloadUnits = entity instanceof ItemEntity item ? item.getItem().getCount() : 0;
        applyForces(
                entity,
                buoyancy,
                mobileWater,
                physicsProfile,
                payloadUnits,
                runtimeTuning
        );
    }

    private static BuoyancySample sampleBuoyancy(
            Level level,
            Entity entity,
            WaterPhysicsProfile physicsProfile
    ) {
        if (physicsProfile.rigidWatercraft()) {
            return WaterServices.buoyancy().sampleOriented(
                    level,
                    entity.getBoundingBox(),
                    entity.getDeltaMovement(),
                    entity.getYRot()
            );
        }
        return WaterServices.buoyancy().sample(
                level,
                entity.getBoundingBox(),
                entity.getDeltaMovement()
        );
    }

    // Server movement consumes the same authority sample used by adapters.

    private static void applyForces(
            Entity entity,
            BuoyancySample buoyancy,
            SPHSimulationManager.MobileWaterSample mobileWater,
            WaterPhysicsProfile profile,
            int payloadUnits,
            HydrodynamicForces.RuntimeTuning runtimeTuning
    ) {
        Vec3 delta;
        double yawDegreesPerTick = 0.0;
        if (profile.rigidWatercraft() && buoyancy.touchingWater()) {
            WatercraftDynamicsState.Response response = WatercraftDynamicsState.update(
                    entity,
                    buoyancy,
                    profile,
                    payloadUnits,
                    runtimeTuning
            );
            delta = response.velocityDelta();
            yawDegreesPerTick = response.yawDegreesPerTick();
        } else {
            delta = HydrodynamicForces.velocityDelta(
                    buoyancy,
                    entity.getBoundingBox(),
                    entity.getDeltaMovement(),
                    profile,
                    payloadUnits,
                    HydrodynamicForces.FIXED_DELTA_SECONDS,
                    runtimeTuning
            );
        }

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
        // Empty craft weathercock into lateral flow. Ridden craft retain direct
        // player steering while still receiving hull-oriented translation.
        if (profile.rigidWatercraft()
                && !entity.isVehicle()
                && Math.abs(yawDegreesPerTick) > 1.0e-4) {
            entity.setYRot((float) (entity.getYRot() + yawDegreesPerTick));
        }
    }

    private static BuoyancySample withLocalCurrents(
            Level level,
            Entity entity,
            BlockPos position,
            WatershedConditions watershed,
            BuoyancySample buoyancy,
            SPHSimulationManager.MobileWaterSample mobileWater
    ) {
        Vec3 additionalCurrent = mobileWater.wet() ? mobileCurrent(mobileWater) : Vec3.ZERO;
        WatershedLocalFlow localFlow = WaterServices.access().getLocalWatershedFlow(level, position);
        if (localFlow != WatershedLocalFlow.NONE) {
            additionalCurrent = additionalCurrent.add(
                    localFlow.currentX() - watershed.currentX(),
                    0.0,
                    localFlow.currentZ() - watershed.currentZ()
            );
        }
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
        if (tuningLevel.get() == level && tuningGameTime == gameTime) {
            return cachedRuntimeTuning;
        }

        // Entity ticks are grouped by level, so sampling once per level tick
        // avoids allocating a tuning record for every wet or dry mob.
        cachedRuntimeTuning = new HydrodynamicForces.RuntimeTuning(
                WaterSimulationConfig.entityHydrodynamicsEnabled(),
                WaterSimulationConfig.entityBuoyancyScale(),
                WaterSimulationConfig.entityDragScale(),
                WaterSimulationConfig.entityMaxAddedVelocityScale(),
                WaterSimulationConfig.entityPlaningScale(),
                WaterSimulationConfig.entitySlammingScale(),
                WaterSimulationConfig.entityAngularResponseScale()
        );
        tuningLevel = new WeakReference<>(level);
        tuningGameTime = gameTime;
        return cachedRuntimeTuning;
    }

}
