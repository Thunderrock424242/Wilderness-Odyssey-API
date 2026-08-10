package com.thunder.wildernessodysseyapi.weather.severe;

import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Presents rare severe systems with bounded particles, wind, and opt-in foliage damage. */
public final class SevereWeatherScheduler {

    /** Runs infrequently and touches only loaded severe systems near entities. */
    public void tick(
            ServerLevel level,
            long gameTime,
            List<TrackedWeatherSystem> systems,
            WeatherConfig.FeatureSettings settings
    ) {
        if (!settings.severeWeatherEnabled() || Math.floorMod(gameTime, 10L) != 0L) {
            return;
        }
        int processed = 0;
        for (TrackedWeatherSystem system : systems) {
            if (!system.type().severe() || system.intensity() < 0.62 || processed++ >= 8) {
                continue;
            }
            BlockPos centerColumn = BlockPos.containing(system.centerX(), 64.0, system.centerZ());
            if (!level.hasChunkAt(centerColumn)) {
                continue;
            }
            int surfaceY = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    centerColumn.getX(),
                    centerColumn.getZ()
            );
            double effectRadius = Math.min(72.0, Math.max(12.0,
                    system.radiusBlocks() * (system.type() == WeatherSystemType.TORNADO ? 0.18 : 0.32)));
            level.sendParticles(
                    system.type() == WeatherSystemType.TORNADO ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE,
                    system.centerX(), surfaceY + 12.0, system.centerZ(),
                    24, effectRadius * 0.45, 10.0, effectRadius * 0.45, 0.03
            );
            pushEntities(level, system, surfaceY, effectRadius, settings.severeEntityWindStrength());
            if (settings.severeBlockDamageEnabled()
                    && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                damageOnePlant(level, system, surfaceY, effectRadius, gameTime);
            }
        }
    }

    private static void pushEntities(
            ServerLevel level,
            TrackedWeatherSystem system,
            int surfaceY,
            double radius,
            double configuredStrength
    ) {
        AABB bounds = new AABB(
                system.centerX() - radius, surfaceY - 8.0, system.centerZ() - radius,
                system.centerX() + radius, surfaceY + 48.0, system.centerZ() + radius
        );
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, bounds, LivingEntity::isAlive);
        int limit = Math.min(32, entities.size());
        for (int index = 0; index < limit; index++) {
            LivingEntity entity = entities.get(index);
            // A severe identity must persist beyond its forming sample before
            // it can physically move players or other living entities.
            if (!SevereWeatherWindPolicy.canApplyEntityWind(
                    system.type(),
                    system.stage()
            )) {
                continue;
            }
            double dx = entity.getX() - system.centerX();
            double dz = entity.getZ() - system.centerZ();
            double distance = Math.max(1.0, Math.hypot(dx, dz));
            double falloff = Math.max(0.0, 1.0 - distance / radius);
            double strength = configuredStrength * system.intensity() * falloff;
            double rotation = system.type() == WeatherSystemType.TORNADO ? 1.0 : 0.55;
            entity.push(
                    (-dz / distance * rotation - dx / distance * 0.18) * strength,
                    system.type() == WeatherSystemType.TORNADO ? strength * 0.28 : strength * 0.05,
                    (dx / distance * rotation - dz / distance * 0.18) * strength
            );
            entity.hurtMarked = true;
        }
    }

    private static void damageOnePlant(
            ServerLevel level,
            TrackedWeatherSystem system,
            int surfaceY,
            double radius,
            long gameTime
    ) {
        long bits = system.id() * 0x9E3779B97F4A7C15L ^ gameTime * 0xC2B2AE3D27D4EB4FL;
        int x = (int) Math.round(system.centerX() + (Math.floorMod(bits, 101L) / 100.0 - 0.5) * radius * 2.0);
        int z = (int) Math.round(system.centerZ() + (Math.floorMod(bits >>> 9, 101L) / 100.0 - 0.5) * radius * 2.0);
        BlockPos pos = new BlockPos(x, surfaceY, z);
        if (!level.hasChunkAt(pos)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos target = new BlockPos(x, y, z).below();
        BlockState state = level.getBlockState(target);
        if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof BushBlock) {
            level.destroyBlock(target, false);
        }
    }
}
