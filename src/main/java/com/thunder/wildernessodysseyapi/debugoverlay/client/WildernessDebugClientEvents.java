package com.thunder.wildernessodysseyapi.debugoverlay.client;

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
     * Changes pages or scrolls content on a configured key press while F3 is visible.
     * A normal gameplay screen check prevents the page controls from acting in
     * menus, chat, inventories, or any other screen that owns arrow-key input.
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

        switch (DebugKeyMappings.navigationActionFor(event.getKey(), event.getScanCode())) {
            case PREVIOUS -> WildernessDebugManager.get().previousPage();
            case NEXT -> WildernessDebugManager.get().nextPage();
            case SCROLL_UP -> WildernessDebugManager.get().scrollUp();
            case SCROLL_DOWN -> WildernessDebugManager.get().scrollDown();
            case NONE -> {
                // The normal input system retains ownership of every other key.
            }
        }
    }
}
