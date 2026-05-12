package com.thunder.wildernessodysseyapi.temporalrift.block;

import com.mojang.serialization.MapCodec;
import com.thunder.wildernessodysseyapi.temporalrift.RiftEffectHelper;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftManager;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftTeleporter;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class RiftCoreBlock extends BaseEntityBlock {
    public static final MapCodec<RiftCoreBlock> CODEC = simpleCodec(RiftCoreBlock::new);
    private static final String NBT_LAST_RETURN_ATTEMPT_TICK = "temporalrift_last_return_attempt_tick";
    private static final long RETURN_ATTEMPT_COOLDOWN_TICKS = 40L;

    public RiftCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftCoreBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            handlePortalUse(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            handlePortalUse(serverPlayer, serverLevel, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                handleEntry(serverPlayer, serverLevel, pos);
            } else if (level.dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY)) {
                handleReturnAttempt(serverPlayer, serverLevel);
            }
        }
    }

    private void handlePortalUse(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.dimension().equals(Level.OVERWORLD)) {
            handleEntry(player, level, pos);
        } else if (level.dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY)) {
            handleReturnAttempt(player, level);
        }
    }

    private void handleEntry(ServerPlayer player, ServerLevel overworldLevel, BlockPos riftPos) {
        ServerLevel pastLevel = overworldLevel.getServer().getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
        if (pastLevel == null) {
            player.sendSystemMessage(Component.literal("[Temporal Rift] The past dimension is unreachable. Contact a server admin."));
            return;
        }

        RiftEffectHelper.playTransitEarthquake(overworldLevel, riftPos);
        TemporalRiftTeleporter.teleportToPastDimension(player, pastLevel);
    }

    private void handleReturnAttempt(ServerPlayer player, ServerLevel beforeLevel) {
        long gameTime = beforeLevel.getGameTime();
        long lastAttempt = player.getPersistentData().getLong(NBT_LAST_RETURN_ATTEMPT_TICK);
        if (lastAttempt > 0L && gameTime - lastAttempt < RETURN_ATTEMPT_COOLDOWN_TICKS) {
            return;
        }

        player.getPersistentData().putLong(NBT_LAST_RETURN_ATTEMPT_TICK, gameTime);
        if (beforeLevel.getRandom().nextBoolean()) {
            player.sendSystemMessage(Component.literal("[Temporal Rift] The sky-tear rejects you."));
            player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
            return;
        }

        ServerLevel overworld = beforeLevel.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            player.sendSystemMessage(Component.literal("[Temporal Rift] The present cannot be reached from here."));
            return;
        }

        TemporalRiftManager.returnPlayerThroughTransientRift(player, overworld);
    }
}
