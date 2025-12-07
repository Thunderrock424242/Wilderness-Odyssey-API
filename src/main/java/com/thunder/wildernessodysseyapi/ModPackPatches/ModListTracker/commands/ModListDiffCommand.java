package com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.commands;

import com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.ModTracker;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.gui.ModDiffViewer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.awt.GraphicsEnvironment;
import java.util.List;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/**
 * Command to display mod list differences since last launch.
 */
public class ModListDiffCommand {
    /**
     * Registers the {@code /modlistdiff} command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("modlistdiff")
                .executes(context -> {
                    showModDifferences(context.getSource());
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /**
     * Sends the mod difference summary to chat and opens a GUI view.
     */
    private static void showModDifferences(CommandSourceStack source) {
        ModTracker.checkModChanges();
        List<String> addedMods = ModTracker.getAddedMods();
        List<String> removedMods = ModTracker.getRemovedMods();
        List<String> updatedMods = ModTracker.getUpdatedMods();
        String versionChange = ModTracker.getVersionChange();

        if (addedMods.isEmpty() && removedMods.isEmpty() && updatedMods.isEmpty() && versionChange.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No mod changes detected."), false);
        } else {
            if (!addedMods.isEmpty()) {
                source.sendSuccess(() -> Component.literal("\u00A7a[Added Mods]: " + String.join(", ", addedMods)), false);
            }
            if (!removedMods.isEmpty()) {
                source.sendSuccess(() -> Component.literal("\u00A7c[Removed Mods]: " + String.join(", ", removedMods)), false);
            }
            if (!updatedMods.isEmpty()) {
                source.sendSuccess(() -> Component.literal("\u00A7e[Updated Mods]: " + String.join(", ", updatedMods)), false);
            }
            if (!versionChange.isEmpty()) {
                source.sendSuccess(() -> Component.literal("\u00A7b" + versionChange), false);
            }
        }

        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.info("Headless environment detected; skipping mod diff viewer GUI."
                    + " Players can review logs/mod-changes.log for the full diff.");
            source.sendSuccess(() -> Component.literal(
                    "GUI not available on this server. Check logs/mod-changes.log for details."), false);
            return;
        }

        new Thread(() -> javax.swing.SwingUtilities.invokeLater(ModDiffViewer::createAndShowGUI)).start();
    }
}
