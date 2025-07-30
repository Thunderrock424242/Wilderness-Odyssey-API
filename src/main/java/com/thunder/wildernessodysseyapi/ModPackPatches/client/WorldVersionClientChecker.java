package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.mojang.realmsclient.RealmsMainScreen;
import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.thunder.wildernessodysseyapi.ModPackPatches.client.WorldVersionChecker.*;

@EventBusSubscriber
public class WorldVersionClientChecker {

    public static boolean PATCH_UPDATE_NOTIFICATION = false;
    public static boolean MINOR_UPDATE_NOTIFICATION = false;
    public static boolean MAJOR_UPDATE_NOTIFICATION = true;

    private static boolean hasCheckedCurrentWorld = false;
    private static String lastCheckedWorldPath = "";

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof LocalPlayer) {
            hasCheckedCurrentWorld = false;
            lastCheckedWorldPath = "";
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.getSingleplayerServer() == null)
        {
            LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, "[WorldVersionClientChecker] Not in singleplayer, skipping world version check.");
            return;
        }

        String currentWorldPath = getWorldVersionPath().toString();
        if (hasCheckedCurrentWorld && lastCheckedWorldPath.equals(currentWorldPath)) return;

        try {
            String configVersion = readVersion(Paths.get("config/world_version.json"));
            Path worldPath = getWorldVersionPath();
            String worldVersion = readVersion(worldPath);

            int[] configSegments = parseVersion(configVersion);
            int[] worldSegments = parseVersion(worldVersion);

            if (isVersionGreater(configSegments, worldSegments)) {
                String updateType = getUpdateType(worldVersion, configVersion);

                if (
                        ("Patch".equals(updateType) && PATCH_UPDATE_NOTIFICATION) || ("Minor".equals(updateType) && MINOR_UPDATE_NOTIFICATION) || ("Major".equals(updateType) && MAJOR_UPDATE_NOTIFICATION)
                )

                {
                    mc.execute(() -> mc.setScreen(new WorldVersionWarningScreen(
                            worldVersion,
                            configVersion,
                            () -> mc.setScreen(null),
                            () -> saveAndQuitToMenu(mc)
                    )));
                }
            }
        } catch (Exception e) {
            LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR,
                    "[WorldVersionClientChecker] Exception during check: " + e.getMessage());
            e.printStackTrace();
        }
        hasCheckedCurrentWorld = true;
        lastCheckedWorldPath = currentWorldPath;
    }

    public static Path getWorldVersionPath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT)
                    .resolve("world_version.json");
        }
        if (mc.level != null) {
            String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            return mc.gameDirectory.toPath()
                    .resolve("saves")
                    .resolve(worldName)
                    .resolve("world_version.json");
        }
        return mc.gameDirectory.toPath().resolve("world_version.json");
    }

    private static void saveAndQuitToMenu(Minecraft mc) {
        boolean isLocal = mc.isLocalServer();
        ServerData serverData = mc.getCurrentServer();
        // Disconnect from the current level
        assert mc.level != null;
        mc.level.disconnect();
        if (isLocal) {
            mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            mc.disconnect();
        }

        TitleScreen titleScreen = new TitleScreen();
        if (isLocal) {
            mc.setScreen(titleScreen);
        } else if (serverData != null && serverData.isRealm()) {
            mc.setScreen(new RealmsMainScreen(titleScreen));
        } else {
            mc.setScreen(new JoinMultiplayerScreen(titleScreen));
        }
    }
}