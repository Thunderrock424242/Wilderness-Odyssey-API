package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.ModpackLocation;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.*;

/** Standalone Java 21 development entry point. No Minecraft bootstrap or world required; local assets improve preview fidelity. */
public final class StructureViewer {
    private StructureViewer() {}

    /** Launches the structure browser, optionally opening one NBT or supported JSON file. */
    public static void main(String[] args) {
        Path open = null, requestedPack = null, report = null;
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Structure Viewer [--modpack <folder>] [--open <file.nbt|file.json>] [--diagnostics <report.json>]\nF1 orbit, F2 freecam, right-drag look, WASD move, F focus all, G focus selected block, R reload.");
            return;
        }
        for (int i=0;i<args.length;i+=2) {
            if(i+1>=args.length)throw new IllegalArgumentException("Missing value for "+args[i]);
            switch(args[i]) {
                case "--open" -> open=Path.of(args[i+1]);
                case "--modpack" -> requestedPack=ModpackLocation.normalize(Path.of(args[i+1]));
                case "--diagnostics" -> report=Path.of(args[i+1]);
                default -> throw new IllegalArgumentException("Unknown option: "+args[i]);
            }
        }
        boolean development=System.getProperty("structureViewer.projectDir")!=null;
        Path local=Path.of(System.getenv().getOrDefault("LOCALAPPDATA",System.getProperty("user.home")),"WildernessOdyssey","StructureViewer");
        Path build=Path.of(System.getProperty("structureViewer.buildDir",(development?Path.of(System.getProperty("structureViewer.projectDir")).resolve("build"):local.resolve("cache")).toString()));
        Path settings=Path.of(System.getProperty("structureViewer.settings",(development?build.resolve("tools/structure-viewer/settings.properties"):local.resolve("settings.properties")).toString()));
        System.setProperty("structureViewer.settings",settings.toString());
        Path project=requestedPack;
        if(project==null&&development)project=Path.of(System.getProperty("structureViewer.projectDir"));
        String launcher=System.getProperty("jpackage.app-path","");
        if(project==null&&!launcher.isBlank())project=ModpackLocation.nearby(Path.of(launcher).getParent()).orElse(null);
        if(project==null)project=ModpackLocation.nearby(Path.of(".")).orElse(null);
        if(project==null)try {
            Path saved=ViewerSettings.read(settings).modpack();
            if(saved!=null&&Files.isDirectory(saved))project=saved;
        }catch(java.io.IOException|RuntimeException error){System.err.println("Could not restore last modpack: "+error.getMessage());}
        if(project==null)project=Path.of(".").toAbsolutePath().normalize();
        if(report!=null){writeDiagnostics(report,project,settings);return;}
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("The viewer requires a desktop display.");
        Path initial = open;
        Path root=project;
        SwingUtilities.invokeLater(() -> {
            ViewerTheme.install();
            ViewerWindow window = new ViewerWindow(root, build);
            window.setVisible(true);
            if (initial != null) window.load(initial);
        });
    }

    private static void writeDiagnostics(Path report,Path root,Path settings) {
        var json=new com.google.gson.JsonObject();
        json.addProperty("javaVersion",System.getProperty("java.version"));
        json.addProperty("javaHome",System.getProperty("java.home"));
        json.addProperty("applicationPath",System.getProperty("jpackage.app-path","Gradle / Java launcher"));
        json.addProperty("modpack",root.toString());json.addProperty("settings",settings.toString());
        json.addProperty("archiveSupport",java.nio.file.spi.FileSystemProvider.installedProviders().stream().anyMatch(p->p.getScheme().equals("jar")));
        json.addProperty("desktopSupport",!GraphicsEnvironment.isHeadless());
        try {Files.createDirectories(report.toAbsolutePath().getParent());Files.writeString(report,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));}
        catch(java.io.IOException error){throw new IllegalStateException("Could not write runtime diagnostics",error);}
    }
}

