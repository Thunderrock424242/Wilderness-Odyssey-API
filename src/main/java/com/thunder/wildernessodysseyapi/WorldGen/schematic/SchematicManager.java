package com.thunder.wildernessodysseyapi.WorldGen.schematic;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads .schem files from data packs under the {@code structures} folder.
 */
public class SchematicManager extends SimplePreparableReloadListener<Map<ResourceLocation, Clipboard>> {
    public static final SchematicManager INSTANCE = new SchematicManager();

    private final Map<ResourceLocation, Clipboard> schematics = new HashMap<>();

    private SchematicManager() {}

    @Override
    protected Map<ResourceLocation, Clipboard> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Clipboard> loaded = new HashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources("structures", rl -> rl.getPath().endsWith(".schem"));
        for (var entry : resources.entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath().substring("structures/".length(), file.getPath().length() - ".schem".length());
            ResourceLocation id = ResourceLocation.tryBuild(file.getNamespace(), path);
            try (InputStream in = entry.getValue().open()) {
                ClipboardFormat fmt = ClipboardFormats.findByAlias("schem");
                if (fmt != null) {
                    try (ClipboardReader reader = fmt.getReader(in)) {
                        loaded.put(id, reader.read());
                    }
                }
            } catch (Exception e) {
                System.err.println("[SchematicManager] Failed to load " + file + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, Clipboard> loaded, ResourceManager manager, ProfilerFiller profiler) {
        schematics.clear();
        schematics.putAll(loaded);
    }

    /**
     * Gets a clipboard for the given id, or {@code null} if not loaded.
     */
    public Clipboard get(ResourceLocation id) {
        return schematics.get(id);
    }
}
