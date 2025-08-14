package com.thunder.wildernessodysseyapi.skytorch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.thunder.wildernessodysseyapi.skytorch.SkyTorchLaser;


/**
 * Simple staff item that calls down a beam from the sky and ignites the target.
 */
public class SkyTorchStaffItem extends Item {
    private static final int BORE_RADIUS = 3;
    private static final int BURN_RADIUS = 5;

    public SkyTorchStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(256));
            HitResult result = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockResult = (BlockHitResult) result;
                BlockPos hitPos = blockResult.getBlockPos();
                Vec3 originVec = new Vec3(hitPos.getX() + 0.5, level.getMaxBuildHeight() - 1, hitPos.getZ() + 0.5);
                Vec3 hitVec = Vec3.atCenterOf(hitPos);

                SkyTorchLaser.Options options = new SkyTorchLaser.Options();
                options.boreDistance = originVec.distanceTo(hitVec);

                new SkyTorchLaser(level, originVec, hitVec, options).fire();

                if (level instanceof ServerLevel serverLevel) {
                    for (int y = level.getMaxBuildHeight() - 1; y >= hitPos.getY(); y--) {
                        serverLevel.sendParticles(ParticleTypes.END_ROD, hitPos.getX() + 0.5, y + 0.5, hitPos.getZ() + 0.5, 1, 0, 0, 0, 0);
                    }

                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                    if (bolt != null) {
                        bolt.moveTo(Vec3.atBottomCenterOf(hitPos));
                        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            bolt.setCause(serverPlayer);
                        }
                        serverLevel.addFreshEntity(bolt);
                    }
                }

                if (result.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockResult = (BlockHitResult) result;
                    BlockPos hitPos = blockResult.getBlockPos();
                    BlockPos topPos = new BlockPos(hitPos.getX(), level.getMaxBuildHeight() - 1, hitPos.getZ());

                    // carve a vertical shaft with radius
                    for (int y = topPos.getY(); y >= hitPos.getY(); y--) {
                        for (int dx = -BORE_RADIUS; dx <= BORE_RADIUS; dx++) {
                            for (int dz = -BORE_RADIUS; dz <= BORE_RADIUS; dz++) {
                                if (dx * dx + dz * dz <= BORE_RADIUS * BORE_RADIUS) {
                                    BlockPos current = new BlockPos(hitPos.getX() + dx, y, hitPos.getZ() + dz);
                                    if (!level.getBlockState(current).isAir()) {
                                        level.destroyBlock(current, false);
                                    }
                                }
                            }
                        }
                    }

                    // render a light shaft from build height to the target
                    if (level instanceof ServerLevel serverLevel) {
                        for (int y = topPos.getY(); y >= hitPos.getY(); y--) {
                            serverLevel.sendParticles(
                                    ParticleTypes.END_ROD,
                                    hitPos.getX() + 0.5,
                                    y + 0.5,
                                    hitPos.getZ() + 0.5,
                                    1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }

                    // explosion at the impact and ignite surrounding ring
                    level.explode(null, hitPos.getX() + 0.5, hitPos.getY(), hitPos.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.TNT);
                    for (int dx = -BURN_RADIUS; dx <= BURN_RADIUS; dx++) {
                        for (int dz = -BURN_RADIUS; dz <= BURN_RADIUS; dz++) {
                            if (dx * dx + dz * dz <= BURN_RADIUS * BURN_RADIUS) {
                                BlockPos firePos = hitPos.offset(dx, 0, dz);
                                if (level.isEmptyBlock(firePos) && !level.isEmptyBlock(firePos.below())) {
                                    level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 11);
                                }
                            }
                        }
                    }

                    level.gameEvent(player, GameEvent.ITEM_INTERACT_FINISH, hitPos);
                }
            }

            player.getCooldowns().addCooldown(this, 100);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
    }
}
