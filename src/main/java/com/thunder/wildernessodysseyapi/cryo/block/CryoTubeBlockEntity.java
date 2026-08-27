package com.thunder.wildernessodysseyapi.cryo.block;

import com.thunder.wildernessodysseyapi.cinematic.CinematicActor;
import com.thunder.wildernessodysseyapi.cinematic.sequence.CryoWakeupSequence;
import com.thunder.wildernessodysseyapi.worldgen.spawn.CryoSpawnData;
import com.thunder.wildernessodysseyapi.worldgen.spawn.WorldSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the cryo tube and its synchronized cinematic cue state.
 */
public class CryoTubeBlockEntity extends BlockEntity implements CinematicActor {
    private static final String ANIMATION_STATE_TAG = "cinematic_animation_state";

    private CryoTubeAnimationState animationState = CryoTubeAnimationState.IDLE;

    public CryoTubeBlockEntity(BlockPos pos, BlockState state) {
        super(CryoTubeBlock.CRYO_TUBE_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            CryoSpawnData data = CryoSpawnData.get(serverLevel);
            if (data.add(this.worldPosition)) {
                WorldSpawnHandler.refreshWorldSpawn(serverLevel);
            }
        }
    }

    /** Returns the server-synchronized state reserved for cinematic presentation layers. */
    public CryoTubeAnimationState getAnimationState() {
        return animationState;
    }

    /**
     * Translates generic cinematic cues into cryo-owned animation states.
     *
     * <p>The original static Blockbench model remains the rendering source of
     * truth. The sequence may still retain cue state for non-model presentation
     * layers without replacing that geometry.</p>
     */
    @Override
    public boolean applyCinematicCue(ResourceLocation sequenceId, ResourceLocation cueId) {
        if (!CryoWakeupSequence.ID.equals(sequenceId)) {
            return false;
        }

        CryoTubeAnimationState next;
        if (CryoWakeupSequence.CUE_IDLE.equals(cueId)) {
            next = CryoTubeAnimationState.IDLE;
        } else if (CryoWakeupSequence.CUE_SUSPENDED.equals(cueId)) {
            next = CryoTubeAnimationState.SUSPENDED;
        } else if (CryoWakeupSequence.CUE_DIAGNOSTIC.equals(cueId)) {
            next = CryoTubeAnimationState.DIAGNOSTIC;
        } else if (CryoWakeupSequence.CUE_REWARMING.equals(cueId)) {
            next = CryoTubeAnimationState.REWARMING;
        } else if (CryoWakeupSequence.CUE_CARDIAC_PACING.equals(cueId)) {
            next = CryoTubeAnimationState.CARDIAC_PACING;
        } else if (CryoWakeupSequence.CUE_DRAINING.equals(cueId)) {
            next = CryoTubeAnimationState.DRAINING;
        } else if (CryoWakeupSequence.CUE_MASK_RELEASE.equals(cueId)) {
            next = CryoTubeAnimationState.MASK_RELEASE;
        } else if (CryoWakeupSequence.CUE_OPENING.equals(cueId)) {
            next = CryoTubeAnimationState.OPENING;
        } else if (CryoWakeupSequence.CUE_OPEN.equals(cueId)) {
            next = CryoTubeAnimationState.OPEN;
        } else {
            return false;
        }
        setAnimationState(next);
        return true;
    }

    private void setAnimationState(CryoTubeAnimationState next) {
        if (animationState == next) {
            return;
        }
        animationState = next;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(ANIMATION_STATE_TAG, animationState.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains(ANIMATION_STATE_TAG)) {
            animationState = CryoTubeAnimationState.IDLE;
            return;
        }
        try {
            animationState = CryoTubeAnimationState.valueOf(tag.getString(ANIMATION_STATE_TAG));
        } catch (IllegalArgumentException ignored) {
            animationState = CryoTubeAnimationState.IDLE;
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
