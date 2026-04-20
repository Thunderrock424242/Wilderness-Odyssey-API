package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveAnimator;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * WaveEntityPhysics
 * <p>
 * Applies wave-driven forces to entities in water:
 * <p>
 *   Boats     — rock (pitch/roll) and bob vertically with the wave profile.
 *               Horizontal push from wave direction.
 * <p>
 *   Items     — float and drift with the surface current.
 * <p>
 *   Living    — slight push when wading in ocean/river water.
 *               Stronger at high tide (tidal current effect).
 */
@EventBusSubscriber(modid = "wildernessodysseyapi")
public class WaveEntityPhysics {

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level   = entity.level();

        if (!level.isClientSide()) return; // visual only — server handles real movement
        if (!entity.isInWater() && !(entity instanceof Boat)) return;

        WaterBodyClassifier.WaterType type =
            WaterBodyClassifier.classify(level, entity.blockPosition());

        if (entity instanceof Boat boat) {
            tickBoat(boat, type, level);
        } else if (entity instanceof ItemEntity item) {
            tickItem(item, type);
        } else if (entity.isInWater()) {
            tickLivingInWater(entity, type, level);
        }
    }

    // -------------------------------------------------------------------------
    // Boats
    // -------------------------------------------------------------------------

    private static void tickBoat(Boat boat, WaterBodyClassifier.WaterType type, Level level) {
        float bx = (float) boat.getX();
        float bz = (float) boat.getZ();
        float time = GerstnerWaveAnimator.getTime();

        GerstnerWaveProfile profile = profileFor(type);

        // Sample wave height at boat position and at two offset points for tilt
        float hCenter = GerstnerWaveAnimator.getHeightAt(bx,        bz,        type);
        float hFront  = GerstnerWaveAnimator.getHeightAt(bx,        bz + 0.8f, type);
        float hRight  = GerstnerWaveAnimator.getHeightAt(bx + 0.8f, bz,        type);

        // Pitch (nose up/down) and roll (lean sideways)
        float pitch = (float) Math.toDegrees(Math.atan2(hFront - hCenter, 0.8f));
        float roll  = (float) Math.toDegrees(Math.atan2(hRight - hCenter, 0.8f));

        // Clamp tilt angles so they look natural
        pitch = Math.max(-25f, Math.min(25f, pitch));
        roll  = Math.max(-20f, Math.min(20f, roll));

        // Apply vertical bob
        float bobTarget = hCenter * profile.boatBobStrength;
        double newY = boat.getY() + bobTarget * 0.08f;
        boat.setPos(boat.getX(), newY, boat.getZ());

        // Apply horizontal push from wave
        float[] push = profile.getPushAt(bx, bz, time);

        // Tidal current adds directional push in oceans
        if (type == WaterBodyClassifier.WaterType.OCEAN) {
            float tideRate = TideSystem.getTideRate(level);
            float[] tidalDir = TideSystem.getTidalCurrentDirection(level);
            push[0] += tidalDir[0] * tideRate * 0.002f;
            push[1] += tidalDir[1] * tideRate * 0.002f;
        }

        boat.setDeltaMovement(
            boat.getDeltaMovement().x + push[0],
            boat.getDeltaMovement().y,
            boat.getDeltaMovement().z + push[1]
        );

        // Store pitch/roll for the render mixin to read
        BoatTiltStore.set(boat.getId(), pitch, roll);
    }

    // -------------------------------------------------------------------------
    // Item entities
    // -------------------------------------------------------------------------

    private static void tickItem(ItemEntity item, WaterBodyClassifier.WaterType type) {
        if (!item.isInWater()) return;

        float ix = (float) item.getX();
        float iz = (float) item.getZ();

        float[] push = GerstnerWaveAnimator.getPushAt(ix, iz, type);

        // Items drift gently with the current
        item.setDeltaMovement(
            item.getDeltaMovement().x + push[0] * 0.4f,
            item.getDeltaMovement().y,
            item.getDeltaMovement().z + push[1] * 0.4f
        );
    }

    // -------------------------------------------------------------------------
    // Living entities wading in water
    // -------------------------------------------------------------------------

    private static void tickLivingInWater(Entity entity,
                                           WaterBodyClassifier.WaterType type,
                                           Level level) {
        // Only push if wading (not swimming) and in ocean or river
        if (type == WaterBodyClassifier.WaterType.POND) return;

        float ex = (float) entity.getX();
        float ez = (float) entity.getZ();
        float[] push = GerstnerWaveAnimator.getPushAt(ex, ez, type);

        // Tidal current in ocean
        float tidalBoost = 1f;
        if (type == WaterBodyClassifier.WaterType.OCEAN) {
            float tideRate = TideSystem.getTideRate(level);
            tidalBoost = 1f + Math.abs(tideRate) * 0.5f;
        }

        entity.setDeltaMovement(
            entity.getDeltaMovement().x + push[0] * tidalBoost,
            entity.getDeltaMovement().y,
            entity.getDeltaMovement().z + push[1] * tidalBoost
        );
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static GerstnerWaveProfile profileFor(WaterBodyClassifier.WaterType type) {
        return switch (type) {
            case OCEAN -> GerstnerWaveProfile.OCEAN;
            case RIVER -> GerstnerWaveProfile.RIVER;
            case POND  -> GerstnerWaveProfile.POND;
        };
    }
}
