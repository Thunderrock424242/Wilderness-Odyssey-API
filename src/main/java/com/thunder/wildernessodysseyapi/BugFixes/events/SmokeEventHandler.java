package com.thunder.wildernessodysseyapi.BugFixes.events;

import com.thunder.wildernessodysseyapi.BugFixes.util.SmokeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class SmokeEventHandler {

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent event) {
        // Ensure the event's level is a server world
        if (event.getLevel() instanceof ServerLevel serverWorld) {
            serverWorld.players().forEach(player -> {
                BlockPos playerPos = player.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-16, -16, -16), playerPos.offset(16, 16, 16))) {
                    BlockState state = serverWorld.getBlockState(pos);
                    if (state.is(Blocks.CAMPFIRE)) {
                        SmokeUtils.spawnSmoke(serverWorld, pos);
                    }
                }
            });
        }
    }
}
