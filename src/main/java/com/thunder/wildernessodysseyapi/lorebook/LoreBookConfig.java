package com.thunder.wildernessodysseyapi.lorebook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and exposes the configurable loot-book entries used by the lore book
 * system.
 */
public class LoreBookConfig {
    public static final String CONFIG_NAME = "lore_books.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private float chance = 0.03f;
    private List<LoreBookEntry> books = new ArrayList<>();

    /**
     * @return Per-chest chance of injecting a generated lore book entry.
     */
    public float chance() {
        return chance;
    }

    /**
     * @return Configured lore book definitions that can be generated for players.
     */
    public List<LoreBookEntry> books() {
        return books;
    }

    /**
     * Loads the lore book configuration from the mod config directory, seeding
     * a default file from resources when missing.
     *
     * @return Loaded config instance or defaults when reading/parsing fails.
     */
    public static LoreBookConfig load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(ModConstants.MOD_ID).resolve(CONFIG_NAME);
        ensureDefaultConfig(configPath);
        if (!Files.exists(configPath)) {
            return new LoreBookConfig();
        }
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            LoreBookConfig config = GSON.fromJson(reader, LoreBookConfig.class);
            if (config == null) {
                return new LoreBookConfig();
            }
            if (config.books == null) {
                config.books = new ArrayList<>();
            }
            return config;
        } catch (IOException | JsonParseException e) {
            ModConstants.LOGGER.warn("Failed to read lore books config at {}.", configPath, e);
            return new LoreBookConfig();
        }
    }

    private static void ensureDefaultConfig(Path configPath) {
        if (Files.exists(configPath)) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to create config directory for lore books.", e);
            return;
        }
        try (InputStream in = LoreBookConfig.class.getResourceAsStream("/config/" + ModConstants.MOD_ID + "/" + CONFIG_NAME)) {
            if (in == null) {
                return;
            }
            Files.copy(in, configPath);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to seed default lore books config at {}.", configPath, e);
        }
    }

    /**
     * One configured book entry from {@code lore_books.json}.
     *
     * @param id Internal unique key for this lore entry.
     * @param title Display title written into the generated book.
     * @param author Display author for the generated book.
     * @param pages Ordered pages written into the generated book.
     */
    public record LoreBookEntry(String id, String title, String author, List<String> pages) {
    }
}
