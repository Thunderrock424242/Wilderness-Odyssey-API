package com.thunder.wildernessodysseyapi.temporalrift.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftManager;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftSavedData;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftTeleporter;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalTransferManager;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalTransferSavedData;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class TemporalRiftCommand {
    private TemporalRiftCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wildernessrift")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("open").executes(TemporalRiftCommand::cmdOpen))
                        .then(Commands.literal("debug_open_near")
                                .executes(context -> cmdDebugOpenNear(context, 10))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(3, 64))
                                        .executes(context -> cmdDebugOpenNear(context, IntegerArgumentType.getInteger(context, "distance")))))
                        .then(Commands.literal("close").executes(TemporalRiftCommand::cmdClose))
                        .then(Commands.literal("status").executes(TemporalRiftCommand::cmdStatus))
                        .then(Commands.literal("teleport_past").executes(TemporalRiftCommand::cmdTeleportPast))
                        .then(Commands.literal("teleport_overworld").executes(TemporalRiftCommand::cmdTeleportOverworld))
                        .then(Commands.literal("force_transfer_capsules").executes(TemporalRiftCommand::cmdForceTransfer))
        );
    }

    private static int cmdOpen(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        TemporalRiftManager.forceOpenRift(source.getServer(), pos);
        source.sendSuccess(() -> Component.literal("Temporal rift force-opened at " + pos + "."), true);
        return 1;
    }

    private static int cmdDebugOpenNear(CommandContext<CommandSourceStack> context, int distance) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (!player.level().dimension().equals(Level.OVERWORLD)) {
                source.sendFailure(Component.literal("Debug rifts must be opened from the Overworld."));
                return 0;
            }

            ServerLevel overworld = player.serverLevel();
            Vec3 look = player.getLookAngle();
            Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
            if (horizontalLook.lengthSqr() < 1.0E-6D) {
                float yaw = player.getYRot() * Mth.DEG_TO_RAD;
                horizontalLook = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
            } else {
                horizontalLook = horizontalLook.normalize();
            }

            Vec3 offset = horizontalLook.scale(distance);
            int x = Mth.floor(player.getX() + offset.x);
            int z = Mth.floor(player.getZ() + offset.z);
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            TemporalRiftManager.forceOpenRift(source.getServer(), pos);
            source.sendSuccess(() -> Component.literal("Debug rift opened " + distance + " blocks ahead at " + pos + "."), true);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Debug rift failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int cmdClose(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TemporalRiftManager.forceCloseRift(source.getServer());
        source.sendSuccess(() -> Component.literal("Temporal rift force-closed."), true);
        return 1;
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TemporalRiftSavedData riftData = TemporalRiftSavedData.get(source.getServer());
        TemporalTransferSavedData transferData = TemporalTransferSavedData.get(source.getServer());
        String message = "[Temporal Rift Status]\n"
                + "Rift Open: " + riftData.isRiftOpen() + "\n"
                + "Position: " + riftData.getRiftPosition() + "\n"
                + "Before Sky Rift: " + riftData.getBeforeSkyRiftPosition() + "\n"
                + "Before Ground Rift: " + riftData.getBeforeGroundRiftPosition() + "\n"
                + "Close At Tick: " + riftData.getRiftCloseGameTime() + "\n"
                + "Next Rift Day: " + riftData.getNextRiftDay() + "\n"
                + "Pending Capsules: " + transferData.getPendingTransfers().size();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int cmdTeleportPast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel pastLevel = source.getServer().getLevel(TemporalRiftDimensions.THE_BEFORE_KEY);
            if (pastLevel == null) {
                source.sendFailure(Component.literal("The Before dimension is not loaded."));
                return 0;
            }
            TemporalRiftTeleporter.teleportToPastDimension(player, pastLevel);
            source.sendSuccess(() -> Component.literal("Teleported to The Before."), true);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Teleport failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int cmdTeleportOverworld(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) {
                source.sendFailure(Component.literal("Overworld is not loaded."));
                return 0;
            }
            TemporalRiftTeleporter.teleportToOverworld(player, overworld);
            source.sendSuccess(() -> Component.literal("Returned to the Overworld."), true);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Teleport failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int cmdForceTransfer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TemporalTransferManager.forceDeliverAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Force-delivered all pending time capsule transfers."), true);
        return 1;
    }
}
