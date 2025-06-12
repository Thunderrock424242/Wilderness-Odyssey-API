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

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class WorldLoadListener {
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        var storage = level.getDataStorage();
        var data = storage.computeIfAbsent(WorldVersionData.factory(), WorldVersionData.FILE_NAME);

        int expMaj = ModConstants.CURRENT_WORLD_VERSION_MAJOR;
        int expMin = ModConstants.CURRENT_WORLD_VERSION_MINOR;
        int wMaj   = data.getMajor();
        int wMin   = data.getMinor();

        // If world version is lower than current, warn first player
        if (wMaj < expMaj || (wMaj == expMaj && wMin < expMin)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                        String.format("[Warning] World version %d.%d is outdated; expected %d.%d.",
                                wMaj, wMin, expMaj, expMin)
                ));
                player.sendSystemMessage(Component.literal(
                        "Please BACK UP your world, update the mod on both server and client, and verify compatibility before proceeding."
                ));
            }
            // Do not auto-upgrade; version remains unchanged until manual action
        }
    }
}
