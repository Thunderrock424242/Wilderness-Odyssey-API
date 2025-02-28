package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine;

import java.io.FileWriter;
import java.io.IOException;

public class RenderThreadLogger {
    public static void logIncompatibleMod(String modName, Exception e) {
        try (FileWriter writer = new FileWriter("render_thread_incompatibility.log", true)) {
            writer.write("Mod: " + modName + " is incompatible.\n");
            writer.write("Error: " + e.getMessage() + "\n\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}