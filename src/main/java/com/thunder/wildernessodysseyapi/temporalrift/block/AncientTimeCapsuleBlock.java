package com.thunder.wildernessodysseyapi.temporalrift.block;

import com.mojang.serialization.MapCodec;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.AncientTimeCapsuleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AncientTimeCapsuleBlock extends BaseEntityBlock {
    public static final MapCodec<AncientTimeCapsuleBlock> CODEC = simpleCodec(AncientTimeCapsuleBlock::new);

    public AncientTimeCapsuleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AncientTimeCapsuleBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        openCapsule(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        openCapsule(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openCapsule(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof AncientTimeCapsuleBlockEntity capsule) {
            capsule.openForPlayer(serverPlayer);
        }
    }
}
