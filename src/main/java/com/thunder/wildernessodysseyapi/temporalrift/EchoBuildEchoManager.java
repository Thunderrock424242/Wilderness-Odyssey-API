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

import com.thunder.wildernessodysseyapi.temporalrift.echo.EchoRealityModel;

/** Owns sampled Earth-to-Echo synchronization using the existing persistent ledger. */
public final class EchoBuildEchoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TemporalRift");

    private EchoBuildEchoManager() {
    }

    /** Explicit outcomes prevent failed world writes from dropping pending changes. */
    enum ApplyResult { APPLIED, SKIPPED, RETRY }

    static boolean shouldDequeue(ApplyResult result) {
        return result != ApplyResult.RETRY;
    }

    public static void tick(MinecraftServer server) {
        if (!enabled()) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        ServerLevel echo = server.getLevel(TemporalRiftDimensions.THE_ECHO_KEY);
        if (overworld == null || echo == null) {
            return;
        }

        if (overworld.getGameTime() % 20 != 0) return;
        long currentDay = overworld.getGameTime() / 24000L;

        EchoBuildEchoSavedData data = EchoBuildEchoSavedData.get(server);
        // At most 64 examined records and 32 world writes per second across the server.
        int applied = 0;
        for (EchoBuildEcho echoBuildEcho : data.nextBatch(64)) {
            if (currentDay - echoBuildEcho.revealDay() > TemporalRiftConfig.ECHO_REALITY_ECHO_RETENTION_DAYS.get()) {
                data.removeEcho(echoBuildEcho);
            } else if (currentDay >= echoBuildEcho.revealDay() && applied < 32) {
                ApplyResult result = applyEcho(echo, echoBuildEcho);
                if (shouldDequeue(result)) data.removeEcho(echoBuildEcho);
                if (result == ApplyResult.APPLIED) applied++;
            }
        }
    }

    public static void recordOverworldPlacedBlock(ServerLevel overworld, BlockPos pos, BlockState placedState, ServerPlayer player) {
        if (!shouldRecord(overworld, pos, placedState)) {
            return;
        }

        long hash = mix(overworld.getSeed() ^ pos.asLong() ^ 0xE0C0E0L);
        if (!EchoRealityModel.sample(hash, TemporalRiftConfig.ECHO_REALITY_ECHO_CHANCE.get(), false)) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        long revealDay = currentDay + 1L + Math.floorMod(hash, 3);
        BlockPos target = distortedTarget(overworld, pos);
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
        if (!EchoRealityModel.sample(hash, TemporalRiftConfig.ECHO_REALITY_ECHO_CHANCE.get(), true)) {
            return;
        }

        long currentDay = overworld.getGameTime() / 24000L;
        long revealDay = currentDay + 1L + Math.floorMod(hash, 4);
        BlockPos target = distortedTarget(overworld, pos);
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
        return enabled()
                && overworld.dimension().equals(Level.OVERWORLD)
                && !state.isAir()
                && state.isSolid()
                && !state.hasBlockEntity()
                && !state.is(Blocks.BEDROCK)
                && !state.is(TemporalRiftBlocks.RIFT_CORE.get())
                && !state.is(TemporalRiftBlocks.TIME_CAPSULE.get());
    }

    private static BlockPos distortedTarget(ServerLevel overworld, BlockPos pos) {
        long fragment = EchoRealityModel.fragmentHash(overworld.getSeed(), pos.getX(), pos.getY(), pos.getZ());
        int dx = EchoRealityModel.lateralOffset(fragment);
        int dy = EchoRealityModel.verticalOffset(fragment);
        int dz = EchoRealityModel.lateralOffset(fragment >>> 16);
        int y = Math.max(overworld.getMinBuildHeight() + 2, Math.min(overworld.getMaxBuildHeight() - 2, pos.getY() + dy));
        return new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
    }

    private static ApplyResult applyEcho(ServerLevel echoLevel, EchoBuildEcho echoBuildEcho) {
        BlockPos target = echoBuildEcho.targetPos();
        if (echoLevel.isOutsideBuildHeight(target) || !echoLevel.getWorldBorder().isWithinBounds(target)) return ApplyResult.SKIPPED;
        if (!echoLevel.hasChunkAt(target)) {
            return ApplyResult.RETRY;
        }
        var chunk = echoLevel.getChunkAt(target);
        if (chunk.hasData(com.thunder.wildernessodysseyapi.core.ModAttachments.ECHO_CHUNK)
                && chunk.getData(com.thunder.wildernessodysseyapi.core.ModAttachments.ECHO_CHUNK).protects(target)) return ApplyResult.SKIPPED;

        BlockState current = echoLevel.getBlockState(target);
        if (!canReplaceWithEcho(echoLevel, target, current)) {
            return ApplyResult.SKIPPED;
        }

        BlockState replacement = echoBuildEcho.type() == TemporalEcho.Type.BREAK
                ? scarredStateFor(echoBuildEcho.materialKey())
                : ruinedStateFor(echoBuildEcho.materialKey());
        if (replacement.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
            long fragment = EchoRealityModel.fragmentHash(echoLevel.getSeed(), target.getX(), target.getY(), target.getZ());
            replacement = replacement.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                    net.minecraft.core.Direction.Axis.values()[Math.floorMod(fragment, 3)]);
        }
        if (!echoLevel.setBlock(target, replacement, 3)) {
            // The write did not land: retain the pending echo for a later tick.
            return ApplyResult.RETRY;
        }
        chunk.getData(com.thunder.wildernessodysseyapi.core.ModAttachments.ECHO_CHUNK)
                .record(target, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(replacement.getBlock()));
        com.thunder.wildernessodysseyapi.temporalrift.echo.EchoSyncManager.onRealityEcho(echoLevel, target);
        if (TemporalRiftConfig.DEBUG_LOGGING.get()) {
            LOGGER.debug("[TemporalRift] Material synchronization from {} reached Echo Earth at {}.", echoBuildEcho.sourcePos(), target);
        }
        return ApplyResult.APPLIED;
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

    private static boolean enabled() {
        return TemporalRiftConfig.ENABLE_ECHO_BUILD_ECHOES.get() && TemporalRiftConfig.ENABLE_ECHO_REALITY_ECHOES.get();
    }
}
