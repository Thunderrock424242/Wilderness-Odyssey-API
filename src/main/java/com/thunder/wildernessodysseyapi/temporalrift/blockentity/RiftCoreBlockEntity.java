package com.thunder.wildernessodysseyapi.temporalrift.blockentity;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RiftCoreBlockEntity extends BlockEntity {
    public enum VisualMode {
        OVERWORLD_SINKHOLE,
        BEFORE_SKY_TEAR,
        BEFORE_GROUND_RETURN,
        TRANSIENT_RETURN
    }

    private float renderScale = 1.0F;
    private VisualMode visualMode = VisualMode.OVERWORLD_SINKHOLE;

    public RiftCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TemporalRiftBlockEntities.RIFT_CORE.get(), pos, state);
    }

    public float getRenderScale() {
        return renderScale;
    }

    public void setRenderScale(float renderScale) {
        float clamped = Mth.clamp(renderScale, 0.08F, 1.0F);
        if (Math.abs(this.renderScale - clamped) < 0.01F) {
            return;
        }

        this.renderScale = clamped;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    public VisualMode getVisualMode() {
        return visualMode;
    }

    public void setVisualMode(VisualMode visualMode) {
        if (this.visualMode == visualMode) {
            return;
        }

        this.visualMode = visualMode;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("renderScale", renderScale);
        tag.putString("visualMode", visualMode.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        renderScale = tag.contains("renderScale") ? Mth.clamp(tag.getFloat("renderScale"), 0.08F, 1.0F) : 1.0F;
        if (tag.contains("visualMode")) {
            try {
                visualMode = VisualMode.valueOf(tag.getString("visualMode"));
            } catch (IllegalArgumentException ignored) {
                visualMode = VisualMode.OVERWORLD_SINKHOLE;
            }
        } else {
            visualMode = VisualMode.OVERWORLD_SINKHOLE;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
