package com.thunder.wildernessodysseyapi.temporalrift.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftManager;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftSavedData;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftFieldMath;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftSites;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftState;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftTrigger;
import com.thunder.wildernessodysseyapi.worldgen.structure.rift.RiftRockPlacement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Operator-only development controls under the established rift command. Coordinates always identify the anchor. */
public final class HybridRiftCommands {
    private HybridRiftCommands() { }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root = Commands.literal("hybrid");
        root.then(Commands.literal("spawn").then(Commands.argument("anchor", BlockPosArgument.blockPos()).executes(context ->
                spawn(context.getSource(), BlockPosArgument.getBlockPos(context, "anchor"), true))));
        root.then(Commands.literal("ring").then(Commands.argument("anchor", BlockPosArgument.blockPos()).executes(context ->
                spawn(context.getSource(), BlockPosArgument.getBlockPos(context, "anchor"), false))));
        root.then(Commands.literal("composition").then(Commands.argument("anchor", BlockPosArgument.blockPos()).executes(context -> {
            var core = core(context.getSource(), BlockPosArgument.getBlockPos(context, "anchor"));
            if (core == null) return 0;
            var result = RiftRockPlacement.place(context.getSource().getLevel(), core);
            if (!result.placed()) context.getSource().sendFailure(Component.literal(result.reason()));
            else context.getSource().sendSuccess(() -> Component.literal(result.reason()), true);
            return result.placed() ? 1 : 0;
        })));
        var state = Commands.literal("state");
        for (RiftState value : RiftState.values()) {
            state.then(Commands.literal(value.name().toLowerCase(Locale.ROOT)).then(Commands.argument("anchor", BlockPosArgument.blockPos()).executes(context -> {
                var core = core(context.getSource(), BlockPosArgument.getBlockPos(context, "anchor"));
                if (core == null) return 0;
                core.setRiftState(value);
                context.getSource().sendSuccess(() -> Component.literal("Rift state: " + value), true);
                return 1;
            })));
        }
        root.then(state);
        root.then(Commands.literal("inspect").then(Commands.argument("anchor", BlockPosArgument.blockPos()).executes(context -> {
            var core = core(context.getSource(), BlockPosArgument.getBlockPos(context, "anchor"));
            if (core == null) return 0;
            double distance = context.getSource().getPosition().distanceTo(RiftTrigger.center(core.getBlockPos()));
            String message = String.format(Locale.ROOT, "State %s, facing %s, distance %.2f blocks, field intensity %.3f, radius %.1f blocks, rocks placed %s. Trigger %s. Client bounds: /riftfx debug true",
                    core.getRiftState(), core.getFacing(), distance, RiftFieldMath.intensity(distance, RiftConfig.FIELD_RADIUS.get()),
                    RiftConfig.FIELD_RADIUS.get(), core.isCompositionPlaced(), RiftTrigger.bounds(core.getBlockPos(), core.getFacing()));
            context.getSource().sendSuccess(() -> Component.literal(message), false);
            return 1;
        })));
        root.then(Commands.literal("remove").then(Commands.argument("anchor", BlockPosArgument.blockPos()).executes(context -> {
            var core = core(context.getSource(), BlockPosArgument.getBlockPos(context, "anchor"));
            if (core == null) return 0;
            var source = context.getSource();
            var managed = TemporalRiftSavedData.get(source.getServer());
            if (core.getManagedCloseTick() > 0 && source.getLevel() == source.getServer().overworld()
                    && core.getBlockPos().equals(managed.getRiftPosition())) TemporalRiftManager.forceCloseRift(source.getServer());
            else source.getLevel().removeBlock(core.getBlockPos(), false);
            context.getSource().sendSuccess(() -> Component.literal("Ring removed. Placed terrain is preserved."), true);
            return 1;
        })));
        return root;
    }

    private static int spawn(CommandSourceStack source, BlockPos anchor, boolean rocks) throws CommandSyntaxException {
        Direction facing = source.getEntity() == null ? Direction.NORTH : source.getEntity().getDirection();
        var result = RiftSites.create(source.getLevel(), anchor, facing, rocks);
        if (result.core() == null) { source.sendFailure(Component.literal(result.message())); return 0; }
        source.sendSuccess(() -> Component.literal("Hybrid ring at " + anchor.toShortString() + ". " + result.message()), true);
        return 1;
    }

    private static RiftCoreBlockEntity core(CommandSourceStack source, BlockPos pos) {
        if (source.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            source.sendFailure(Component.literal("The anchor chunk is not loaded.")); return null;
        }
        if (source.getLevel().getBlockEntity(pos) instanceof RiftCoreBlockEntity core && core.isHybrid()) return core;
        source.sendFailure(Component.literal("No hybrid rift core at " + pos.toShortString() + "."));
        return null;
    }
}
