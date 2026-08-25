package com.thunder.wildernessodysseyapi.ecosystem.debug.map;

import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeForm;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the bounded, self-consistent ecosystem map wire contract. */
class EcosystemDebugMapPayloadTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "overworld"
    );
    private static final ResourceLocation DEER = ResourceLocation.fromNamespaceAndPath(
            "examplemod", "deer"
    );

    @Test
    void codecRoundTripPreservesCellsConditionsAndGroupMarkers() {
        EcosystemDebugMapPayload original = populatedPayload();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EcosystemDebugMapPayload.STREAM_CODEC.encode(buffer, original);
            EcosystemDebugMapPayload decoded = EcosystemDebugMapPayload.STREAM_CODEC.decode(buffer);

            assertEquals(original, decoded);
            assertEquals(9, decoded.cells().size());
            assertEquals(7, decoded.cells().get(4).totalPopulation());
            assertEquals(DEER, decoded.cells().get(4).species().getFirst().species());
            assertEquals(0.625F, decoded.cells().get(4).foodAvailability());
            assertEquals(0.25F, decoded.groups().getFirst().populationRemainder());
        } finally {
            buffer.release();
        }
    }

    @Test
    void constructorRequiresOneUniqueCellForEveryWindowCoordinate() {
        List<EcosystemDebugMapPayload.CellSnapshot> cells = emptyCells();
        cells.removeLast();

        assertThrows(IllegalArgumentException.class, () -> new EcosystemDebugMapPayload(
                OVERWORLD, EcosystemDebugMapPayload.DATA_VERSION, 100L,
                0, 0, 0, 0, 64, 1,
                true, true, true, 96, cells, List.of()
        ));
    }

    @Test
    void constructorRejectsCellTotalsThatDoNotMatchMarkers() {
        List<EcosystemDebugMapPayload.CellSnapshot> cells = emptyCells();
        cells.set(4, populatedCell());

        assertThrows(IllegalArgumentException.class, () -> new EcosystemDebugMapPayload(
                OVERWORLD, EcosystemDebugMapPayload.DATA_VERSION, 100L,
                0, 0, 0, 0, 64, 1,
                true, true, true, 96, cells, List.of()
        ));
    }

    @Test
    void codecRejectsExcessCellCountBeforeAllocatingCells() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(OVERWORLD);
            buffer.writeVarInt(EcosystemDebugMapPayload.DATA_VERSION);
            buffer.writeVarLong(100L);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeVarInt(64);
            buffer.writeVarInt(1);
            buffer.writeByte(7);
            buffer.writeVarInt(96);
            buffer.writeVarInt(EcosystemDebugMapPayload.MAXIMUM_CELLS + 1);

            assertThrows(
                    DecoderException.class,
                    () -> EcosystemDebugMapPayload.STREAM_CODEC.decode(buffer)
            );
        } finally {
            buffer.release();
        }
    }

    private static EcosystemDebugMapPayload populatedPayload() {
        List<EcosystemDebugMapPayload.CellSnapshot> cells = emptyCells();
        cells.set(4, populatedCell());
        var group = new EcosystemDebugMapPayload.GroupSnapshot(
                11L, DEER, 7, 0.25F,
                10.5, 12.5, 0.6F, 0.8F, DistantWildlifeForm.GROUND
        );
        return new EcosystemDebugMapPayload(
                OVERWORLD, EcosystemDebugMapPayload.DATA_VERSION, 100L,
                3, 5, 0, 0, 64, 1,
                true, true, true, 96, cells, List.of(group)
        );
    }

    private static EcosystemDebugMapPayload.CellSnapshot populatedCell() {
        return new EcosystemDebugMapPayload.CellSnapshot(
                0, 0, WildlifeSimulationLod.ACTIVE,
                1, 7, List.of(new EcosystemDebugMapPayload.SpeciesPopulation(DEER, 7)),
                true, 1, 0,
                0.625F, 0.75F, 0.2F, 0.1F, 0.3F, 80L
        );
    }

    private static List<EcosystemDebugMapPayload.CellSnapshot> emptyCells() {
        List<EcosystemDebugMapPayload.CellSnapshot> cells = new ArrayList<>();
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                cells.add(new EcosystemDebugMapPayload.CellSnapshot(
                        x, z, WildlifeSimulationLod.NEAR,
                        0, 0, List.of(), false, x, z,
                        0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0L
                ));
            }
        }
        return cells;
    }
}
