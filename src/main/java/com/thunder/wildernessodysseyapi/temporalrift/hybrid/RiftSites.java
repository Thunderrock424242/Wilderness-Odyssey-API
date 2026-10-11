package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import com.thunder.wildernessodysseyapi.worldgen.structure.rift.RiftRockPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/** Loaded-only cold-path hybrid site creation; no sinkhole or distant dimension generation. */
public final class RiftSites {
    public record Result(@Nullable RiftCoreBlockEntity core, String message) { }
    private RiftSites() { }

    public static Result create(ServerLevel level, BlockPos anchor, Direction facing, boolean rocks) {
        if (!level.getWorldBorder().isWithinBounds(anchor) || anchor.getY() < level.getMinBuildHeight() + 3
                || anchor.getY() + 7 >= level.getMaxBuildHeight()) return new Result(null, "Invalid world boundary/build height.");
        for (int x = (anchor.getX() - 3) >> 4; x <= (anchor.getX() + 3) >> 4; x++) {
            for (int z = (anchor.getZ() - 3) >> 4; z <= (anchor.getZ() + 3) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return new Result(null, "Ring footprint crosses an unloaded chunk.");
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-3, 0, -3), anchor.offset(3, 6, 3))) {
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir()) {
                return new Result(null, "The ring needs clear air at " + pos.toShortString() + ".");
            }
        }
        if (!level.setBlock(anchor, TemporalRiftBlocks.RIFT_CORE.get().defaultBlockState(), 3)
                || !(level.getBlockEntity(anchor) instanceof RiftCoreBlockEntity core)) {
            return new Result(null, "The core block could not be placed.");
        }
        core.setVisualMode(RiftCoreBlockEntity.VisualMode.ANOMALY_RING);
        core.setFacing(facing);
        core.setRiftState(RiftState.ACTIVE);
        if (!rocks) return new Result(core, "Ring placed; no terrain requested.");
        var result = RiftRockPlacement.place(level, core);
        return new Result(core, result.reason());
    }

    public static @Nullable BlockPos findLoadedSite(ServerLevel level, int radius) {
        BlockPos spawn = level.getSharedSpawnPos();
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = spawn.getX() + (radius > 0 ? level.random.nextInt(radius * 2 + 1) - radius : 0);
            int z = spawn.getZ() + (radius > 0 ? level.random.nextInt(radius * 2 + 1) - radius : 0);
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) continue;
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 4;
            if (y + 7 < level.getMaxBuildHeight()) return new BlockPos(x, y, z);
        }
        return null;
    }
}
