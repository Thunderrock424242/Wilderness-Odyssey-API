package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
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

public final class EchoBuildEchoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TemporalRift");
    private static final int ECHO_SAMPLE_PERCENT = 35;
    private static long lastCheckedDay = -1L;

    private EchoBuildEchoManager() {
    }

    public static void tick(MinecraftServer server) {
        if (!TemporalRiftConfig.ENABLE_ECHO_BUILD_ECHOES.get()) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        ServerLevel echo = server.getLevel(TemporalRiftDimensions.THE_ECHO_KEY);
        if (overworld == null || echo == null) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        if (currentDay == lastCheckedDay) {
            return;
        }
        lastCheckedDay = currentDay;

        EchoBuildEchoSavedData data = EchoBuildEchoSavedData.get(server);
        List<EchoBuildEcho> due = new ArrayList<>();
        for (EchoBuildEcho echoBuildEcho : data.pendingEchoes()) {
            if (currentDay >= echoBuildEcho.revealDay()) {
                due.add(echoBuildEcho);
            }
        }

        for (EchoBuildEcho echoBuildEcho : due) {
            if (applyEcho(echo, echoBuildEcho)) {
                data.removeEcho(echoBuildEcho);
            }
        }
    }

    public static void recordOverworldPlacedBlock(ServerLevel overworld, BlockPos pos, BlockState placedState, ServerPlayer player) {
        if (!shouldRecord(overworld, pos, placedState)) {
            return;
        }

        long hash = mix(overworld.getSeed() ^ pos.asLong() ^ 0xE0C0E0L);
        if (Math.floorMod(hash, 100) >= ECHO_SAMPLE_PERCENT) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        long revealDay = currentDay + 1L + Math.floorMod(hash, 3);
        BlockPos target = distortedTarget(overworld, pos, hash);
        String materialKey = materialKeyFor(placedState);
        EchoBuildEchoSavedData.get(overworld.getServer()).addEcho(new EchoBuildEcho(
                pos,
                target,
                revealDay,
                materialKey,
                player.getName().getString(),
                TemporalEcho.Type.PLACE
        ));
    }

    public static void recordOverworldBrokenBlock(ServerLevel overworld, BlockPos pos, BlockState brokenState, ServerPlayer player) {
        if (!shouldRecord(overworld, pos, brokenState)) {
            return;
        }

        long hash = mix(overworld.getSeed() ^ pos.asLong() ^ 0xB12EA7L);
        if (Math.floorMod(hash, 100) >= ECHO_SAMPLE_PERCENT / 2) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        long revealDay = currentDay + 1L + Math.floorMod(hash, 4);
        BlockPos target = distortedTarget(overworld, pos, hash);
        String materialKey = materialKeyFor(brokenState);
        EchoBuildEchoSavedData.get(overworld.getServer()).addEcho(new EchoBuildEcho(
                pos,
                target,
                revealDay,
                materialKey,
                player.getName().getString(),
                TemporalEcho.Type.BREAK
        ));
    }

    private static boolean shouldRecord(ServerLevel overworld, BlockPos pos, BlockState state) {
        return TemporalRiftConfig.ENABLE_ECHO_BUILD_ECHOES.get()
                && overworld.dimension().equals(Level.OVERWORLD)
                && !state.isAir()
                && state.isSolid()
                && !state.is(Blocks.BEDROCK)
                && !state.is(TemporalRiftBlocks.RIFT_CORE.get())
                && !state.is(TemporalRiftBlocks.TIME_CAPSULE.get());
    }

    private static BlockPos distortedTarget(ServerLevel overworld, BlockPos pos, long hash) {
        int dx = Math.floorMod(hash, 9) - 4;
        int dy = Math.floorMod(hash >>> 8, 3) - 1;
        int dz = Math.floorMod(hash >>> 16, 9) - 4;
        int y = Math.max(overworld.getMinBuildHeight() + 2, Math.min(overworld.getMaxBuildHeight() - 2, pos.getY() + dy));
        return new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
    }

    private static boolean applyEcho(ServerLevel echoLevel, EchoBuildEcho echoBuildEcho) {
        BlockPos target = echoBuildEcho.targetPos();
        if (!echoLevel.hasChunkAt(target)) {
            return false;
        }

        BlockState current = echoLevel.getBlockState(target);
        if (!canReplaceWithEcho(echoLevel, target, current)) {
            return true;
        }

        BlockState replacement = echoBuildEcho.type() == TemporalEcho.Type.BREAK
                ? scarredStateFor(echoBuildEcho.materialKey())
                : ruinedStateFor(echoBuildEcho.materialKey());
        echoLevel.setBlock(target, replacement, 3);
        LOGGER.info("[TemporalRift] Echo copy from {} distorted {} into The Echo at {}.", echoBuildEcho.playerName(), echoBuildEcho.sourcePos(), target);
        return true;
    }

    private static boolean canReplaceWithEcho(ServerLevel echoLevel, BlockPos target, BlockState current) {
        return !current.is(Blocks.BEDROCK)
                && echoLevel.getBlockEntity(target) == null
                && (current.isAir()
                || current.is(Blocks.GRASS_BLOCK)
                || current.is(Blocks.DIRT)
                || current.is(Blocks.STONE)
                || current.is(Blocks.DEEPSLATE)
                || current.is(Blocks.SAND)
                || current.is(Blocks.GRAVEL)
                || current.is(Blocks.TUFF)
                || current.is(Blocks.COBBLESTONE)
                || current.is(Blocks.COBBLED_DEEPSLATE)
                || current.is(BlockTags.LEAVES));
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

    private static BlockState scarredStateFor(String materialKey) {
        return switch (materialKey) {
            case "wood" -> Blocks.PODZOL.defaultBlockState();
            case "brick" -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case "sand" -> Blocks.GRAVEL.defaultBlockState();
            case "metal" -> Blocks.TUFF.defaultBlockState();
            case "earth" -> Blocks.ROOTED_DIRT.defaultBlockState();
            default -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        };
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
