package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class TemporalEchoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TemporalRift");
    private static long lastCheckedDay = -1L;

    private TemporalEchoManager() {
    }

    public static void tick(MinecraftServer server) {
        if (!TemporalRiftConfig.ENABLE_TEMPORAL_ECHOES.get()) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        if (currentDay == lastCheckedDay) {
            return;
        }
        lastCheckedDay = currentDay;

        TemporalEchoSavedData data = TemporalEchoSavedData.get(server);
        List<TemporalEcho> due = new ArrayList<>();
        for (TemporalEcho echo : data.getPendingEchoes()) {
            if (currentDay >= echo.getRevealDay()) {
                due.add(echo);
            }
        }

        for (TemporalEcho echo : due) {
            applyEcho(overworld, echo);
            data.removeEcho(echo);
        }
    }

    public static void recordPlayerPlacedBlock(ServerLevel pastLevel, BlockPos pos, BlockState placedState, ServerPlayer player) {
        if (!TemporalRiftConfig.ENABLE_TEMPORAL_ECHOES.get()
                || placedState.isAir()
                || placedState.is(TemporalRiftBlocks.RIFT_CORE.get())
                || placedState.is(TemporalRiftBlocks.TIME_CAPSULE.get())
                || !placedState.isSolid()) {
            return;
        }

        MinecraftServer server = pastLevel.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        long revealDay = currentDay + TemporalRiftConfig.TEMPORAL_ECHO_DELAY_DAYS.get();
        String materialKey = materialKeyFor(placedState);
        TemporalEchoSavedData.get(server).addEcho(new TemporalEcho(pos, revealDay, materialKey, player.getName().getString()));
    }

    private static void applyEcho(ServerLevel overworld, TemporalEcho echo) {
        BlockPos source = echo.getSourcePos();
        int y = Math.max(overworld.getMinBuildHeight() + 2, source.getY() - TemporalRiftConfig.TEMPORAL_ECHO_BURIAL_DEPTH.get());
        BlockPos target = new BlockPos(source.getX(), y, source.getZ());
        BlockState current = overworld.getBlockState(target);
        if (!canReplaceWithEcho(overworld, target, current)) {
            return;
        }

        overworld.setBlock(target, ruinedStateFor(echo.getMaterialKey()), 3);
        LOGGER.info("[TemporalRift] Temporal echo from {} materialized at {} as {}.", echo.getPlayerName(), target, echo.getMaterialKey());
    }

    private static boolean canReplaceWithEcho(ServerLevel overworld, BlockPos target, BlockState current) {
        return !current.is(Blocks.BEDROCK)
                && overworld.getBlockEntity(target) == null
                && (current.isAir()
                || current.is(Blocks.GRASS_BLOCK)
                || current.is(Blocks.DIRT)
                || current.is(Blocks.STONE)
                || current.is(Blocks.DEEPSLATE)
                || current.is(Blocks.SAND)
                || current.is(Blocks.GRAVEL)
                || current.is(Blocks.TUFF)
                || current.is(Blocks.COBBLESTONE)
                || current.is(Blocks.COBBLED_DEEPSLATE));
    }

    private static String materialKeyFor(BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)) {
            return "wood";
        }
        if (state.is(BlockTags.STONE_BRICKS) || state.is(Blocks.BRICKS) || state.is(Blocks.DEEPSLATE_BRICKS)) {
            return "brick";
        }
        if (state.is(BlockTags.SAND) || state.is(Blocks.SANDSTONE)) {
            return "sand";
        }
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.GOLD_BLOCK)) {
            return "metal";
        }
        if (state.is(BlockTags.DIRT)) {
            return "earth";
        }
        return "stone";
    }

    private static BlockState ruinedStateFor(String materialKey) {
        return switch (materialKey) {
            case "wood" -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            case "brick" -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case "sand" -> Blocks.SANDSTONE.defaultBlockState();
            case "metal" -> Blocks.WEATHERED_COPPER.defaultBlockState();
            case "earth" -> Blocks.COARSE_DIRT.defaultBlockState();
            default -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        };
    }
}
