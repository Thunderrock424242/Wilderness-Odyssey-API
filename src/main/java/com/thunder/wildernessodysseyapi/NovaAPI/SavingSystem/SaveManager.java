package com.thunder.wildernessodysseyapi.NovaAPI.SavingSystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SaveManager {
    private static final Path SAVE_DIR = Paths.get("config", "NovaAPI", "saves");
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor();

    static {
        try {
            Files.createDirectories(SAVE_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create save directory!", e);
        }
    }

    public static void saveDataAsync(String fileName, CompoundTag data) {
        SAVE_EXECUTOR.execute(() -> saveData(fileName, data));
    }

    private static void saveData(String fileName, CompoundTag data) {
        Path filePath = SAVE_DIR.resolve(fileName + ".dat");
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            NbtIo.writeCompressed(data, bos);
        } catch (IOException e) {
            System.err.println("Failed to save data: " + fileName);
            e.printStackTrace();
        }
    }

    public static CompoundTag loadData(String fileName) {
        Path filePath = SAVE_DIR.resolve(fileName + ".dat");
        if (!Files.exists(filePath)) return new CompoundTag();

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            return NbtIo.readCompressed(fis, NbtAccounter.unlimitedHeap()); // FIX: Added NbtAccounter
        } catch (IOException e) {
            System.err.println("Failed to load data: " + fileName);
            e.printStackTrace();
            return new CompoundTag();
        }
    }

    public static void shutdown() {
        SAVE_EXECUTOR.shutdown();
    }
}