package com.thunder.wildernessodysseyapi.ecosystem.distant.network;

import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeForm;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the bounded group snapshot wire contract. */
class DistantWildlifeSyncPayloadTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "overworld"
    );

    @Test
    void codecRoundTripPreservesDeterministicMovementInputs() {
        var group = new DistantWildlifeSyncPayload.GroupSnapshot(
                4L,
                ResourceLocation.fromNamespaceAndPath("examplemod", "deer"),
                18,
                120.5,
                72.0,
                -48.25,
                0.6F,
                0.8F,
                0.45F,
                91L,
                1_200L,
                DistantWildlifeForm.GROUND
        );
        var payload = new DistantWildlifeSyncPayload(
                OVERWORLD,
                DistantWildlifeSyncPayload.DATA_VERSION,
                7L,
                true,
                1_260L,
                96,
                512,
                32,
                100,
                List.of(group)
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DistantWildlifeSyncPayload.STREAM_CODEC.encode(buffer, payload);
            DistantWildlifeSyncPayload decoded = DistantWildlifeSyncPayload.STREAM_CODEC.decode(buffer);

            assertEquals(payload, decoded);
            assertEquals(group.positionAt(1_260L), decoded.groups().getFirst().positionAt(1_260L));
        } finally {
            buffer.release();
        }
    }

    @Test
    void codecRejectsExcessGroupCountBeforeReadingGroupData() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(OVERWORLD);
            buffer.writeVarInt(DistantWildlifeSyncPayload.DATA_VERSION);
            buffer.writeVarLong(1L);
            buffer.writeBoolean(true);
            buffer.writeVarLong(20L);
            buffer.writeVarInt(96);
            buffer.writeVarInt(512);
            buffer.writeVarInt(32);
            buffer.writeVarInt(100);
            buffer.writeVarInt(DistantWildlifeSyncPayload.MAXIMUM_GROUPS + 1);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> DistantWildlifeSyncPayload.STREAM_CODEC.decode(buffer)
            );
        } finally {
            buffer.release();
        }
    }

    @Test
    void disabledSnapshotCannotCarryRepresentedPopulation() {
        var group = new DistantWildlifeSyncPayload.GroupSnapshot(
                1L,
                ResourceLocation.fromNamespaceAndPath("examplemod", "deer"),
                1,
                0.0,
                64.0,
                0.0,
                1.0F,
                0.0F,
                0.0F,
                2L,
                0L,
                DistantWildlifeForm.GROUND
        );

        assertThrows(IllegalArgumentException.class, () -> new DistantWildlifeSyncPayload(
                OVERWORLD,
                DistantWildlifeSyncPayload.DATA_VERSION,
                1L,
                false,
                0L,
                96,
                512,
                32,
                100,
                List.of(group)
        ));
    }

    @Test
    void constructorRejectsAggregatePopulationAboveWireSafetyCap() {
        List<DistantWildlifeSyncPayload.GroupSnapshot> groups = java.util.stream.IntStream
                .range(0, 65)
                .mapToObj(index -> new DistantWildlifeSyncPayload.GroupSnapshot(
                        index + 1L,
                        ResourceLocation.fromNamespaceAndPath("examplemod", "deer"),
                        64,
                        index,
                        64.0,
                        0.0,
                        1.0F,
                        0.0F,
                        0.0F,
                        index,
                        0L,
                        DistantWildlifeForm.GROUND
                ))
                .toList();

        assertThrows(IllegalArgumentException.class, () -> new DistantWildlifeSyncPayload(
                OVERWORLD,
                DistantWildlifeSyncPayload.DATA_VERSION,
                1L,
                true,
                0L,
                96,
                512,
                32,
                100,
                groups
        ));
    }
}
