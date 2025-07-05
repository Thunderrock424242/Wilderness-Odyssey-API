package com.thunder.wildernessodysseyapi.ModPackPatches;

import com.thunder.wildernessodysseyapi.ModPackPatches.util.IconCaptureState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class IconCaptureStateReset {

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide()) return;
        IconCaptureState.iconCaptured = false;
    }
}