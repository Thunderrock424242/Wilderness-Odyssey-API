package com.thunder.wildernessodysseyapi.vegetation.network;

import com.thunder.wildernessodysseyapi.dataengine.DataEngineIds;
import com.thunder.wildernessodysseyapi.dataengine.network.DataDelta;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactiveVegetationDataDeltaCodecTest {

    @Test
    void existingSnapshotCodecRoundTripsInsideDataEngineDelta() {
        ReactiveVegetationSyncPayload payload = ReactiveVegetationSyncPayload.from(
                ResourceLocation.withDefaultNamespace("overworld"),
                -12,
                37,
                44L,
                new VegetationClimateState(
                        0.40,
                        0.65,
                        0.25,
                        0.80,
                        VegetationSeasonState.WET,
                        900L,
                        920L,
                        5,
                        18.0
                )
        );

        DataDelta delta = ReactiveVegetationDataDeltaCodec.encode(payload);
        ReactiveVegetationSyncPayload decoded = ReactiveVegetationDataDeltaCodec.decode(delta);

        assertEquals(DataEngineIds.REACTIVE_VEGETATION, delta.systemId());
        assertEquals(
                ReactiveVegetationDataDeltaCodec.targetKey(payload.dimension(), -12, 37),
                delta.targetKey()
        );
        assertEquals(ReactiveVegetationDataDeltaCodec.COMPLETE_SNAPSHOT_FIELDS, delta.changedFields());
        assertEquals(UpdatePriority.NORMAL, delta.priority());
        assertEquals(payload, decoded);
    }

    @Test
    void rejectsUnknownFieldMaskAndMismatchedTarget() {
        ReactiveVegetationSyncPayload payload = ReactiveVegetationSyncPayload.from(
                ResourceLocation.withDefaultNamespace("overworld"),
                4,
                9,
                10L,
                VegetationClimateState.DEFAULT
        );
        DataDelta valid = ReactiveVegetationDataDeltaCodec.encode(payload);
        DataDelta unknownFields = new DataDelta(
                valid.systemId(),
                valid.targetKey(),
                2L,
                valid.priority(),
                valid.body()
        );
        DataDelta mismatchedTarget = new DataDelta(
                valid.systemId(),
                ChunkPos.asLong(5, 9),
                valid.changedFields(),
                valid.priority(),
                valid.body()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ReactiveVegetationDataDeltaCodec.decode(unknownFields)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ReactiveVegetationDataDeltaCodec.decode(mismatchedTarget)
        );
    }

    @Test
    void sameCoordinatesInDifferentDimensionsDoNotCoalesce() {
        long overworld = ReactiveVegetationDataDeltaCodec.targetKey(
                ResourceLocation.withDefaultNamespace("overworld"),
                12,
                -8
        );
        long nether = ReactiveVegetationDataDeltaCodec.targetKey(
                ResourceLocation.withDefaultNamespace("the_nether"),
                12,
                -8
        );

        assertNotEquals(overworld, nether);
    }
}
