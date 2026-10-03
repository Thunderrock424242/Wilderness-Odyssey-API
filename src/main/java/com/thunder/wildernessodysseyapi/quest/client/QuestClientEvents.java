package com.thunder.wildernessodysseyapi.quest.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client-only lifecycle hook; never referenced by common/server startup. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class QuestClientEvents {
    private QuestClientEvents() { }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { QuestClientState.INSTANCE.clear(); }
}
