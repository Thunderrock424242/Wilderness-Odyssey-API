package com.thunder.wildernessodysseyapi.vegetation.client;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientVegetationDirtyQueueTest {

    @AfterEach
    void clearClientMirror() {
        ClientVegetationClimateStore.clear(null);
    }

    @Test
    void reloadedChunkRejoinsAtTheTailOfTheDirtyQueue() {
        ClientVegetationClimateStore.clear(null);
        ClientVegetationClimateStore.publish(null, 0, 0, VegetationClimateState.DEFAULT);
        ClientVegetationClimateStore.forget(null, 0, 0);
        ClientVegetationClimateStore.publish(null, 1, 0, VegetationClimateState.DEFAULT);
        ClientVegetationClimateStore.publish(null, 0, 0, VegetationClimateState.DEFAULT);

        assertEquals(
                List.of(ChunkPos.asLong(1, 0), ChunkPos.asLong(0, 0)),
                ClientVegetationClimateStore.drainDirty(null, 2)
        );
    }

    @Test
    void unloadChurnLeavesOnlyCurrentChunksToRebuild() {
        ClientVegetationClimateStore.clear(null);
        for (int chunkX = 0; chunkX < 2_000; chunkX++) {
            ClientVegetationClimateStore.publish(null, chunkX, 0, VegetationClimateState.DEFAULT);
            ClientVegetationClimateStore.forget(null, chunkX, 0);
        }
        ClientVegetationClimateStore.publish(null, 4_000, 0, VegetationClimateState.DEFAULT);
        ClientVegetationClimateStore.publish(null, 4_001, 0, VegetationClimateState.DEFAULT);
        ClientVegetationClimateStore.publish(null, 4_002, 0, VegetationClimateState.DEFAULT);

        assertEquals(
                List.of(ChunkPos.asLong(4_000, 0), ChunkPos.asLong(4_001, 0)),
                ClientVegetationClimateStore.drainDirty(null, 2)
        );
        assertEquals(List.of(ChunkPos.asLong(4_002, 0)), ClientVegetationClimateStore.drainDirty(null, 2));
        assertEquals(List.of(), ClientVegetationClimateStore.drainDirty(null, 2));
    }
}
