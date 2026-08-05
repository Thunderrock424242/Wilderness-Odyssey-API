package com.thunder.wildernessodysseyapi.anomaly.block;

import com.thunder.wildernessodysseyapi.anomaly.AnomalyDimensionRules;
import com.thunder.wildernessodysseyapi.anomaly.AnomalyGatewayTravelData;
import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyBlocks;
import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.thunder.wildernessodysseyapi.temporalrift.SafeTeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * A two-way gateway between the Anomaly and its linked temporal layers.
 *
 * <p>Gateways in the Overworld and The Before preserve their source dimension
 * and coordinates on the player. The matching Anomaly gateway is placed beside
 * a safe landing position so standing still cannot immediately retrigger it.</p>
 */
public class AnomalyPortalBlock extends Block {
    private static final String NBT_LAST_TRANSFER_TICK = "anomaly_gateway_last_transfer_tick";
    private static final long TRANSFER_COOLDOWN_TICKS = 80L;
    private static final int GATEWAY_SEARCH_RADIUS = 4;

    /** Creates a gateway block with the registry-owned block properties. */
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

        if (AnomalyDimensionRules.isAnomaly(currentLevel.dimension())) {
            returnToOrigin(player, currentLevel);
        } else if (AnomalyDimensionRules.isGatewaySource(currentLevel.dimension())) {
            enterAnomalyDimension(player, currentLevel, portalPos);
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.wildernessodysseyapi.anomaly_gateway.unsupported_source"
            ));
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

    private static void enterAnomalyDimension(ServerPlayer player, ServerLevel sourceLevel, BlockPos portalPos) {
        ServerLevel anomalyLevel = sourceLevel.getServer().getLevel(AnomalyDimensions.ANOMALY_DIMENSION_KEY);
        if (anomalyLevel == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.wildernessodysseyapi.anomaly_gateway.anomaly_unreachable"
            ));
            return;
        }

        CompoundTag data = player.getPersistentData();
        AnomalyGatewayTravelData.store(data, sourceLevel.dimension(), portalPos);

        GatewayArrival arrival = prepareArrival(anomalyLevel, portalPos.getX(), portalPos.getZ());
        playGatewayEffects(sourceLevel, portalPos);
        teleport(player, anomalyLevel, arrival.playerPos());
        playGatewayEffects(anomalyLevel, arrival.gatewayPos());
        player.sendSystemMessage(Component.translatable(
                "message.wildernessodysseyapi.anomaly_gateway.entered"
        ));
    }

    private static void returnToOrigin(ServerPlayer player, ServerLevel anomalyLevel) {
        CompoundTag data = player.getPersistentData();
        AnomalyGatewayTravelData.ReturnTarget target = AnomalyGatewayTravelData.read(
                data,
                Level.OVERWORLD,
                player.blockPosition()
        );
        ServerLevel targetLevel = anomalyLevel.getServer().getLevel(target.dimension());
        if (targetLevel == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.wildernessodysseyapi.anomaly_gateway.origin_unreachable"
            ));
            return;
        }

        BlockPos sourceGateway = target.gatewayPos();
        ensureGateway(targetLevel, sourceGateway);
        BlockPos arrival = findSafePositionBesideGateway(targetLevel, sourceGateway, 12);
        if (arrival == null) {
            arrival = findArrival(targetLevel, sourceGateway.getX(), sourceGateway.getZ());
        }

        playGatewayEffects(anomalyLevel, player.blockPosition());
        teleport(player, targetLevel, arrival);
        playGatewayEffects(targetLevel, sourceGateway);
        AnomalyGatewayTravelData.clear(data);
        player.sendSystemMessage(Component.translatable(
                "message.wildernessodysseyapi.anomaly_gateway.returned"
        ));
    }

    private static GatewayArrival prepareArrival(ServerLevel level, int x, int z) {
        BlockPos playerPos = findArrival(level, x, z);
        BlockPos gatewayPos = findGatewayPosition(level, playerPos);
        ensureGateway(level, gatewayPos);
        return new GatewayArrival(playerPos, gatewayPos);
    }

    private static BlockPos findArrival(ServerLevel level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int clampedY = Math.max(level.getMinBuildHeight() + 2, Math.min(surfaceY + 1, level.getMaxBuildHeight() - 2));
        BlockPos safePos = SafeTeleportHelper.findSafePositionNearby(level, x, clampedY, z, 16);
        return safePos != null ? safePos : new BlockPos(x, clampedY, z);
    }

    private static BlockPos findGatewayPosition(ServerLevel level, BlockPos playerPos) {
        for (int radius = 1; radius <= GATEWAY_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos candidate = SafeTeleportHelper.findSafePosition(
                            level,
                            playerPos.getX() + dx,
                            playerPos.getY(),
                            playerPos.getZ() + dz
                    );
                    if (candidate != null && canReplaceWithGateway(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        // A floating fallback keeps the player's two-block landing column clear.
        return playerPos.above(2);
    }

    private static BlockPos findSafePositionBesideGateway(ServerLevel level, BlockPos gatewayPos, int radius) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = gatewayPos.relative(direction);
            BlockPos safePos = SafeTeleportHelper.findSafePosition(
                    level,
                    candidate.getX(),
                    candidate.getY(),
                    candidate.getZ()
            );
            if (safePos != null && !safePos.equals(gatewayPos)) {
                return safePos;
            }
        }

        for (int searchRadius = 2; searchRadius <= radius; searchRadius++) {
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    if (Math.abs(dx) != searchRadius && Math.abs(dz) != searchRadius) {
                        continue;
                    }
                    BlockPos safePos = SafeTeleportHelper.findSafePosition(
                            level,
                            gatewayPos.getX() + dx,
                            gatewayPos.getY(),
                            gatewayPos.getZ() + dz
                    );
                    if (safePos != null) {
                        return safePos;
                    }
                }
            }
        }
        return null;
    }

    private static void ensureGateway(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(AnomalyBlocks.ANOMALY_GATEWAY.get())) {
            return;
        }
        if (canReplaceWithGateway(level, pos)) {
            level.setBlock(pos, AnomalyBlocks.ANOMALY_GATEWAY.get().defaultBlockState(), 3);
        }
    }

    private static boolean canReplaceWithGateway(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (!state.isSolid() && !state.liquid());
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

    private record GatewayArrival(BlockPos playerPos, BlockPos gatewayPos) {
    }
}
