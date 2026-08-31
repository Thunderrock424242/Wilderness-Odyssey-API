package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.EntityWaterCompat;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Emits client-only splash, ripple, and displacement effects on water entry.
 *
 * <p>The handler consumes the centralized entity compatibility state after each
 * entity tick. It does not repeat water-body detection or keep a competing
 * transition cache.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class WaterEntryEventHandler {

    private static final double MAX_EFFECT_DISTANCE_SQUARED = 96.0 * 96.0;
    private static final long IMPACT_CAPTURE_RETENTION_TICKS = 4L;
    private static final Map<Entity, EntryKinematics> ENTRY_KINEMATICS = new WeakHashMap<>();

    private WaterEntryEventHandler() {
    }

    /** Captures dry-side motion before vanilla water drag can reduce it. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || entity.distanceToSqr(minecraft.player) > MAX_EFFECT_DISTANCE_SQUARED
                || !WildernessWaterRules.isEnabled(entity.level())) {
            ENTRY_KINEMATICS.remove(entity);
            return;
        }

        Vec3 velocity = entity.getDeltaMovement();
        EntryKinematics current = new EntryKinematics(
                velocity.x,
                velocity.z,
                Math.max(0.0, -velocity.y),
                velocity.horizontalDistance(),
                Math.max(0.0f, entity.fallDistance),
                entity.level().getGameTime()
        );
        EntryKinematics previous = ENTRY_KINEMATICS.get(entity);
        if (previous == null
                || current.gameTime - previous.gameTime > IMPACT_CAPTURE_RETENTION_TICKS
                || current.impactScore() >= previous.impactScore()) {
            ENTRY_KINEMATICS.put(entity, current);
        }
    }

    /** Reacts once when cached custom-water state enters its first wet tick. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide()) {
            return;
        }

        var waterState = EntityWaterCompat.stateFor(entity);
        if (!WildernessWaterRules.isEnabled(entity.level())) {
            ENTRY_KINEMATICS.remove(entity);
            return;
        }
        if (!waterState.touchingWater()) {
            return;
        }
        EntryKinematics kinematics = ENTRY_KINEMATICS.remove(entity);
        if (waterState.ticksInWater() != 1) {
            return;
        }
        if (kinematics == null) {
            Vec3 velocity = entity.getDeltaMovement();
            kinematics = new EntryKinematics(
                    velocity.x,
                    velocity.z,
                    Math.max(0.0, -velocity.y),
                    velocity.horizontalDistance(),
                    Math.max(0.0f, entity.fallDistance),
                    entity.level().getGameTime()
            );
        }

        WaterEntryImpactModel.Impact impact = WaterEntryImpactModel.evaluate(
                entity.getBbWidth(),
                entity.getBbHeight(),
                kinematics.downwardSpeed,
                kinematics.horizontalSpeed,
                kinematics.fallDistance,
                entity instanceof Boat,
                WaterRenderingConfig.splashParticles()
        );

        double x = entity.getX();
        double y = Double.isFinite(waterState.surfaceHeight())
                ? waterState.surfaceHeight() + 0.035
                : entity.getY() + 0.1;
        double z = entity.getZ();
        spawnSplashParticles(entity, kinematics, impact, x, y, z);
        RippleRenderer.spawnRipple(x, y, z, impact.rippleStrength());
        WaterSurfaceDisplacement.spawnImpact(entity, x, z, impact.strength());
    }

    // Visual particles stay in the renderer package and never affect authority.
    private static void spawnSplashParticles(
            Entity entity,
            EntryKinematics kinematics,
            WaterEntryImpactModel.Impact impact,
            double x,
            double y,
            double z
    ) {
        if (!WaterRenderingConfig.ENABLE_RIPPLES.get()
                || !WildernessWaterRules.isEnabled(entity.level())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RandomSource random = minecraft.level.getRandom();
        for (int i = 0; i < impact.particleCount(); i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radialDistance = Math.sqrt(random.nextDouble()) * impact.spawnRadius();
            double directionX = Math.cos(angle);
            double directionZ = Math.sin(angle);
            double outwardSpeed = impact.outwardSpeed() * (0.55 + random.nextDouble() * 0.45);
            double velocityX = directionX * outwardSpeed + kinematics.motionX * 0.35;
            double velocityY = impact.upwardSpeed() * (0.72 + random.nextDouble() * 0.50);
            double velocityZ = directionZ * outwardSpeed + kinematics.motionZ * 0.35;
            minecraft.level.addParticle(
                    ParticleTypes.SPLASH,
                    x + directionX * radialDistance,
                    y,
                    z + directionZ * radialDistance,
                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }

    /** Clears retained dry-side motion when the client dimension unloads. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ENTRY_KINEMATICS.clear();
        }
    }

    private record EntryKinematics(
            double motionX,
            double motionZ,
            double downwardSpeed,
            double horizontalSpeed,
            float fallDistance,
            long gameTime
    ) {
        private double impactScore() {
            return downwardSpeed + horizontalSpeed * 0.25 + fallDistance * 0.0125;
        }
    }
}
