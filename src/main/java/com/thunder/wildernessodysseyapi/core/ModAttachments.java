package com.thunder.wildernessodysseyapi.core;

import com.thunder.wildernessodysseyapi.capabilities.ChunkDataCapability;
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

    private ModAttachments() {
    }
}
