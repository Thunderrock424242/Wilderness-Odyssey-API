package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/** Publishes late attachment delivery through the existing immutable water store. */
public final class RiverReachAttachmentSyncHandler implements AttachmentSyncHandler<RiverGenerationChunk> {
    public static final RiverReachAttachmentSyncHandler INSTANCE = new RiverReachAttachmentSyncHandler();
    private RiverReachAttachmentSyncHandler() { }
    @Override
    public void write(RegistryFriendlyByteBuf buffer, RiverGenerationChunk value, boolean initialSync) {
        RiverGenerationChunk.STREAM_CODEC.encode(buffer, value);
    }
    @Override
    public RiverGenerationChunk read(IAttachmentHolder holder, RegistryFriendlyByteBuf buffer,
                                     @Nullable RiverGenerationChunk previousValue) {
        var value = RiverGenerationChunk.STREAM_CODEC.decode(buffer);
        if (holder instanceof LevelChunk chunk && chunk.getLevel().isClientSide) {
            ClientWaterSnapshotStore.publishRivers(chunk.getLevel(), chunk.getPos().x, chunk.getPos().z, value.snapshot());
        }
        return value;
    }
}
