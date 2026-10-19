package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Read-only, loaded-only reach geometry. It cannot credit or debit water volume. */
public final class RiverReachServices {
    private RiverReachServices() { }

    /** Samples generated reach metadata only at its planned vertical interval. */
    public static Sample sample(Level level, BlockPos position) {
        if (level == null || position == null) return null;
        RiverGenerationChunk.Snapshot metadata;
        if (level instanceof ServerLevel server) {
            if (!server.getServer().isSameThread()) return null;
            var chunk = server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk == null) return null;
            var attachment = chunk.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
            if (attachment == null) return null;
            metadata = attachment.snapshot();
        } else {
            var chunk = ClientWaterSnapshotStore.getAtBlock(level, position.getX(), position.getZ());
            if (chunk == null) return null;
            metadata = chunk.rivers();
        }
        var reach = metadata.reachAt(position.getX() & 15, position.getZ() & 15);
        if (reach == null || position.getY() <= reach.floorY() || position.getY() > reach.topY() + 1) return null;
        return new Sample(metadata.basinId(), reach);
    }

    /**
     * Reorients regional motion (blocks/tick) along the channel and caps it by
     * captured Manning speed (m/s). Pools stay calm and zero motion stays zero.
     */
    public static Current current(RiverGenerationChunk.Reach reach, float modeledX, float modeledZ) {
        float magnitude = (float) Math.hypot(modeledX, modeledZ);
        if (reach == null || !Float.isFinite(magnitude) || reach.kind() == RiverChannelColumn.Kind.LAKE) return Current.STILL;
        float length = (float) Math.hypot(reach.directionX(), reach.directionZ());
        if (length < 0.001f) return Current.STILL;
        float speed = Math.min(magnitude, reach.speed() / 20f);
        return new Current(reach.directionX() / length * speed, reach.directionZ() / length * speed);
    }

    /** Geometry and composed physical current in m/s; stage is the generated surface block Y. */
    public static Observation observe(Level level, BlockPos position) {
        var sample = sample(level, position);
        if (sample == null || !WaterServices.access().isWaterAt(level, position)) return null;
        var flow = WaterServices.access().getCurrent(level, Vec3.atCenterOf(position));
        return new Observation(sample.basinId(), sample.reach().waterY(), sample.reach().floorY(),
                sample.reach().kind(), (float) flow.x, (float) flow.z);
    }

    public record Sample(long basinId, RiverGenerationChunk.Reach reach) { }
    public record Current(float currentX, float currentZ) {
        public static final Current STILL = new Current(0, 0);
        public float speed() { return (float) Math.hypot(currentX, currentZ); }
    }
    public record Observation(long waterbodyId, int stage, int floorY, RiverChannelColumn.Kind kind,
                              float velocityX, float velocityZ) { }
}
