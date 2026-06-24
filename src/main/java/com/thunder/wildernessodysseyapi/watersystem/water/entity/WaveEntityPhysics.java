package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.ocean.shore.ShorelineWaterManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveAnimator;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Couples boats and floating entities to the shared wave surface.
 *
 * <p>The client computes only boat pitch, roll, and render bobbing. Horizontal
 * motion is applied on the logical server from the deterministic world-time
 * wave field, preventing client movement from fighting server corrections.</p>
 */
@EventBusSubscriber(modid = "wildernessodysseyapi")
public final class WaveEntityPhysics {

    private static final float TICKS_PER_SECOND = 20.0f;

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
        var mobileWater = SPHSimulationManager.get().sampleAt(
                level,
                entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.35,
                entity.getZ()
        );

        if (entity instanceof Boat boat && !isBoatTouchingWater(boat) && !mobileWater.wet()) {
            if (level.isClientSide()) {
                BoatTiltStore.remove(boat.getId());
            }
            return;
        }
        if (!entity.isInWater()
                && !CanonicalWater.isWater(level, entity.blockPosition())
                && !mobileWater.wet()
                && !(entity instanceof Boat)) {
            return;
        }

        WaterBodyClassifier.WaterType type =
                WaterBodyClassifier.classify(level, entity.blockPosition());

        // Rendering state is client-owned; gameplay movement is server-owned.
        if (level.isClientSide()) {
            if (entity instanceof Boat boat) {
                updateBoatVisuals(boat, type);
            }
            return;
        }

        if (entity instanceof Boat boat) {
            applyBoatForces(boat, type, level);
        } else if (entity instanceof ItemEntity item) {
            applyItemForces(item, type, level);
        } else if (entity instanceof LivingEntity livingEntity) {
            applyWadingForces(livingEntity, type, level);
        }

