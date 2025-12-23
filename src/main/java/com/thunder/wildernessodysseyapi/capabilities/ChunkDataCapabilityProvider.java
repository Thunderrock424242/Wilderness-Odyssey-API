package com.thunder.wildernessodysseyapi.capabilities;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capability;
import net.neoforged.neoforge.capabilities.CapabilityManager;
import net.neoforged.neoforge.capabilities.CapabilityToken;
import net.neoforged.neoforge.capabilities.ICapabilitySerializable;
import net.neoforged.neoforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Provider for the chunk data capability.
 */
public class ChunkDataCapabilityProvider implements ICapabilitySerializable<CompoundTag> {

    public static final ResourceLocation IDENTIFIER = new ResourceLocation(MOD_ID, "chunk_data");
    public static final Capability<ChunkDataCapability> CHUNK_DATA_CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private final ChunkDataCapability backend = new ChunkDataCapability();
    private final LazyOptional<ChunkDataCapability> optional = LazyOptional.of(() -> backend);

    public ChunkDataCapabilityProvider(LevelChunk chunk) {
        backend.setDirtyListener(() -> chunk.setUnsaved(true));
    }

    public void invalidate() {
        optional.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == CHUNK_DATA_CAPABILITY ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return backend.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        backend.deserializeNBT(nbt);
    }
}
