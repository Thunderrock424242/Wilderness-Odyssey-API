package com.thunder.wildernessodysseyapi.ModPackPatches;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class ClientSaveHandler {

    public ClientSaveHandler() {
        // For some custom client events, you might want to register with MinecraftForge.EVENT_BUS:
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * If you want to optimize the final "Saving World" step on a singleplayer game,
     * you can ensure chunks are saved incrementally or flush them just before
     * the integrated server shuts down. On the client side, we can detect
     * the logout from a singleplayer session and do a pre-emptive flush.
     */
    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();

        // Check if we are leaving a singleplayer world.
        // (If the integrated server is running, it means singleplayer.)
        if (mc.hasSingleplayerServer()) {
            // The integrated server is about to close.
            // We can trigger a final chunk flush on the server side to minimize
            // how much is left to save during "Saving World."
            mc.getSingleplayerServer().execute(() -> {
                // Force chunks to save
                mc.getSingleplayerServer().overworld().save(null, false, true);
                // You could also iterate over loaded dimensions if you want to be thorough:
                // for (ServerLevel level : mc.getSingleplayerServer().getAllLevels()) {
                //     level.save(null, false, true);
                // }
            });
        }
    }

    /**
     * (Optional) Periodic approach:
     * If you want to flush chunks more frequently to reduce final save time,
     * you could do so every X ticks on the client side, when in singleplayer.
     */
    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.hasSingleplayerServer()) {
                // Example: flush once every 10,000 ticks on the client side
                long time = mc.level.getGameTime();
                if (time % 10000 == 0) {
                    mc.getSingleplayerServer().execute(() -> {
                        mc.getSingleplayerServer().overworld().save(null, false, true);
                    });
                }
            }
        }
    }
}