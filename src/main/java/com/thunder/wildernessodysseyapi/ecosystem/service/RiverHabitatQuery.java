package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.ecosystem.api.RiverHabitat;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverReachServices;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/** Loaded-only habitat observations through the current water and climate owners. */
final class RiverHabitatQuery {
    private RiverHabitatQuery() { }

    static Optional<RiverHabitat> sample(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !level.getServer().isSameThread() || !level.isInWorldBounds(position))
            return Optional.empty();
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return Optional.empty();
        var reach = RiverReachServices.sample(level, position);
        var water = WaterServices.access();
        if (reach == null || !water.isWaterAt(level, position)) return Optional.empty();
        var body = water.getWaterBody(level, position).orElse(null);
        if (body == null || !Double.isFinite(body.depth()) || body.depth() <= 0) return Optional.empty();
        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(position.getX()), QuartPos.fromBlock(position.getY()),
                QuartPos.fromBlock(position.getZ())).unwrapKey().map(key -> key.location()).orElse(null);
        if (biome == null) return Optional.empty();
        var conditions = water.getWatershedConditions(level, position);
        return Optional.of(new RiverHabitat(reach.basinId(), biome, reach.reach().kind(), body.surfaceHeight(), body.depth(),
                water.getCurrent(level, Vec3.atCenterOf(position)), conditions.clarity(), conditions.sediment(),
                WeatherServices.query().temperatureAt(level, position), conditions.flooding()));
    }
}
