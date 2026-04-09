package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wilderness.water.particle.WildernessParticleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;

import java.util.WeakHashMap;

/**
 * WaterEntryEventHandler
 *
 * Detects the moment an entity transitions from non-water to water
 * and triggers:
 *   1. A splash of vanilla WaterSuspend particles
 *   2. An expanding ripple ring via RippleRenderer
 *
 * Works client-side only — ripples are purely cosmetic.
 * Uses a WeakHashMap to track previous-tick water state per entity
 * so we catch the exact frame of entry without ticking every entity.
 */
@EventBusSubscriber(modid = "wilderness", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class WaterEntryEventHandler {

    private static final WeakHashMap<Entity, Boolean> wasInWater = new WeakHashMap<>();

    @SubscribeEvent
    public static void onEntityTick(EntityEvent.Size event) {
        // EntityEvent.Size fires every tick for all entities — we piggyback on it
        // as a lightweight per-entity tick hook on the client.
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            checkWaterEntry(entity);
        }
    }

    private static void checkWaterEntry(Entity entity) {
        boolean inWaterNow = entity.isInWater();
        boolean wasInWaterBefore = wasInWater.getOrDefault(entity, false);

        if (inWaterNow && !wasInWaterBefore) {
            // Entity just entered water this tick — spawn effects at feet
            double x = entity.getX();
            double y = entity.getY() + 0.1;
            double z = entity.getZ();

            spawnSplashParticles(entity, x, y, z);
            RippleRenderer.spawnRipple(x, y, z);
        }

        wasInWater.put(entity, inWaterNow);
    }

    private static void spawnSplashParticles(Entity entity, double x, double y, double z) {
        var level = entity.level();
        if (!level.isClientSide()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Spawn a burst of water drip / splash particles
        for (int i = 0; i < 8; i++) {
            double vx = (Math.random() - 0.5) * 0.4;
            double vy = Math.random() * 0.3 + 0.15;
            double vz = (Math.random() - 0.5) * 0.4;

            mc.level.addParticle(
                net.minecraft.core.particles.ParticleTypes.SPLASH,
                x + (Math.random() - 0.5) * 0.5,
                y,
                z + (Math.random() - 0.5) * 0.5,
                vx, vy, vz
            );
        }

        // Also spawn the custom ripple particle
        mc.level.addParticle(
            WildernessParticleRegistry.RIPPLE.get(),
            x, y, z,
            0, 0, 0
        );
    }
}
