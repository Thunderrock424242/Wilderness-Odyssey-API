package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Map;

/**
 * Encapsulates either a delta sync or a full chunk payload ready to send to a player.
 */
public record ChunkDeltaPayload(
        ChunkPos pos,
        boolean fullChunk,
        CompoundTag fullPayload,
        Map<BlockPos, BlockState> blockUpdates,
        Map<LightLayer, Map<Integer, LightBandDelta>> lightUpdates,
        int changeCost,
        boolean budgetExceeded
) {

    public static ChunkDeltaPayload full(ChunkPos pos, CompoundTag payload, int cost, boolean budgetExceeded) {
        return new ChunkDeltaPayload(pos, true, payload, Collections.emptyMap(), Collections.emptyMap(), cost, budgetExceeded);
    }

    public boolean hasDeltas() {
        return (blockUpdates != null && !blockUpdates.isEmpty()) || (lightUpdates != null && !lightUpdates.isEmpty());
    }
}
