package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

import java.util.List;
import java.util.Optional;

/** Computes a cached same-species centroid for low-priority herd regrouping. */
public final class HerdAwarenessService {

    private final NearbyLivingEntityCache nearbyEntities;

    HerdAwarenessService(NearbyLivingEntityCache nearbyEntities) {
        this.nearbyEntities = nearbyEntities;
    }

    /** Returns the centroid of nearby same-type animals, excluding the caller. */
    public Optional<EnvironmentalContext.HerdCenter> assess(PathfinderMob animal, int radius) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        List<LivingEntity> nearby = nearbyEntities.query(level, animal.blockPosition(), radius, level.getGameTime());
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int members = 0;
        for (LivingEntity candidate : nearby) {
            if (candidate != animal && candidate.getType() == animal.getType()) {
                x += candidate.getX();
                y += candidate.getY();
                z += candidate.getZ();
                members++;
            }
        }
        if (members == 0) {
            return Optional.empty();
        }
        BlockPos center = BlockPos.containing(x / members, y / members, z / members);
        return Optional.of(new EnvironmentalContext.HerdCenter(
                center,
                members + 1,
                animal.distanceToSqr(center.getCenter())
        ));
    }
}
