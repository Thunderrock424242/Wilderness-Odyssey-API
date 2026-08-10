package com.thunder.wildernessodysseyapi.debugoverlay.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.debugoverlay.config.DebugOverlayConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** Client event bridge for the vanilla debug-text hook and page controls. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class WildernessDebugClientEvents {
    private WildernessDebugClientEvents() {
    }

    /**
     * Runs after other debug-text contributors so Vanilla Raw retains their lines.
     * Only the two lists owned by {@code DebugScreenOverlay} are cleared; charts
     * and unrelated NeoForge HUD overlays continue through vanilla's render path.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!DebugOverlayConfig.ENABLE_CUSTOM_DEBUG_HUD.get()) {
            return;
        }

        WildernessDebugManager manager = WildernessDebugManager.get();
        if (manager.render(event.getGuiGraphics(), event.getLeft(), event.getRight())) {
            event.getLeft().clear();
            event.getRight().clear();
        }
    }

    // Visibility tracking implements rememberLastDebugPage without changing F3 state.
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        WildernessDebugManager.get().syncVisibility(Minecraft.getInstance());
    }

    /**
     * Handles only the configured F3 chords after vanilla keyboard processing.
     * The event is non-cancellable, so every vanilla F3 combination remains intact.
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !DebugOverlayConfig.ENABLE_CUSTOM_DEBUG_HUD.get()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        long window = minecraft.getWindow().getWindow();
        if (!InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F3)) {
            return;
        }

        if (DebugKeyMappings.PREVIOUS_PAGE.matches(event.getKey(), event.getScanCode())) {
            WildernessDebugManager.get().previousPage();
        } else if (DebugKeyMappings.NEXT_PAGE.matches(event.getKey(), event.getScanCode())) {
            WildernessDebugManager.get().nextPage();
        }
    }
}
