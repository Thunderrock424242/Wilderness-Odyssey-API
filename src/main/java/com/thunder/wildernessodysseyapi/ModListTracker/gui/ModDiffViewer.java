package com.thunder.wildernessodysseyapi.ModListTracker.gui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public class ModDiffViewer {
    private static final Path LOG_FILE = Paths.get("logs/mod-changes.log");

    public static void createAndShowGUI() {
        JFrame frame = new JFrame("Mod List Differences");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Keep game running after closing GUI
        frame.setSize(600, 400);

        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Arial", Font.PLAIN, 14));

        JScrollPane scrollPane = new JScrollPane(textPane);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Load log file and apply color-coding
        loadLog(textPane);

        frame.setVisible(true);
    }

    private static void loadLog(JTextPane textPane) {
        try {
            List<String> logLines = Files.exists(LOG_FILE)
                    ? Files.readAllLines(LOG_FILE)
                    : List.of("No mod changes log found.");

            StyledDocument doc = textPane.getStyledDocument();
            Style defaultStyle = textPane.addStyle("default", null);
            Style addedStyle = textPane.addStyle("added", defaultStyle);
            StyleConstants.setForeground(addedStyle, Color.GREEN);
            Style removedStyle = textPane.addStyle("removed", defaultStyle);
            StyleConstants.setForeground(removedStyle, Color.RED);
            Style updatedStyle = textPane.addStyle("updated", defaultStyle);
            StyleConstants.setForeground(updatedStyle, Color.ORANGE);

            doc.remove(0, doc.getLength()); // Clear previous content

            for (String line : logLines) {
                Style style = defaultStyle;
                if (line.contains("[Added Mods]")) {
                    style = addedStyle;
                } else if (line.contains("[Removed Mods]")) {
                    style = removedStyle;
                } else if (line.contains("[Updated Mods]")) {
                    style = updatedStyle;
                }
                doc.insertString(doc.getLength(), line + "\n", style);
            }
        } catch (IOException | BadLocationException e) {
            e.printStackTrace();
        }
    }
}
