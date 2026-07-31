package com.thunder.wildernessodysseyapi.core;

import com.thunder.wildernessodysseyapi.capabilities.ChunkDataCapability;
import com.thunder.wildernessodysseyapi.watersystem.water.network.GeneratedWaterAttachmentSyncHandler;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Attachment registrations for the mod.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ModConstants.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ChunkDataCapability>> CHUNK_DATA = ATTACHMENTS.register(
            "chunk_data",
            () -> AttachmentType.serializable(holder -> {
                ChunkDataCapability capability = new ChunkDataCapability();
                if (holder instanceof ChunkAccess chunk) {
                    capability.setDirtyListener(() -> chunk.setUnsaved(true));
                }
                return capability;
            }).build()
    );

    /** Persistent sparse water volume owned by each loaded chunk. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WaterVolumeChunk>> WATER_VOLUME = ATTACHMENTS.register(
            "water_volume",
            () -> AttachmentType.serializable(holder -> {
                WaterVolumeChunk volume = new WaterVolumeChunk();
                if (holder instanceof ChunkAccess chunk) {
                    volume.setDirtyListener(() -> chunk.setUnsaved(true));
                }
                return volume;
            }).build()
    );

    /**
     * Persistent compact baseline recorded while a {@link net.minecraft.world.level.chunk.ProtoChunk} is generated.
     *
     * <p>The custom sync handler publishes the decoded client value immediately,
     * because NeoForge delivers synchronized attachments after the client chunk
     * load event used by the renderer's normal fast path.</p>
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GeneratedWaterChunk>> GENERATED_WATER = ATTACHMENTS.register(
            "generated_water",
            () -> AttachmentType.serializable(holder -> {
                GeneratedWaterChunk generated = new GeneratedWaterChunk();
                if (holder instanceof ChunkAccess chunk) {
                    generated.setDirtyListener(() -> chunk.setUnsaved(true));
                }
                return generated;
            }).sync(GeneratedWaterAttachmentSyncHandler.INSTANCE).build()
    );

    private ModAttachments() {
    }
}
