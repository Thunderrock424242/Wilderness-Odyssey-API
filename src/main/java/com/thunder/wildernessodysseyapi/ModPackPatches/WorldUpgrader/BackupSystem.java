package com.thunder.wildernessodysseyapi.ModPackPatches.WorldUpgrader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupSystem {
    private static final String BACKUP_FOLDER = "backups/";

    public static void createBackup(MinecraftServer server) {
        try {
            Path worldPath = server.getWorldPath(LevelResource.ROOT);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Path backupPath = worldPath.resolve(BACKUP_FOLDER + "backup_" + timestamp + ".zip");

            System.out.println("[Wilderness Odyssey] Creating world backup...");
            Files.copy(worldPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Wilderness Odyssey] Backup saved at: " + backupPath.toString());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[Wilderness Odyssey] Backup failed!");
        }
    }
}