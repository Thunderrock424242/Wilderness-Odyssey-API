package com.thunder.wildernessodysseyapi.temporalrift.block;

import com.mojang.serialization.MapCodec;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalTransferManager;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.TimeCapsuleBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

public class TimeCapsuleBlock extends BaseEntityBlock {
    public static final MapCodec<TimeCapsuleBlock> CODEC = simpleCodec(TimeCapsuleBlock::new);

    public TimeCapsuleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimeCapsuleBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return sealCapsule(level, pos, player, ItemStack.EMPTY, null);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = sealCapsule(level, pos, player, stack, hand);
        return switch (result) {
            case SUCCESS, CONSUME -> ItemInteractionResult.sidedSuccess(level.isClientSide);
            case FAIL -> ItemInteractionResult.FAIL;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    private InteractionResult sealCapsule(Level level, BlockPos pos, Player player, ItemStack payload, @Nullable InteractionHand hand) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (!serverLevel.dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY)) {
            serverPlayer.sendSystemMessage(Component.literal("Time Capsules can only be sealed while in the past dimension."));
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockEntity(pos) instanceof TimeCapsuleBlockEntity capsule)) {
            return InteractionResult.PASS;
        }

        if (capsule.isSealed()) {
            serverPlayer.sendSystemMessage(Component.literal("This capsule is already sealed and awaiting transfer to the present."));
        } else {
            ItemStack storedPayload = payload.isEmpty() ? ItemStack.EMPTY : payload.copy();
            capsule.seal(serverPlayer, storedPayload);
            if (!payload.isEmpty() && hand != null && !serverPlayer.getAbilities().instabuild) {
                serverPlayer.getItemInHand(hand).shrink(storedPayload.getCount());
            }
            TemporalTransferManager.scheduleCapsule(serverLevel, pos);
            serverPlayer.sendSystemMessage(Component.literal("Capsule sealed. It will echo into the Overworld... in time."));
        }

        return InteractionResult.SUCCESS;
    }
}
