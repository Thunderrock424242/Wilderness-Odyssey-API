package com.thunder.wildernessodysseyapi.client.notice;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Owns the alpha-notice startup decision and installation-local acknowledgment.
 *
 * <p>The hook runs only on the physical client and replaces the first compatible
 * title screen supplied during a startup. Retaining that exact screen instance
 * avoids assuming vanilla is the only mod responsible for the main menu.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class AlphaNoticeManager {
    /** Increase this value whenever players must accept a materially revised warning. */
    public static final int CURRENT_NOTICE_VERSION = 1;

    /** Single edit point for the official Wilderness Odyssey community invite. */
    public static final URI DISCORD_INVITE_URI = URI.create("https://discord.gg/XHSFDb7EM5");

    private static final String STATE_FILE_NAME = "alpha_notice.properties";
    private static boolean startupDecisionMade;

    private AlphaNoticeManager() {
    }

    /**
     * Replaces the first title screen only when the current notice has not been accepted.
     * File and path errors deliberately leave the supplied title screen unchanged.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen newScreen = event.getNewScreen();
        Minecraft minecraft = Minecraft.getInstance();
        if (startupDecisionMade || !(newScreen instanceof TitleScreen) || minecraft.level != null) {
            return;
        }

        startupDecisionMade = true;
        try {
            Path stateFile = stateFile(FMLPaths.CONFIGDIR.get());
            AlphaNoticeState.ReadResult state = AlphaNoticeState.read(stateFile);
            if (!state.readable()) {
                ModConstants.LOGGER.warn(
                        "Unable to read alpha notice state at {}; continuing to the main menu ({})",
                        stateFile,
                        state.warning()
                );
                return;
            }
            if (state.requiresNotice(CURRENT_NOTICE_VERSION)) {
                event.setNewScreen(new AlphaNoticeScreen(newScreen));
            }
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.warn(
                    "Unable to prepare the alpha development notice; continuing to the main menu",
                    exception
            );
        }
    }

    /** Persists acceptance when possible and always continues to the preserved main menu. */
    public static void acknowledgeAndContinue(Minecraft minecraft, Screen mainMenu) {
        try {
            AlphaNoticeState.write(stateFile(FMLPaths.CONFIGDIR.get()), CURRENT_NOTICE_VERSION);
        } catch (IOException | RuntimeException exception) {
            ModConstants.LOGGER.warn(
                    "Unable to save alpha notice acknowledgment; continuing to the main menu",
                    exception
            );
        }
        minecraft.setScreen(mainMenu);
    }

    /** Opens the official Discord invite with Minecraft's normal platform browser integration. */
    public static void openDiscord() {
        try {
            Util.getPlatform().openUri(DISCORD_INVITE_URI);
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.warn("Unable to open the Wilderness Odyssey Discord invite", exception);
        }
    }

    static Path stateFile(Path configDirectory) {
        return configDirectory.resolve(ModConstants.MOD_ID).resolve(STATE_FILE_NAME);
    }
}
