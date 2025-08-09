package com.thunder.wildernessodysseyapi.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

/**
 * Simple staff item that calls down a beam from the sky and ignites the target.
 */
public class SkyTorchStaffItem extends Item {
    public SkyTorchStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            HitResult result = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockResult = (BlockHitResult) result;
                BlockPos hitPos = blockResult.getBlockPos();
                BlockPos topPos = new BlockPos(hitPos.getX(), level.getMaxBuildHeight(), hitPos.getZ());

                // carve a vertical shaft from the sky down to the impact point
                for (int y = topPos.getY(); y >= hitPos.getY(); y--) {
                    BlockPos current = new BlockPos(hitPos.getX(), y, hitPos.getZ());
                    if (!level.getBlockState(current).isAir()) {
                        level.destroyBlock(current, false);
                    }
                }

                // call lightning from the build height
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(hitPos.getX() + 0.5, level.getMaxBuildHeight(), hitPos.getZ() + 0.5);
                    level.addFreshEntity(bolt);
                }

                // explosion at the impact and ignite nearby blocks
                level.explode(null, hitPos.getX() + 0.5, hitPos.getY(), hitPos.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.TNT);
                for (BlockPos firePos : BlockPos.betweenClosed(hitPos.offset(-1, 0, -1), hitPos.offset(1, 0, 1))) {
                    if (level.isEmptyBlock(firePos)) {
                        level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 11);
                    }
                }

                level.gameEvent(player, GameEvent.ITEM_INTERACT_FINISH, hitPos);
            }
        }

        player.getCooldowns().addCooldown(this, 100);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
