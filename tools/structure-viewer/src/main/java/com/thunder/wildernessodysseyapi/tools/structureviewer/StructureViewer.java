package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.ViewerWindow;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/** Standalone Java 21 development entry point. No Minecraft bootstrap or world required; local assets improve preview fidelity. */
public final class StructureViewer {
    private StructureViewer() {}

    /** Launches the structure browser, optionally opening one NBT or supported JSON file. */
    public static void main(String[] args) {
        Path open = null;
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Structure Viewer [--open <file.nbt|file.json>]\nF1 orbit, F2 freecam, right-drag look, WASD move, F focus all, G focus selected block, R reload.");
            return;
        }
        if (args.length == 2 && args[0].equals("--open")) open = Path.of(args[1]);
        else if (args.length != 0) throw new IllegalArgumentException("Usage: StructureViewer [--open <file.nbt|file.json>]");
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("The viewer requires a desktop display.");
        Path project = Path.of(System.getProperty("structureViewer.projectDir", ".")).toAbsolutePath().normalize();
        Path build = Path.of(System.getProperty("structureViewer.buildDir", project.resolve("build").toString()));
        Path initial = open;
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException exception) {
                System.err.println("Using default Swing theme: " + exception.getMessage());
            }
            ViewerWindow window = new ViewerWindow(project, build);
            window.setVisible(true);
            if (initial != null) window.load(initial);
        });
    }
}

