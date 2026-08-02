package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.EntityWaterCompat;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.water.render.ClientWaterImmersion;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterShaders;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterSurfaceDisplacement;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterSurfaceEquation;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveAnimator;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Drives client-only boat presentation and cosmetic surface wakes.
 *
 * <p>This subscriber is physically client-scoped so the common authoritative
 * buoyancy handler never references Minecraft client classes on a dedicated
 * server. It reads synchronized snapshots and never changes entity authority.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientWaveEntityEffects {

    private static final float TICKS_PER_SECOND = 20.0f;

    private ClientWaveEntityEffects() {
    }

    /** Updates boat pose and creates bounded cosmetic wakes after vanilla movement. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (!level.isClientSide()) {
            return;
        }
        if (!WildernessWaterRules.isEnabled(level)) {
            if (entity instanceof Boat boat) {
                BoatTiltStore.remove(boat.getId());
            }
            return;
        }
        if (!(entity instanceof Boat) && !(entity instanceof LivingEntity)) {
            return;
        }

        boolean cachedTouchingWater = (entity instanceof Boat boat && boat.isInWater())
                || EntityWaterCompat.stateFor(entity).touchingWater();
        SPHSimulationManager.MobileWaterSample mobileWater = SPHSimulationManager.get().sampleAt(
                level,
                entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.35,
                entity.getZ()
        );

        if (entity instanceof Boat boat
                && (!WaterSimulationConfig.vanillaBoatCompatEnabled()
                || (!cachedTouchingWater && !mobileWater.wet()))) {
            BoatTiltStore.remove(boat.getId());
            return;
        }
        if (!cachedTouchingWater && !mobileWater.wet()) {
            return;
        }

        // Only the built-in surface exposes the exact analytic height field.
        // External shader packs retain their own visual water ownership.
        if (entity instanceof Boat boat) {
            if (WaterShaders.shouldUseCoreShader()) {
                WaterBodyClassifier.WaterType type =
                        WaterBodyClassifier.classify(level, entity.blockPosition());
                updateBoatVisuals(boat, type);
            } else {
                BoatTiltStore.remove(boat.getId());
            }
            WaterSurfaceDisplacement.spawnEntityWake(boat);
        } else {
            WaterSurfaceDisplacement.spawnEntityWake(entity);
        }
    }

    // Hull-footprint sampling follows the visible crest instead of a center-only normal.
    private static void updateBoatVisuals(Boat boat, WaterBodyClassifier.WaterType type) {
        double worldX = boat.getX();
        double worldZ = boat.getZ();
        GerstnerWaveProfile profile = profileFor(type);
        Level level = boat.level();
        float yawRadians = (float) Math.toRadians(boat.getYRot());
        float forwardX = -(float) Math.sin(yawRadians);
        float forwardZ = (float) Math.cos(yawRadians);
        float rightX = (float) Math.cos(yawRadians);
        float rightZ = (float) Math.sin(yawRadians);
        float hullLength = Math.max(1.4f, boat.getBbWidth() * 1.45f);
        float hullWidth = Math.max(0.9f, boat.getBbWidth() * 0.90f);
        Vec3 selectionOrigin = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        float centerHeight = renderedSurfaceHeight(
                level, type, worldX, worldZ, selectionOrigin.x, selectionOrigin.z);
        float frontHeight = renderedSurfaceHeight(
                level, type,
                worldX + forwardX * hullLength * 0.5f,
                worldZ + forwardZ * hullLength * 0.5f,
                selectionOrigin.x, selectionOrigin.z);
        float backHeight = renderedSurfaceHeight(
                level, type,
                worldX - forwardX * hullLength * 0.5f,
                worldZ - forwardZ * hullLength * 0.5f,
                selectionOrigin.x, selectionOrigin.z);
        float rightHeight = renderedSurfaceHeight(
                level, type,
                worldX + rightX * hullWidth * 0.5f,
                worldZ + rightZ * hullWidth * 0.5f,
                selectionOrigin.x, selectionOrigin.z);
        float leftHeight = renderedSurfaceHeight(
                level, type,
                worldX - rightX * hullWidth * 0.5f,
                worldZ - rightZ * hullWidth * 0.5f,
                selectionOrigin.x, selectionOrigin.z);

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
            double worldX,
            double worldZ,
            double selectionX,
            double selectionZ
    ) {
        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);
        ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(
                level, blockX, blockZ);
        ClientWaterChunkSnapshot.Column column = snapshot == null
                ? ClientWaterChunkSnapshot.Column.DRY
                : snapshot.column(blockX & 15, blockZ & 15);
        float currentX = column.wet() ? column.velocityX() : 0.0f;
        float currentZ = column.wet() ? column.velocityZ() : 0.0f;
        float continuity = column.wet() && level instanceof ClientLevel clientLevel
                ? WaterSurfaceEquation.surfaceContinuityFactor(
                        ClientWaterImmersion.sampleSurfaceContinuity(
                                clientLevel, worldX, worldZ))
                : 0.0f;
        WaveSurfaceSample surface = GerstnerWaveAnimator.getSurfaceSampleAt(
                worldX, worldZ, type, currentX, currentZ).attenuated(continuity);
        double sampleTick = GerstnerWaveAnimator.getTimeSeconds() * TICKS_PER_SECOND;
        float transientHeight = WaterSurfaceDisplacement.sampleHeight(
                level, worldX, worldZ, sampleTick, selectionX, selectionZ);
        return surface.height() + transientHeight * continuity;
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
