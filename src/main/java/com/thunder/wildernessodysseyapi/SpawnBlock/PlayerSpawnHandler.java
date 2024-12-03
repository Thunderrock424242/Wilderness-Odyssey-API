package com.thunder.wildernessodysseyapi.SpawnBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class PlayerSpawnHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel world = player.serverLevel(); // Corrected method to get the ServerLevel
        BlockPos spawnPos = world.getSharedSpawnPos();

        // Ensure the player spawns on the custom block
        player.teleportTo(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel world = player.serverLevel(); // Corrected method to get the ServerLevel
        BlockPos spawnPos = world.getSharedSpawnPos();

        // Teleport player to the custom block on respawn
        player.teleportTo(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
    }
}
