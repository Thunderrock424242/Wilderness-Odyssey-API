package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.thunder.ticktoklib.TickTokHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Gives a timer stick that can lock doors.
 */
public class DoorLockCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doorlock")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                        .executes(ctx -> giveStick(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))));
    }

    private static int giveStick(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        Player player = ctx.getSource().getPlayerOrException();
        int duration = TickTokHelper.duration(0, 0, seconds, 0);
        ItemStack stick = new ItemStack(Items.STICK);
        CustomData custom = stick.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = custom.copyTag();
        tag.putInt("door_lock_duration", duration);
        stick.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stick.set(DataComponents.CUSTOM_NAME, Component.literal("Door Timer (" + seconds + "s)"));
        player.addItem(stick);
        return 1;
    }
}
