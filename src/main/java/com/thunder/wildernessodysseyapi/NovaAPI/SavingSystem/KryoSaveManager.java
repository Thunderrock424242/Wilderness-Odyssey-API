package com.thunder.wildernessodysseyapi.NovaAPI.SavingSystem;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KryoSaveManager {
    private static final Path SAVE_DIR = Paths.get("config", "NovaAPI", "external_saves");
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Kryo kryo = new Kryo();

    static {
        try {
            Files.createDirectories(SAVE_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create save directory!", e);
        }
    }

    public static void registerClass(Class<?> clazz) {
        kryo.register(clazz);
    }

    public static <T> void saveDataAsync(String fileName, T data) {
        SAVE_EXECUTOR.execute(() -> saveData(fileName, data));
    }

    private static <T> void saveData(String fileName, T data) {
        Path filePath = SAVE_DIR.resolve(fileName + ".kryo");
        try (Output output = new Output(new FileOutputStream(filePath.toFile()))) {
            kryo.writeObject(output, data);
        } catch (IOException e) {
            System.err.println("Failed to save data: " + fileName);
            e.printStackTrace();
        }
    }

    public static <T> T loadData(String fileName, Class<T> clazz) {
        Path filePath = SAVE_DIR.resolve(fileName + ".kryo");
        if (!Files.exists(filePath)) return null;

        try (Input input = new Input(new FileInputStream(filePath.toFile()))) {
            return kryo.readObject(input, clazz);
        } catch (IOException e) {
            System.err.println("Failed to load data: " + fileName);
            e.printStackTrace();
            return null;
        }
    }

    public static void shutdown() {
        SAVE_EXECUTOR.shutdown();
    }
}