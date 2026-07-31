package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that the custom sync handler preserves generated-water wire data. */
class GeneratedWaterAttachmentSyncHandlerTest {

    @Test
    void roundTripsGeneratedBaselineThroughCustomAttachmentHandler() {
        GeneratedWaterChunk original = new GeneratedWaterChunk();
        BlockPos water = new BlockPos(5, 62, 9);
        original.recordCell(water, GeneratedWaterChunk.Cell.of(
                8,
                false,
                GeneratedWaterChunk.BodyType.OCEAN
        ));
        original.recordSurfaceCover(water.above(), true);

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE
        );
        try {
            GeneratedWaterAttachmentSyncHandler.INSTANCE.write(buffer, original, true);
            GeneratedWaterChunk decoded = GeneratedWaterAttachmentSyncHandler.INSTANCE.read(
                    new AttachmentHolder() { },
                    buffer,
                    null
            );

            GeneratedWaterChunk.WaterSpan span = decoded.spanAt(5, 62, 9);
            assertNotNull(span);
            assertEquals(GeneratedWaterChunk.BodyType.OCEAN, span.cell().bodyType());
            assertEquals(8, span.cell().amount());
            assertTrue(decoded.snapshot().surfaceCovered(5, 9));
            assertEquals(original.revision(), decoded.revision());
        } finally {
            buffer.release();
        }
    }
}
