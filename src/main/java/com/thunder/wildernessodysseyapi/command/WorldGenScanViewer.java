package com.thunder.wildernessodysseyapi.command;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.Map;

/**
 * Simple Swing window displaying worldgen scan results in tabs.
 */
public class WorldGenScanViewer {

    public static void show(Map<ResourceLocation, Integer> structures,
                             Map<ResourceLocation, Integer> features,
                             Map<ResourceLocation, Integer> biomes) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("WorldGen Scan Results");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(450, 600);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Structures", createPanel(structures));
            tabs.addTab("Features", createPanel(features));
            tabs.addTab("Biomes", createPanel(biomes));

            frame.add(tabs, BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static JScrollPane createPanel(Map<ResourceLocation, Integer> map) {
        DefaultListModel<String> model = new DefaultListModel<>();
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> {
                    String modName = ModList.get().getModContainerById(entry.getKey().getNamespace())
                            .map(c -> c.getModInfo().getDisplayName())
                            .orElse("Unknown Mod");
                    model.addElement(entry.getKey().getPath() + " [" + modName + "]: " + entry.getValue());
                });
        JList<String> list = new JList<>(model);
        return new JScrollPane(list);
    }
}
