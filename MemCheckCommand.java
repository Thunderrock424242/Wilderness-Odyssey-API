package com.thunder.wildernessodysseyapi.MemUtils;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class MemCheckCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("memcheck")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();

                            long usedMB  = MemoryUtils.getUsedMemoryMB();
                            long totalMB = MemoryUtils.getTotalMemoryMB();
                            int recommended = MemoryUtils.calculateRecommendedRAM(
                                    usedMB,
                                    ResourceManagerMod.MOD_COUNT
                            );

                            source.sendSuccess(
                                    () -> Component.literal("Current memory usage: ")
                                            .withStyle(Style.EMPTY.withColor(0x55FF55))
                                            .append(Component.literal(usedMB + "MB / " + totalMB + "MB")),
                                    false
                            );
                            source.sendSuccess(
                                    () -> Component.literal("Recommended allocation: ~" + recommended + "MB")
                                            .withStyle(Style.EMPTY.withColor(0xFFFF55)),  // Yellow
                                    false
                            );

                            return 1;
                        })
        );
    }
}
