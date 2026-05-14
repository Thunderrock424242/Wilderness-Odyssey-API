package com.thunder.wildernessodysseyapi.anomaly.block;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyBlocks;
import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.thunder.wildernessodysseyapi.temporalrift.SafeTeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Set;

public class AnomalyPortalBlock extends Block {
    private static final String NBT_RETURN_X = "anomaly_gateway_return_x";
    private static final String NBT_RETURN_Y = "anomaly_gateway_return_y";
    private static final String NBT_RETURN_Z = "anomaly_gateway_return_z";
    private static final String NBT_LAST_TRANSFER_TICK = "anomaly_gateway_last_transfer_tick";
    private static final long TRANSFER_COOLDOWN_TICKS = 80L;

    public AnomalyPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            transfer(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            transfer(serverPlayer, serverLevel, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            transfer(serverPlayer, serverLevel, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }

        double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.85D;
        double y = pos.getY() + 0.15D + random.nextDouble() * 0.9D;
        double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.85D;
        level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, 0.0D, 0.04D + random.nextDouble() * 0.05D, 0.0D);
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0D, 0.02D, 0.0D);
        }
    }

    private static void transfer(ServerPlayer player, ServerLevel currentLevel, BlockPos portalPos) {
        if (isCoolingDown(player, currentLevel)) {
            return;
        }

        if (currentLevel.dimension().equals(AnomalyDimensions.ANOMALY_DIMENSION_KEY)) {
            returnToOverworld(player, currentLevel);
        } else if (currentLevel.dimension().equals(Level.OVERWORLD)) {
            enterAnomalyDimension(player, currentLevel, portalPos);
        } else {
            player.sendSystemMessage(Component.literal("[Anomaly Gateway] The frame hums, but this dimension cannot tune the signal."));
        }
    }

    private static boolean isCoolingDown(ServerPlayer player, ServerLevel level) {
        long gameTime = level.getGameTime();
        CompoundTag data = player.getPersistentData();
        long lastTransfer = data.getLong(NBT_LAST_TRANSFER_TICK);
        if (lastTransfer > 0L && gameTime - lastTransfer < TRANSFER_COOLDOWN_TICKS) {
            return true;
        }

        data.putLong(NBT_LAST_TRANSFER_TICK, gameTime);
        return false;
    }

    private static void enterAnomalyDimension(ServerPlayer player, ServerLevel overworld, BlockPos portalPos) {
        ServerLevel anomalyLevel = overworld.getServer().getLevel(AnomalyDimensions.ANOMALY_DIMENSION_KEY);
        if (anomalyLevel == null) {
            player.sendSystemMessage(Component.literal("[Anomaly Gateway] The anomaly dimension is unreachable. Contact a server admin."));
            return;
        }

        CompoundTag data = player.getPersistentData();
        data.putInt(NBT_RETURN_X, portalPos.getX());
        data.putInt(NBT_RETURN_Y, portalPos.getY());
        data.putInt(NBT_RETURN_Z, portalPos.getZ());

        BlockPos arrival = findArrival(anomalyLevel, portalPos.getX(), portalPos.getZ());
        ensureGateway(anomalyLevel, arrival);
        playGatewayEffects(overworld, portalPos);
        teleport(player, anomalyLevel, arrival);
        playGatewayEffects(anomalyLevel, arrival);
        player.sendSystemMessage(Component.literal("[Anomaly Gateway] The meteor signal locks on. You cross into the Anomaly Dimension."));
    }

    private static void returnToOverworld(ServerPlayer player, ServerLevel anomalyLevel) {
        ServerLevel overworld = anomalyLevel.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            player.sendSystemMessage(Component.literal("[Anomaly Gateway] The Overworld signal is gone."));
            return;
        }

        CompoundTag data = player.getPersistentData();
        int x = data.contains(NBT_RETURN_X) ? data.getInt(NBT_RETURN_X) : (int) Math.floor(player.getX());
        int y = data.contains(NBT_RETURN_Y) ? data.getInt(NBT_RETURN_Y) : (int) Math.floor(player.getY());
        int z = data.contains(NBT_RETURN_Z) ? data.getInt(NBT_RETURN_Z) : (int) Math.floor(player.getZ());
        data.remove(NBT_RETURN_X);
        data.remove(NBT_RETURN_Y);
        data.remove(NBT_RETURN_Z);

        BlockPos arrival = SafeTeleportHelper.findSafePositionNearby(overworld, x, y, z, 12);
        if (arrival == null) {
            arrival = findArrival(overworld, x, z);
        }

        ensureGateway(overworld, arrival);
        playGatewayEffects(anomalyLevel, player.blockPosition());
        teleport(player, overworld, arrival);
        playGatewayEffects(overworld, arrival);
        player.sendSystemMessage(Component.literal("[Anomaly Gateway] You tumble back through the meteor-lit frame."));
    }

    private static BlockPos findArrival(ServerLevel level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int clampedY = Math.max(level.getMinBuildHeight() + 2, Math.min(surfaceY + 1, level.getMaxBuildHeight() - 2));
        BlockPos safePos = SafeTeleportHelper.findSafePositionNearby(level, x, clampedY, z, 16);
        return safePos != null ? safePos : new BlockPos(x, clampedY, z);
    }

    private static void ensureGateway(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(AnomalyBlocks.ANOMALY_GATEWAY.get())) {
            return;
        }
        if (state.isAir() || !state.isSolid()) {
            level.setBlock(pos, AnomalyBlocks.ANOMALY_GATEWAY.get().defaultBlockState(), 3);
        }
    }

    private static void teleport(ServerPlayer player, ServerLevel targetLevel, BlockPos pos) {
        player.teleportTo(
                targetLevel,
                pos.getX() + 0.5D,
                pos.getY() + 0.05D,
                pos.getZ() + 0.5D,
                Set.<RelativeMovement>of(),
                player.getYRot(),
                player.getXRot()
        );
        player.resetFallDistance();
    }

    private static void playGatewayEffects(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.55F, 1.45F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                pos.getX() + 0.5D,
                pos.getY() + 0.6D,
                pos.getZ() + 0.5D,
                48,
                0.55D,
                0.45D,
                0.55D,
                0.08D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.getX() + 0.5D,
                pos.getY() + 0.75D,
                pos.getZ() + 0.5D,
                12,
                0.35D,
                0.35D,
                0.35D,
                0.04D);
    }
}
