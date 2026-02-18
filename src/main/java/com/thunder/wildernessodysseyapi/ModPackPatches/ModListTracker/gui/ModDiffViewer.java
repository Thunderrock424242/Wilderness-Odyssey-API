package com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.gui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Utility window for viewing logged mod list changes.
 */
public class ModDiffViewer {
    private static final Path LOG_FILE = Paths.get("logs/mod-changes.log");

    /**
     * Creates and shows the swing window displaying the diff log.
     */
    public static void createAndShowGUI() {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.warn("Skipping mod diff viewer GUI because the environment is headless. See logs/mod-changes.log for details.");
            return;
        }

        JFrame frame = new JFrame("Mod List Differences");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Keep game running after closing GUI
        frame.setSize(600, 400);

        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Arial", Font.PLAIN, 14));

        JScrollPane scrollPane = new JScrollPane(textPane);
        frame.add(scrollPane, BorderLayout.CENTER);

        loadLog(textPane);

        frame.setVisible(true);
    }

    /**
     * Loads the log file and writes its color-coded contents into the text pane.
     */
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

            doc.remove(0, doc.getLength());

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
