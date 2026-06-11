package com.thunder.wildernessodysseyapi.playtest.verification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class LinkedMinecraftAccountStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(ModConstants.MOD_ID)
            .resolve("linked-minecraft-account.json");

    synchronized Optional<LinkedMinecraftAccount> load() {
        if (!Files.exists(STORE_PATH)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(STORE_PATH, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            return LinkedMinecraftAccount.fromJson((JsonObject) parsed);
        } catch (Exception exception) {
            ModConstants.LOGGER.warn("[MinecraftVerification] Failed to read linked account state at {}.", STORE_PATH, exception);
            return Optional.empty();
        }
    }

    synchronized boolean save(LinkedMinecraftAccount account) {
        try {
            Files.createDirectories(STORE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(STORE_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(account.toJson(), writer);
            }
            return true;
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("[MinecraftVerification] Failed to save linked account state at {}.", STORE_PATH, exception);
            return false;
        }
    }

    synchronized boolean clear() {
        try {
            return Files.deleteIfExists(STORE_PATH);
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("[MinecraftVerification] Failed to clear linked account state at {}.", STORE_PATH, exception);
            return false;
        }
    }

    Path path() {
        return STORE_PATH;
    }
}
