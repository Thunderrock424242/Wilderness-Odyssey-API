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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads .schem files from data packs under the {@code structures} folder.
 */
public class SchematicManager extends SimplePreparableReloadListener<Map<ResourceLocation, Clipboard>> {
    public static final SchematicManager INSTANCE = new SchematicManager();

    private static final ClipboardFormat SCHEMATIC_FORMAT = ClipboardFormats.findByAlias("schem");

    private final Map<ResourceLocation, Clipboard> schematics = new HashMap<>();
    private final Map<ResourceLocation, Clipboard> bundledSchematics = new HashMap<>();
    private final Set<ResourceLocation> missingBundledSchematics = new HashSet<>();

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
                if (SCHEMATIC_FORMAT != null) {
                    try (ClipboardReader reader = SCHEMATIC_FORMAT.getReader(in)) {
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
        Clipboard clipboard = schematics.get(id);
        if (clipboard != null) {
            return clipboard;
        }

        synchronized (bundledSchematics) {
            clipboard = bundledSchematics.get(id);
            if (clipboard != null || missingBundledSchematics.contains(id)) {
                return clipboard;
            }

            clipboard = loadBundledSchematic(id);
            if (clipboard != null) {
                bundledSchematics.put(id, clipboard);
            } else {
                missingBundledSchematics.add(id);
            }
            return clipboard;
        }
    }

    private Clipboard loadBundledSchematic(ResourceLocation id) {
        if (SCHEMATIC_FORMAT == null) {
            return null;
        }

        String path = "/assets/" + id.getNamespace() + "/schematics/" + id.getPath() + ".schem";
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            try (ClipboardReader reader = SCHEMATIC_FORMAT.getReader(in)) {
                return reader.read();
            }
        } catch (Exception e) {
            System.err.println("[SchematicManager] Failed to load bundled schematic " + id + ": " + e.getMessage());
            return null;
        }
    }
}