        if (mobileWater.wet()) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    mobileWater.velocityX() * 0.0025f,
                    mobileWater.velocityY() * 0.0010f,
                    mobileWater.velocityZ() * 0.0025f
            ));
        }
    }

    // Client rendering follows the analytic surface normal in boat-local axes.

    private static void updateBoatVisuals(Boat boat, WaterBodyClassifier.WaterType type) {
        float worldX = (float) boat.getX();
        float worldZ = (float) boat.getZ();
        GerstnerWaveProfile profile = profileFor(type);
        WaveSurfaceSample surface = GerstnerWaveAnimator.getSurfaceSampleAt(worldX, worldZ, type);

        float inverseNormalY = 1.0f / Math.max(0.01f, surface.normalY());
        float slopeX = -surface.normalX() * inverseNormalY;
        float slopeZ = -surface.normalZ() * inverseNormalY;
        float yawRadians = (float) Math.toRadians(boat.getYRot());
        float forwardX = -(float) Math.sin(yawRadians);
        float forwardZ = (float) Math.cos(yawRadians);
        float rightX = (float) Math.cos(yawRadians);
        float rightZ = (float) Math.sin(yawRadians);
        float forwardSlope = slopeX * forwardX + slopeZ * forwardZ;
        float rightSlope = slopeX * rightX + slopeZ * rightZ;

        float pitch = clamp((float) Math.toDegrees(Math.atan(forwardSlope)), -25.0f, 25.0f);
        float roll = clamp((float) Math.toDegrees(Math.atan(rightSlope)), -20.0f, 20.0f);
        float bob = surface.height() * profile.boatBobStrength;
        BoatTiltStore.set(boat.getId(), pitch, roll, bob);
    }

    // Server movement uses the same spectrum evaluated from authoritative time.

    private static void applyBoatForces(Boat boat, WaterBodyClassifier.WaterType type, Level level) {
        float[] push = getServerPush(level, (float) boat.getX(), (float) boat.getZ(), type);

        if (type == WaterBodyClassifier.WaterType.OCEAN) {
            addTidalCurrent(level, push, 0.002f);
            addShorelineFlow(level, push, boat.getX(), boat.getZ(), 0.0025f);
        }

        boat.setDeltaMovement(
                boat.getDeltaMovement().x + push[0],
                boat.getDeltaMovement().y,
                boat.getDeltaMovement().z + push[1]
        );
    }

    private static void applyItemForces(ItemEntity item, WaterBodyClassifier.WaterType type, Level level) {
        float[] push = getServerPush(level, (float) item.getX(), (float) item.getZ(), type);
        if (type == WaterBodyClassifier.WaterType.OCEAN) {
            addShorelineFlow(level, push, item.getX(), item.getZ(), 0.0015f);
        }
        item.setDeltaMovement(
                item.getDeltaMovement().x + push[0] * 0.4f,
                item.getDeltaMovement().y,
                item.getDeltaMovement().z + push[1] * 0.4f
        );
    }

    private static void applyWadingForces(
            LivingEntity entity,
            WaterBodyClassifier.WaterType type,
            Level level
    ) {
        if (type == WaterBodyClassifier.WaterType.POND || entity.isSwimming()) {
            return;
        }

        float[] push = getServerPush(level, (float) entity.getX(), (float) entity.getZ(), type);
        if (type == WaterBodyClassifier.WaterType.OCEAN) {
            float tidalBoost = 1.0f + Math.abs(TideSystem.getTideRate(level)) * 0.5f;
            push[0] *= tidalBoost;
            push[1] *= tidalBoost;
            addShorelineFlow(level, push, entity.getX(), entity.getZ(), 0.0012f);
        }

        entity.setDeltaMovement(
                entity.getDeltaMovement().x + push[0],
                entity.getDeltaMovement().y,
                entity.getDeltaMovement().z + push[1]
        );
    }

    private static float[] getServerPush(
            Level level,
            float worldX,
            float worldZ,
            WaterBodyClassifier.WaterType type
    ) {
        GerstnerWaveProfile profile = profileFor(type);
        float timeSeconds = level.getGameTime() / TICKS_PER_SECOND;
        WaveSpectrumState spectrum = type == WaterBodyClassifier.WaterType.OCEAN
                ? OceanSeaState.sample(level, 0.0f).spectrum()
                : WaveSpectrumState.NEUTRAL;
        WaveSurfaceSample sample = profile.sampleAt(
                worldX,
                worldZ,
                timeSeconds,
                profile.waveCount,
                spectrum
        );
        float[] push = new float[]{
                sample.velocityX() * profile.entityPushStrength,
                sample.velocityZ() * profile.entityPushStrength
        };
        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;
        var volumeCell = CanonicalWater.get(level, new BlockPos(blockX, surfaceY, blockZ));
        push[0] += volumeCell.velocityX() * 0.01f;
        push[1] += volumeCell.velocityZ() * 0.01f;
        return push;
    }

    private static void addTidalCurrent(Level level, float[] push, float strength) {
        float tideRate = TideSystem.getTideRate(level);
        float[] tidalDirection = TideSystem.getTidalCurrentDirection(level);
        push[0] += tidalDirection[0] * tideRate * strength;
        push[1] += tidalDirection[1] * tideRate * strength;
    }

    private static void addShorelineFlow(
            Level level,
            float[] push,
            double worldX,
            double worldZ,
            float strength
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ShorelineWaterManager.FlowSample flow =
                ShorelineWaterManager.get().sample(serverLevel, worldX, worldZ);
        if (!flow.wet()) {
            return;
        }
        push[0] += flow.velocityX() * strength;
        push[1] += flow.velocityZ() * strength;
    }

    private static boolean isBoatTouchingWater(Boat boat) {
        // A floating boat's block position can sit just above the surface, so
        // check both its feet and the block immediately beneath the hull.
        return CanonicalWater.isWater(boat.level(), boat.blockPosition())
                || CanonicalWater.isWater(boat.level(), boat.blockPosition().below());
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
