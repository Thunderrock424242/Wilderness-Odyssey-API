package com.thunder.wildernessodysseyapi.WorldVersionChecker;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.nio.file.Path;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

@EventBusSubscriber(modid = "novaapi")
public class ServerWorldVersionChecker {

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        var storage = level.getDataStorage();
        var data = storage.computeIfAbsent(WorldVersionData::load, WorldVersionData::new, WorldVersionData.FILE_NAME);

        int current = ModConstants.CURRENT_WORLD_VERSION;
        int worldVer = data.getVersion();

        if (worldVer < current) {
            Path root = level.getServer().getWorldPath(LevelResource.ROOT);
            WorldOutdatedAcknowledgement.validateOrCrash(root, current, worldVer);

            LOGGER.warn("World is outdated. Proceeding after accepted acknowledgment.");
            data.setVersion(current);
            storage.set(WorldVersionData.FILE_NAME, data);
        }
    }

}
