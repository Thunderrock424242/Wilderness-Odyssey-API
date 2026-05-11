package com.thunder.wildernessodysseyapi.temporalrift.command;

import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.world.level.Level;

public final class TemporalRiftCommand {
    private TemporalRiftCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wildernessrift")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("open").executes(TemporalRiftCommand::cmdOpen))
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
