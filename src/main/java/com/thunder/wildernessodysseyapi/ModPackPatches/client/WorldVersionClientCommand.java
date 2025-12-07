/*
package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.mojang.realmsclient.RealmsMainScreen;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker.Util.LoggerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.nio.file.Path;
import java.nio.file.Paths;



@EventBusSubscriber(value = Dist.CLIENT)
public class WorldVersionClientCommand {


    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("triggerworldversion")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            String worldVersion;
                            String configVersion;

                            try {
                                // Use actual world version file
                                Path worldPath = WorldVersionClientChecker.getWorldVersionPath();
                                worldVersion = WorldVersionChecker.readVersion(worldPath);

                                // Use actual config version file
                                Path configPath = Paths.get("config/world_version.json");
                                configVersion = WorldVersionChecker.readVersion(configPath);
                            } catch (Exception e) {
                                LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, "[WorldVersionClientCommand] Error reading versions: " + e.getMessage());
                                ctx.getSource().sendFailure(Component.literal("Failed to read version files!"));
                                return 0;
                            }

                            LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "[WorldVersionClientCommand] Manually triggering world version warning screen with real versions.");
                            mc.setScreen(new WorldVersionWarningScreen(
                                    worldVersion,
                                    configVersion,
                                    () -> Minecraft.getInstance().setScreen(null), // No onProceed, just show the screen
                                    () -> saveAndQuitToMenu(mc)
                            ));
                            ctx.getSource().sendSuccess(() -> Component.literal("Triggered World Version Warning Screen"), false);
                            return 1;
                        })
        );
    }

    public static void saveAndQuitToMenu(Minecraft minecraft) {
        boolean isLocal = minecraft.isLocalServer();
        ServerData serverData = minecraft.getCurrentServer();
        // Disconnect from the current level
        minecraft.level.disconnect();
        if (isLocal) {
            minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            minecraft.disconnect();
        }

        TitleScreen titleScreen = new TitleScreen();
        if (isLocal) {
            minecraft.setScreen(titleScreen);
        } else if (serverData != null && serverData.isRealm()) {
            minecraft.setScreen(new RealmsMainScreen(titleScreen));
        } else {
            minecraft.setScreen(new JoinMultiplayerScreen(titleScreen));
        }
    }
}*/
