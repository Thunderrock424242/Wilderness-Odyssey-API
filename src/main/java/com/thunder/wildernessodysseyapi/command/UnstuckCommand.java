package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Teleports a player to a nearby safe location when they are stuck.
 */
public final class UnstuckCommand {
    private static final int MIN_DISTANCE = 10;
    private static final int MAX_DISTANCE = 30;
    private static final int ATTEMPTS = 64;

    private UnstuckCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("unstuck")
                .executes(ctx -> unstuck(ctx.getSource())));
    }

    private static int unstuck(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use /unstuck."));
            return 0;
        }

        if (!(source.getLevel() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("/unstuck must be used in a server world."));
            return 0;
        }

        BlockPos safePos = findSafeDestination(level, player.blockPosition(), level.random);
        if (safePos == null) {
            source.sendFailure(Component.literal("Couldn't find a safe location nearby. Try again in a moment."));
            return 0;
        }

        Vec3 destination = Vec3.atBottomCenterOf(safePos);
        player.teleportTo(level, destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(String.format(
                "Teleported to a safe spot at %d %d %d.",
                safePos.getX(), safePos.getY(), safePos.getZ()
        )), true);
        return Command.SINGLE_SUCCESS;
    }

    private static BlockPos findSafeDestination(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = random.nextDouble() * (Math.PI * 2.0D);
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
            int targetX = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int targetZ = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);

            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(targetX, 0, targetZ));
            BlockPos feet = top;
            if (!level.getBlockState(feet).isAir()) {
                feet = feet.above();
            }

            if (!level.getWorldBorder().isWithinBounds(feet)) {
                continue;
            }

            if (isSafeForStanding(level, feet)) {
                return feet;
            }
        }
        return null;
    }

    private static boolean isSafeForStanding(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos ground = feet.below();

        if (level.isOutsideBuildHeight(feet) || level.isOutsideBuildHeight(head) || level.isOutsideBuildHeight(ground)) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState groundState = level.getBlockState(ground);

        if (!feetState.isAir() || !headState.isAir()) {
            return false;
        }

        if (!groundState.isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)) {
            return false;
        }

        if (groundState.is(Blocks.LAVA) || groundState.is(Blocks.MAGMA_BLOCK)
                || groundState.is(Blocks.CACTUS) || groundState.is(Blocks.CAMPFIRE)
                || groundState.is(Blocks.SOUL_CAMPFIRE) || groundState.is(Blocks.FIRE)
                || groundState.is(Blocks.SOUL_FIRE)) {
            return false;
        }

        return level.getFluidState(feet).isEmpty() && level.getFluidState(head).isEmpty();
    }
}
