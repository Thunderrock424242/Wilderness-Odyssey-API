package com.thunder.wildernessodysseyapi.intro.util;

import com.thunder.wildernessodysseyapi.intro.config.PlayOnJoinConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class VideoFileHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoFileHelper.class);

    public static void createDefaultVideoDirectory() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get();
            Path videoDir = configPath.resolve("playonjoin");
            if (!Files.exists(videoDir, new LinkOption[0])) {
                Files.createDirectories(videoDir);
                LOGGER.info("Created video directory at: {}", videoDir);
                Path readmePath = videoDir.resolve("README.txt");
                String readmeContent = "PlayOnJoin Video Directory\n=========================\n\nPlace your intro.mp4 video file in this directory.\n\nThe video will play when you join a world.\nYou can configure the video path in the playonjoin-client.toml config file.\n\nSupported formats: MP4 (H.264/AAC recommended)\n";
                Files.writeString(readmePath, readmeContent);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create video directory", e);
        }

    }

    public static boolean isVideoAvailable() {
        String videoPath = (String) PlayOnJoinConfig.VIDEO_PATH.get();
        File videoFile = new File(FMLPaths.CONFIGDIR.get().toFile(), videoPath);
        return videoFile.exists() && videoFile.isFile();
    }

    public static File getVideoFile() {
        String videoPath = (String)PlayOnJoinConfig.VIDEO_PATH.get();
        return new File(FMLPaths.CONFIGDIR.get().toFile(), videoPath);
    }
}
