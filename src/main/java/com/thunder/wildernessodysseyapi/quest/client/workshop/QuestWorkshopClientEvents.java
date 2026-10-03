package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.quest.network.QuestWorkshopPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Dist-gated presentation and tick bridge; no common initializer refers to native screens. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class QuestWorkshopClientEvents {
    private QuestWorkshopClientEvents() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { QuestWorkshopClientState.INSTANCE.tick(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { QuestWorkshopClientState.INSTANCE.clear(); }

    static {
        QuestWorkshopClientState.INSTANCE.listener = kind -> {
            var client = Minecraft.getInstance();
            if (kind == QuestWorkshopPayload.Kind.OPEN || kind == QuestWorkshopPayload.Kind.SNAPSHOT && client.screen instanceof QuestWorkshopScreen)
                QuestWorkshopClientState.INSTANCE.model().ifPresent(model -> client.setScreen(new QuestWorkshopScreen(model)));
        };
    }
}
