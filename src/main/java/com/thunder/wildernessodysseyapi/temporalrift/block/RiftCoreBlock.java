package com.thunder.wildernessodysseyapi.temporalrift.block;

import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftTeleporter;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RiftCoreBlock extends Block {
    public RiftCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            handleEntry(serverPlayer, serverLevel);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            handleEntry(serverPlayer, serverLevel);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer && level.dimension().equals(Level.OVERWORLD) && level instanceof ServerLevel serverLevel) {
            handleEntry(serverPlayer, serverLevel);
        }
    }

    private void handleEntry(ServerPlayer player, ServerLevel overworldLevel) {
        ServerLevel pastLevel = overworldLevel.getServer().getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
        if (pastLevel == null) {
            player.sendSystemMessage(Component.literal("[Temporal Rift] The past dimension is unreachable. Contact a server admin."));
            return;
        }

        TemporalRiftTeleporter.teleportToPastDimension(player, pastLevel);
    }
}
