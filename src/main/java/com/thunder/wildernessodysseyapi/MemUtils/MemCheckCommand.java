package com.thunder.wildernessodysseyapi.MemUtils;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.Core.WildernessOdysseyAPIMainModClass;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Command that reports memory usage statistics.
 */
public class MemCheckCommand {

    /**
     * Registers the {@code /memcheck} command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("memcheck")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            LOGGER.info("/memcheck command executed");

                            long usedMB  = MemoryUtils.getUsedMemoryMB();
                            long peakMB  = MemoryUtils.getPeakUsedMemoryMB();
                            long totalMB = MemoryUtils.getTotalMemoryMB();
                            int recommended = MemoryUtils.calculateRecommendedRAM(
                                    peakMB,
                                    WildernessOdysseyAPIMainModClass.dynamicModCount
                            );

                            source.sendSuccess(
                                    () -> Component.nullToEmpty(ChatFormatting.GREEN + "Current memory usage: "
                                            + usedMB + "MB / " + totalMB + "MB"),
                                    false
                            );
                            source.sendSuccess(
                                    () -> Component.nullToEmpty(ChatFormatting.AQUA + "Peak usage observed: "
                                            + peakMB + "MB"),
                                    false
                            );
                            source.sendSuccess(
                                    () -> Component.nullToEmpty(ChatFormatting.YELLOW + "Recommended allocation: ~"
                                            + recommended + "MB"),
                                    false
                            );

                            return 1;
                        })
        );
    }
}
