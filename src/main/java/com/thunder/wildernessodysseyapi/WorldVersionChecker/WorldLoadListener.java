package com.thunder.wildernessodysseyapi.WorldVersionChecker;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class WorldLoadListener {

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        MinecraftServer server = level.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        var dataStorage = overworld.getDataStorage();
        var data = dataStorage.computeIfAbsent(WorldVersionData::load, WorldVersionData::new, WorldVersionData.FILE_NAME);

        int current = ModConstants.CURRENT_WORLD_VERSION;
        int worldVer = data.getVersion();

        if (worldVer < current) {
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (player != null) {
                player.sendSystemMessage(Component.literal("§c[Warning] This world was created with an older version of the mod."));
                player.sendSystemMessage(Component.literal("§6Please BACK UP your world and report any issues."));
            }

            data.setVersion(current); // auto-upgrade version in save
            dataStorage.set(WorldVersionData.FILE_NAME, data);
        }
    }
}
