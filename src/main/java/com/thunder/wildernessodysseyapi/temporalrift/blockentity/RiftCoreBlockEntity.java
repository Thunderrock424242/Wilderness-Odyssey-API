package com.thunder.wildernessodysseyapi.temporalrift.blockentity;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlockEntities;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftState;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftServerController;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

public class RiftCoreBlockEntity extends BlockEntity implements software.bernie.geckolib.animatable.GeoBlockEntity {
    public enum VisualMode {
        OVERWORLD_SINKHOLE,
        BEFORE_SKY_TEAR,
        BEFORE_GROUND_RETURN,
        TRANSIENT_RETURN,
        ANOMALY_RING
    }

    private float renderScale = 1.0F;
    private VisualMode visualMode = VisualMode.OVERWORLD_SINKHOLE;
    private RiftState riftState = RiftState.ACTIVE;
    private Direction facing = Direction.NORTH;
    private boolean compositionPlaced;
    private long managedCloseTick;
    private final RiftServerController hybridController = new RiftServerController();
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache animationCache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    private static final software.bernie.geckolib.animation.RawAnimation IDLE =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.anomaly_rift_shell.idle");
    private static final software.bernie.geckolib.animation.RawAnimation CRITICAL =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.anomaly_rift_shell.critical");

    public RiftCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TemporalRiftBlockEntities.RIFT_CORE.get(), pos, state);
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "rift_shell", 8,
                state -> state.setAndContinue(riftState == RiftState.CRITICAL ? CRITICAL : IDLE)));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
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

    public boolean isHybrid() { return visualMode == VisualMode.ANOMALY_RING; }
    public RiftState getRiftState() { return riftState; }
    public Direction getFacing() { return facing; }
    public boolean isCompositionPlaced() { return compositionPlaced; }
    public long getManagedCloseTick() { return managedCloseTick; }
    public void setManagedCloseTick(long tick) { managedCloseTick = tick; syncHybrid(); }

    /** Server-side state transitions publish one normal block-entity update. */
    public void setRiftState(RiftState state) {
        if (riftState == state) return;
        riftState = state;
        syncHybrid();
    }

    public void setFacing(Direction direction) {
        Direction horizontal = direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
        if (facing == horizontal) return;
        facing = horizontal;
        syncHybrid();
    }

    public void markCompositionPlaced() {
        compositionPlaced = true;
        syncHybrid();
    }

    private void syncHybrid() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Invoked only by the server ticker for a loaded hybrid block entity. */
    public void tickHybrid(ServerLevel serverLevel) {
        if (isHybrid()) hybridController.tick(serverLevel, this);
    }

    @Override
    public void setRemoved() {
        hybridController.clear();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        hybridController.clear();
        super.onChunkUnloaded();
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
        tag.putString("riftState", riftState.name());
        tag.putString("riftFacing", facing.getName());
        tag.putBoolean("riftCompositionPlaced", compositionPlaced);
        tag.putLong("riftManagedCloseTick", managedCloseTick);
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
        riftState = RiftState.fromName(tag.getString("riftState"));
        Direction loadedFacing = Direction.byName(tag.getString("riftFacing"));
        facing = loadedFacing != null && loadedFacing.getAxis().isHorizontal() ? loadedFacing : Direction.NORTH;
        compositionPlaced = tag.getBoolean("riftCompositionPlaced");
        managedCloseTick = Math.max(0, tag.getLong("riftManagedCloseTick"));
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
