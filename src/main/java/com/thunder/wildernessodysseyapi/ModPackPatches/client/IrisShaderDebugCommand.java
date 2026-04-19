package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker.Util.LoggerUtil;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Client-side debug command to inspect common Iris/Oculus shader screen issues.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ModConstants.MOD_ID)
public class IrisShaderDebugCommand {

    private static String lastScreenClassName = "";
    private static boolean autoDebugRanForCurrentScreen = false;

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("irisdebug")
                .executes(ctx -> runIrisDebug(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false),
                        line -> ctx.getSource().sendFailure(Component.literal(line)))));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Screen currentScreen = minecraft.screen;
        String className = currentScreen == null ? "" : currentScreen.getClass().getName();

        if (!className.equals(lastScreenClassName)) {
            lastScreenClassName = className;
            autoDebugRanForCurrentScreen = false;
        }

        if (!autoDebugRanForCurrentScreen && isIrisShaderScreen(currentScreen)) {
            autoDebugRanForCurrentScreen = true;
            minecraft.player.sendSystemMessage(Component.literal("[WO Shader Debug] Iris shader screen opened. Running auto diagnostics..."));
            runIrisDebug(
                    line -> minecraft.player.sendSystemMessage(Component.literal(line)),
                    line -> minecraft.player.sendSystemMessage(Component.literal(line))
            );
        }
    }

    private static boolean isIrisShaderScreen(Screen screen) {
        if (screen == null) {
            return false;
        }

        String className = screen.getClass().getName().toLowerCase();
        return className.contains("iris") && (className.contains("shader") || className.contains("shaderpack"));
    }

    private static int runIrisDebug(Consumer<String> successSink, Consumer<String> failureSink) {
        boolean irisLoaded = ModList.get().isLoaded("iris");
        boolean oculusLoaded = ModList.get().isLoaded("oculus");
        boolean embeddiumLoaded = ModList.get().isLoaded("embeddium");
        boolean sodiumLoaded = ModList.get().isLoaded("sodium");

        successSink.accept("[WO Shader Debug] Iris loaded: " + irisLoaded);
        successSink.accept("[WO Shader Debug] Oculus loaded: " + oculusLoaded);
        successSink.accept("[WO Shader Debug] Embeddium loaded: " + embeddiumLoaded);
        successSink.accept("[WO Shader Debug] Sodium loaded: " + sodiumLoaded);

        Path gameDir = FMLPaths.GAMEDIR.get();
        Path shaderpacksDir = gameDir.resolve("shaderpacks");
        Path optionsShaders = gameDir.resolve("optionsshaders.txt");

        successSink.accept("[WO Shader Debug] Game dir: " + gameDir.toAbsolutePath());
        successSink.accept("[WO Shader Debug] shaderpacks dir exists: " + Files.isDirectory(shaderpacksDir));

        if (Files.isDirectory(shaderpacksDir)) {
            try (Stream<Path> files = Files.list(shaderpacksDir)) {
                List<Path> packs = files
                        .filter(path -> {
                            String name = path.getFileName().toString().toLowerCase();
                            return Files.isDirectory(path) || name.endsWith(".zip");
                        })
                        .sorted()
                        .toList();

                successSink.accept("[WO Shader Debug] Found shader packs: " + packs.size());

                int previewCount = Math.min(10, packs.size());
                for (int i = 0; i < previewCount; i++) {
                    Path pack = packs.get(i);
                    successSink.accept("  - " + pack.getFileName());
                }

                if (packs.size() > previewCount) {
                    successSink.accept("  ... and " + (packs.size() - previewCount) + " more");
                }
            } catch (IOException e) {
                failureSink.accept("[WO Shader Debug] Failed to read shaderpacks folder: " + e.getMessage());
                LoggerUtil.log(LoggerUtil.ConflictSeverity.WARN,
                        "[IrisShaderDebugCommand] Failed reading shaderpacks directory: " + e.getMessage(), true);
            }
        }

        successSink.accept("[WO Shader Debug] optionsshaders.txt exists: " + Files.exists(optionsShaders));

        if (Files.exists(optionsShaders)) {
            try {
                List<String> lines = Files.readAllLines(optionsShaders);
                String selectedPack = lines.stream()
                        .filter(line -> line.startsWith("shaderPack="))
                        .findFirst()
                        .orElse("shaderPack=<missing>");

                successSink.accept("[WO Shader Debug] " + selectedPack);

                if (selectedPack.equals("shaderPack=<missing>")) {
                    failureSink.accept("[WO Shader Debug] shaderPack entry missing in optionsshaders.txt");
                }
            } catch (IOException e) {
                failureSink.accept("[WO Shader Debug] Failed reading optionsshaders.txt: " + e.getMessage());
            }
        }

        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO,
                "[IrisShaderDebugCommand] Debug command run. Iris=" + irisLoaded + ", Oculus=" + oculusLoaded,
                true);

        successSink.accept("[WO Shader Debug] Debug dump complete. Share this output/log file for troubleshooting.");
        return 1;
    }
}
