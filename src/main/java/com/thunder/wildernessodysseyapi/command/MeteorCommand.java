package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.meteor.MeteorEventManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Command for calling in meteor strikes near a target player.
 */
public final class MeteorCommand {

    private MeteorCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("meteorcall")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("command", IntegerArgumentType.integer(1, 10))
                        .executes(context -> triggerMeteor(context, 3.0F))
                        .then(Commands.argument("size", FloatArgumentType.floatArg(1.0F, 8.0F))
                                .executes(context -> triggerMeteor(context,
                                        FloatArgumentType.getFloat(context, "size"))))));
    }

    private static int triggerMeteor(CommandContext<CommandSourceStack> context, float requestedSize) {
        int commandCode = IntegerArgumentType.getInteger(context, "command");
        if (commandCode != 2) {
            context.getSource().sendFailure(Component.literal("Meteor command rejected: use command code 2 to call in a meteor."));
            return 0;
        }

        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by an in-world player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos playerPos = player.blockPosition();
        BlockPos impactPos = chooseImpactPosition(level, playerPos);

        MeteorEventManager.triggerMeteorStrike(level, impactPos, requestedSize);

        source.sendSuccess(() -> Component.literal(String.format(
                "Meteor strike incoming (code 2): impact at %d, %d, %d with size %.1f.",
                impactPos.getX(), impactPos.getY(), impactPos.getZ(), requestedSize)), true);

        return 1;
    }

    private static BlockPos chooseImpactPosition(ServerLevel level, BlockPos playerPos) {
        int minRadius = 10;
        int maxRadius = 28;
        int attempts = 12;

        for (int i = 0; i < attempts; i++) {
            double angle = level.getRandom().nextDouble() * (Math.PI * 2.0D);
            int radius = minRadius + level.getRandom().nextInt(maxRadius - minRadius + 1);
            int x = playerPos.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = playerPos.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, playerPos.getY(), z));
            if (surface.getY() > level.getMinBuildHeight() + 2) {
                return surface;
            }
        }

        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos);
    }
}
