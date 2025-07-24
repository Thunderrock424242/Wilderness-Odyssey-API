package com.thunder.wildernessodysseyapi.intro.event;

import com.thunder.wildernessodysseyapi.intro.client.VideoPlayerScreen;
import com.thunder.wildernessodysseyapi.intro.config.PlayOnJoinConfig;
import com.thunder.wildernessodysseyapi.intro.util.VideoStateManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@OnlyIn(Dist.CLIENT)
public class PlayerJoinHandler {

    @SubscribeEvent
    public void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();

        VideoStateManager.loadState();

        boolean hasPlayed = VideoStateManager.hasVideoBeenPlayed();
        boolean playOnlyOnce = PlayOnJoinConfig.PLAY_ONLY_ONCE.get();

        if (!playOnlyOnce || !hasPlayed) {
            mc.tell(() -> {
                VideoPlayerScreen screen = new VideoPlayerScreen(() -> {
                    if (playOnlyOnce) {
                        VideoStateManager.markVideoAsPlayed();
                    }
                });
                mc.setScreen(screen);
            });
        }
    }
}