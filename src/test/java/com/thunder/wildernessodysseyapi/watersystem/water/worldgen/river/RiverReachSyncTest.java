package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RiverReachSyncTest {
    @Test
    void handlerSendsCompletedReachesWithoutTemporaryTerrain() {
        var data = RiverGenerationChunk.captured(17, new int[512]);
        data.complete(91, 0, 0, List.of(
                new RiverChannelColumn(3, 4, 140, 138, 140, 1, 0, 1.2f, RiverChannelColumn.Kind.STREAM)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            RiverReachAttachmentSyncHandler.INSTANCE.write(buffer, data, true);
            assertTrue(buffer.readableBytes() < 48);
            var decoded = RiverReachAttachmentSyncHandler.INSTANCE.read(new AttachmentHolder() {}, buffer, null);
            assertEquals(91, decoded.basinId());
            assertEquals(data.reachAt(3, 4), decoded.reachAt(3, 4));
            assertFalse(decoded.hasTerrain(17));
        } finally { buffer.release(); }
    }
}
