package com.thunder.wildernessodysseyapi.ModListTracker.commands;

import com.thunder.wildernessodysseyapi.ModListTracker.ModTracker;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModListTracker.gui.ModDiffViewer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.List;

public class ModListDiffCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("modlistdiff")
                .executes(context -> {
                    showModDifferences(context.getSource());
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static void showModDifferences(CommandSourceStack source) {
        List<String> addedMods = ModTracker.getAddedMods();
        List<String> removedMods = ModTracker.getRemovedMods();
        List<String> updatedMods = ModTracker.getUpdatedMods();

        if (addedMods.isEmpty() && removedMods.isEmpty() && updatedMods.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No mod changes detected."), false);
        } else {
            if (!addedMods.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§a[Added Mods]: " + String.join(", ", addedMods)), false);
            }
            if (!removedMods.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§c[Removed Mods]: " + String.join(", ", removedMods)), false);
            }
            if (!updatedMods.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§e[Updated Mods]: " + String.join(", ", updatedMods)), false);
            }
        }

        // 🔹 Open the GUI on a separate thread to avoid freezing the game
        new Thread(() -> {
            // Ensure this method exists in ModDiffViewer
            javax.swing.SwingUtilities.invokeLater(ModDiffViewer::createAndShowGUI);
        }).start();
    }
}
