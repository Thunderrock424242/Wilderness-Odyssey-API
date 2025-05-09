package com.thunder.wildernessodysseyapi.MemUtils;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;

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
                                    WildernessOdysseyAPIMainModClass.dynamicModCount
                            );

                            source.sendSuccess(
                                    () -> ChatFormatting.GREEN + "Current memory usage: "
                                            + usedMB + "MB / " + totalMB + "MB",
                                    false
                            );
                            source.sendSuccess(
                                    () -> ChatFormatting.YELLOW + "Recommended allocation: ~"
                                            + recommended + "MB",
                                    false
                            );

                            return 1;
                        })
        );
    }
}
