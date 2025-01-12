package com.thunder.wildernessodysseyapi.SpawnBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class PlayerSpawnHandler {

    private static final AtomicInteger spawnIndex = new AtomicInteger(0);
    private static List<BlockPos> spawnBlocks = null;

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel world = player.serverLevel();

        // Find spawn blocks if not already done
        if (spawnBlocks == null || spawnBlocks.isEmpty()) {
            spawnBlocks = WorldSpawnHandler.findAllWorldSpawnBlocks(world);
        }

        if (!spawnBlocks.isEmpty()) {
            // Get the next spawn block in round-robin order
            BlockPos spawnPos = getNextSpawnBlock();
            player.teleportTo(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel world = player.serverLevel();

        if (!spawnBlocks.isEmpty()) {
            // Get the next spawn block in round-robin order
            BlockPos spawnPos = getNextSpawnBlock();
            player.teleportTo(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
    }

    private static BlockPos getNextSpawnBlock() {
        int index = spawnIndex.getAndUpdate(i -> (i + 1) % spawnBlocks.size());
        return spawnBlocks.get(index);
    }
}