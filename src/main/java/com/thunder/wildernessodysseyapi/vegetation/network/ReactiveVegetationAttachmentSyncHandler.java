package com.thunder.wildernessodysseyapi.vegetation.network;

import com.thunder.wildernessodysseyapi.vegetation.client.ClientVegetationClimateStore;
import com.thunder.wildernessodysseyapi.vegetation.state.ReactiveVegetationState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Synchronizes compact chunk climate and publishes it for client visual queries.
 *
 * <p>Publication happens in the decoder callback because NeoForge installs the
 * returned attachment only after this method completes. This avoids polling
 * client chunks or querying server weather from rendering code.</p>
 */
public final class ReactiveVegetationAttachmentSyncHandler
        implements AttachmentSyncHandler<ReactiveVegetationState> {

    /** Shared stateless handler used by the attachment registration. */
    public static final ReactiveVegetationAttachmentSyncHandler INSTANCE =
            new ReactiveVegetationAttachmentSyncHandler();

    private ReactiveVegetationAttachmentSyncHandler() {
    }

    /** Writes the full compact regional state for initial and later syncs. */
    @Override
    public void write(
            RegistryFriendlyByteBuf buffer,
            ReactiveVegetationState attachment,
            boolean initialSync
    ) {
        ReactiveVegetationState.STREAM_CODEC.encode(buffer, attachment);
    }

    /** Publishes the decoded client snapshot before NeoForge installs the attachment. */
    @Override
    public ReactiveVegetationState read(
            IAttachmentHolder holder,
            RegistryFriendlyByteBuf buffer,
            @Nullable ReactiveVegetationState previousValue
    ) {
        ReactiveVegetationState decoded = ReactiveVegetationState.STREAM_CODEC.decode(buffer);
        if (holder instanceof LevelChunk chunk && chunk.getLevel().isClientSide) {
            ClientVegetationClimateStore.publish(
                    chunk.getLevel(),
                    chunk.getPos().x,
                    chunk.getPos().z,
                    decoded.snapshot()
            );
        }
        return decoded;
    }
}
