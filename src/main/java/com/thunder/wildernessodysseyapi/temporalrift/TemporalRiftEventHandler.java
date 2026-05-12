package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.command.TemporalRiftCommand;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public final class TemporalRiftEventHandler {
    private TemporalRiftEventHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TemporalRiftManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TemporalRiftCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY)
                && event.getEntity() instanceof ServerPlayer player) {
            TemporalEchoManager.recordPlayerPlacedBlock(level, event.getPos(), event.getPlacedBlock(), player);
        }
    }
}
