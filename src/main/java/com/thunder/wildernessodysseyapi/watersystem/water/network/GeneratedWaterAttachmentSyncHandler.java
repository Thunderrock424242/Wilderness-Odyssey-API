package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Decodes generated-water chunk attachments and publishes their client render snapshot.
 *
 * <p>NeoForge sends synchronized chunk attachments after the vanilla chunk packet.
 * Consequently, {@code ChunkEvent.Load} can run before generated-water metadata
 * exists on the client chunk. Publishing the decoded value from this exact sync
 * callback closes that ordering gap without polling loaded chunks.</p>
 */
public final class GeneratedWaterAttachmentSyncHandler
        implements AttachmentSyncHandler<GeneratedWaterChunk> {

    /** Shared stateless handler used by the generated-water attachment registration. */
    public static final GeneratedWaterAttachmentSyncHandler INSTANCE =
            new GeneratedWaterAttachmentSyncHandler();

    private GeneratedWaterAttachmentSyncHandler() {
    }

    /** Writes the complete immutable generated-water baseline for initial and later syncs. */
    @Override
    public void write(
            RegistryFriendlyByteBuf buffer,
            GeneratedWaterChunk attachment,
            boolean initialSync
    ) {
        GeneratedWaterChunk.STREAM_CODEC.encode(buffer, attachment);
    }

    /**
     * Publishes the decoded baseline before returning it to NeoForge for chunk installation.
     *
     * <p>The payload handler runs on the client main thread. The decoded value is
     * used directly because NeoForge installs it on the holder only after this
     * method returns.</p>
     */
    @Override
    public GeneratedWaterChunk read(
            IAttachmentHolder holder,
            RegistryFriendlyByteBuf buffer,
            @Nullable GeneratedWaterChunk previousValue
    ) {
        GeneratedWaterChunk decoded = GeneratedWaterChunk.STREAM_CODEC.decode(buffer);
        if (holder instanceof LevelChunk chunk && chunk.getLevel().isClientSide) {
            ClientWaterSnapshotStore.publishGenerated(
                    chunk.getLevel(),
                    chunk.getPos().x,
                    chunk.getPos().z,
                    decoded
            );
        }
        return decoded;
    }
}
