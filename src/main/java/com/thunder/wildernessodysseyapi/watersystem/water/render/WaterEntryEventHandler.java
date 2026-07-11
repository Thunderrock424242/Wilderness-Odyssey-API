package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.EntityWaterCompat;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Emits client-only splash, ripple, and displacement effects on water entry.
 *
 * <p>The handler consumes the centralized entity compatibility state after each
 * entity tick. It does not repeat water-body detection or keep a competing
 * transition cache.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class WaterEntryEventHandler {

    private WaterEntryEventHandler() {
    }

    /** Reacts once when cached custom-water state enters its first wet tick. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide()) {
            return;
        }

        var waterState = EntityWaterCompat.stateFor(entity);
        if (!waterState.touchingWater()
                || waterState.ticksInWater() != 1
                || !WildernessWaterRules.isEnabled(entity.level())) {
            return;
        }

        double x = entity.getX();
        double y = entity.getY() + 0.1;
        double z = entity.getZ();
        spawnSplashParticles(entity, x, y, z);
        RippleRenderer.spawnRipple(x, y, z);
        WaterSurfaceDisplacement.spawnImpact(entity, x, z);
    }

    // Visual particles stay in the renderer package and never affect authority.
    private static void spawnSplashParticles(Entity entity, double x, double y, double z) {
        if (!WaterRenderingConfig.ENABLE_RIPPLES.get()
                || !WildernessWaterRules.isEnabled(entity.level())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        int splashParticles = WaterRenderingConfig.splashParticles();
        for (int i = 0; i < splashParticles; i++) {
            double velocityX = (Math.random() - 0.5) * 0.4;
            double velocityY = Math.random() * 0.3 + 0.15;
            double velocityZ = (Math.random() - 0.5) * 0.4;
            minecraft.level.addParticle(
                    ParticleTypes.SPLASH,
                    x + (Math.random() - 0.5) * 0.5,
                    y,
                    z + (Math.random() - 0.5) * 0.5,
                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }
}
