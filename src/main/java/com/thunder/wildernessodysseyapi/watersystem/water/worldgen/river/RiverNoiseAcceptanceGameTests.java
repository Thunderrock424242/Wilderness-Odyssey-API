package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.HashSet;

/** Opt-in native noise-pipeline acceptance in separate storage inside a disposable audit world. */
@GameTestHolder("wildernessodysseyapi_river_acceptance_tests")
@PrefixGameTestTemplate(false)
public final class RiverNoiseAcceptanceGameTests {
    private RiverNoiseAcceptanceGameTests() { }

    @GameTest(template = "water_flow", timeoutTicks = 6000)
    public static void nativeNoiseGenerationMatchesAcrossRequestedChunkOrders(GameTestHelper helper) throws Exception {
        var original = helper.getLevel();
        var root = original.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        helper.assertTrue(root.toString().contains(".codex-build") && root.toString().contains("run-audit-river-"),
                "Native river acceptance requires a unique isolated river audit world");
        var storageRoot = root.resolve("river-noise-acceptance");
        Files.createDirectories(storageRoot);
        var storage = LevelStorageSource.createDefault(storageRoot);
        boolean enabledBefore = RiverWorldgenConfig.ENABLED.get();
        List<ChunkPos> requested = new ArrayList<>();
        Map<Long, CompoundTag> forward = new LinkedHashMap<>();
        long started = System.nanoTime();
        try {
            RiverWorldgenConfig.ENABLED.set(true);
            try (var access = storage.validateAndCreateAccess("forward-" + UUID.randomUUID())) {
                var level = create(original, access);
                try {
                    int candidates = 0;
                    int reaches = 0;
                    long nativeNanos = 0;
                    var timings = new ArrayList<Long>();
                    var queries = new ArrayDeque<ChunkPos>();
                    var visited = new HashSet<Long>();
                    for (int z : new int[]{0, -128, 128, -256, 256, -512, 512}) {
                        for (int x : new int[]{0, -128, 128, -256, 256, -512, 512}) {
                            queries.add(new ChunkPos(x >> 4, z >> 4));
                        }
                    }
                    while (!queries.isEmpty() && visited.size() < 64) {
                        var query = queries.removeFirst();
                        if (!visited.add(query.toLong())) continue;
                        int x = query.getMinBlockX();
                        int z = query.getMinBlockZ();
                        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
                        long queryStart = System.nanoTime();
                        var plan = RiverGenerationPlanning.plan(level, generator, level.getChunkSource().randomState(), x, z,
                                RiverWorldgenConfig.snapshot(), RiverWorldgenConfig.planningSettings(), RiverWorldgenConfig.maximumDrop());
                        timings.add(System.nanoTime() - queryStart);
                        ModConstants.LOGGER.info("Native river prediction [{}, {}]: {} edges, {} shape columns, {} samples, exhausted={}",
                                x, z, plan.topology().edges().size(), plan.shape().columns().size(),
                                plan.topology().terrainSamples(), plan.topology().budgetExceeded());
                        if (plan.shape().columns().isEmpty()) continue;
                        for (var edge : plan.topology().edges()) {
                            queries.addFirst(new ChunkPos(Math.floorDiv(edge.targetX(), 32) * 2, Math.floorDiv(edge.targetZ(), 32) * 2));
                            queries.addFirst(new ChunkPos(Math.floorDiv(edge.sourceX(), 32) * 2, Math.floorDiv(edge.sourceZ(), 32) * 2));
                        }
                        candidates++;
                        var tile = List.of(new ChunkPos(x >> 4, z >> 4), new ChunkPos((x >> 4) + 1, z >> 4),
                                new ChunkPos(x >> 4, (z >> 4) + 1), new ChunkPos((x >> 4) + 1, (z >> 4) + 1));
                        // Normal public chunk futures exercise the engine's own
                        // concurrent dependencies. These requests belong to the
                        // acceptance driver, never to a river planning callback.
                        long nativeStart = System.nanoTime();
                        for (var position : tile) level.getChunkSource().getChunkFuture(position.x, position.z, ChunkStatus.FULL, true);
                        for (var position : tile) {
                            var chunk = level.getChunk(position.x, position.z);
                            requested.add(position);
                            var metadata = chunk.getExistingData(ModAttachments.RIVER_GENERATION).orElseThrow(
                                    () -> new IllegalStateException("Native pipeline did not capture river terrain"));
                            helper.assertTrue(metadata.processed(), "Native pipeline did not invoke the river lifecycle");
                            forward.put(position.toLong(), physicalSnapshot(level, chunk, metadata));
                            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                                var reach = metadata.reachAt(lx, lz);
                                if (reach != null && reach.kind() != RiverChannelColumn.Kind.LAKE) reaches++;
                            }
                        }
                        nativeNanos += System.nanoTime() - nativeStart;
                        if (reaches >= 32 && hasConnectedBorder(level, requested) || candidates >= 8) break;
                    }
                    ModConstants.LOGGER.info("Native river acceptance: {} requested FULL chunks, {} connected reach columns, {} candidate tiles",
                            requested.size(), reaches, candidates);
                    ModConstants.LOGGER.info("Native river prediction statistics: {}; admission rejections: {}",
                            RiverGenerationPlanning.statistics(level), RiverGenerationPlanning.rejections(level));
                    for (var rejection : RiverGenerationPlanning.rejections(level).values()) {
                        var position = rejection.latest().position();
                        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                        ModConstants.LOGGER.info("Native river rejected {} at {} (finished state: {})", rejection.latest().reason(), position,
                                chunk == null ? "unloaded" : chunk.getBlockState(position));
                    }
                    helper.assertTrue(reaches >= 32 && hasConnectedBorder(level, requested),
                            "Real noise generation must retain at least 32 river columns and a physical crossing between chunks");
                    timings.sort(Long::compare);
                    ModConstants.LOGGER.info("Native river timing: {} planning queries mean={}ms p95={}ms p99={}ms; forward FULL batches={}ms",
                            timings.size(), timings.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0,
                            timings.get((int) Math.ceil(timings.size() * 0.95) - 1) / 1_000_000.0,
                            timings.get((int) Math.ceil(timings.size() * 0.99) - 1) / 1_000_000.0, nativeNanos / 1_000_000.0);
                    // Compare final states after every requested FULL dependency
                    // has completed, including cross-chunk native decoration.
                    for (var position : requested) {
                        var chunk = level.getChunk(position.x, position.z);
                        forward.put(position.toLong(), physicalSnapshot(level, chunk, chunk.getData(ModAttachments.RIVER_GENERATION)));
                    }
                    level.save(null, true, false);
                } finally {
                    NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
                    level.close();
                }
                RiverWorldgenConfig.ENABLED.set(false);
                var reloaded = create(original, access);
                try {
                    for (var position : requested) reloaded.getChunk(position.x, position.z);
                    for (var position : requested) {
                        var chunk = reloaded.getChunk(position.x, position.z);
                        var metadata = chunk.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
                        helper.assertTrue(metadata != null && forward.get(position.toLong()).equals(physicalSnapshot(reloaded, chunk, metadata)),
                                "River persistence changed after saving and reopening with generation disabled at " + position);
                    }
                } finally {
                    NeoForge.EVENT_BUS.post(new LevelEvent.Unload(reloaded));
                    reloaded.close();
                    RiverWorldgenConfig.ENABLED.set(true);
                }
            }
            var forwardOrder = List.copyOf(requested);
            Collections.reverse(requested);
            try (var access = storage.validateAndCreateAccess("reverse-" + UUID.randomUUID())) {
                var level = create(original, access);
                try {
                    for (var position : requested) level.getChunkSource().getChunkFuture(position.x, position.z, ChunkStatus.FULL, true);
                    for (var position : requested) level.getChunk(position.x, position.z);
                    for (var position : requested) {
                        var chunk = level.getChunk(position.x, position.z);
                        var metadata = chunk.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
                        helper.assertTrue(metadata != null && forward.get(position.toLong()).equals(physicalSnapshot(level, chunk, metadata)),
                                "Native river metadata or physical channel differs after reversing requested chunk order at " + position);
                    }
                    level.save(null, true, false);
                } finally {
                    NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
                    level.close();
                }
            }
            RiverWorldgenConfig.ENABLED.set(false);
            try (var access = storage.validateAndCreateAccess("baseline-" + UUID.randomUUID())) {
                var baseline = create(original, access);
                try {
                    long baselineStart = System.nanoTime();
                    for (int batch = 0; batch < forwardOrder.size(); batch += 4) {
                        var tile = forwardOrder.subList(batch, Math.min(batch + 4, forwardOrder.size()));
                        for (var position : tile) baseline.getChunkSource().getChunkFuture(position.x, position.z, ChunkStatus.FULL, true);
                        for (var position : tile) baseline.getChunk(position.x, position.z);
                    }
                    ModConstants.LOGGER.info("Native river timing: generation-disabled same-seed/order/batches {} FULL chunks={}ms (single warmed-JVM sample)",
                            forwardOrder.size(), (System.nanoTime() - baselineStart) / 1_000_000.0);
                } finally {
                    NeoForge.EVENT_BUS.post(new LevelEvent.Unload(baseline));
                    baseline.close();
                }
            }
        } finally {
            RiverWorldgenConfig.ENABLED.set(enabledBefore);
        }
        ModConstants.LOGGER.info("Native river acceptance finished in {} ms", (System.nanoTime() - started) / 1_000_000.0);
        helper.succeed();
    }

    private static ServerLevel create(ServerLevel original, LevelStorageSource.LevelStorageAccess access) {
        var registry = original.registryAccess();
        var settings = registry.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var plains = registry.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), settings);
        return new ServerLevel(original.getServer(), Util.backgroundExecutor(), access,
                new DerivedLevelData(original.getServer().getWorldData(), original.getServer().getWorldData().overworldData()),
                Level.OVERWORLD, new LevelStem(original.dimensionTypeRegistration(), generator), new ChunkProgressListener() {
                    public void updateSpawnPos(ChunkPos position) { }
                    public void onStatusChange(ChunkPos position, ChunkStatus status) { }
                    public void start() { }
                    public void stop() { }
                }, false, BiomeManager.obfuscateSeed(original.getSeed()), List.of(), false, null);
    }

    private static boolean hasConnectedBorder(ServerLevel level, List<ChunkPos> requested) {
        for (var position : requested) {
            var chunk = level.getChunkSource().getChunkNow(position.x, position.z);
            var metadata = chunk == null ? null : chunk.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
            if (metadata == null) continue;
            for (int direction = 0; direction < 2; direction++) {
                var neighbor = level.getChunkSource().getChunkNow(position.x + (direction == 0 ? 1 : 0),
                        position.z + (direction == 1 ? 1 : 0));
                var other = neighbor == null ? null : neighbor.getExistingData(ModAttachments.RIVER_GENERATION).orElse(null);
                if (other == null) continue;
                for (int along = 0; along < 16; along++) {
                    var a = metadata.reachAt(direction == 0 ? 15 : along, direction == 0 ? along : 15);
                    var b = other.reachAt(direction == 0 ? 0 : along, direction == 0 ? along : 0);
                    if (a == null || b == null || a.kind() == RiverChannelColumn.Kind.LAKE || b.kind() == RiverChannelColumn.Kind.LAKE) continue;
                    for (int y = Math.max(a.floorY(), b.floorY()) + 1; y <= Math.min(a.topY(), b.topY()); y++) {
                        var left = new net.minecraft.core.BlockPos(chunk.getPos().getMinBlockX() + (direction == 0 ? 15 : along), y,
                                chunk.getPos().getMinBlockZ() + (direction == 0 ? along : 15));
                        var right = left.offset(direction == 0 ? 1 : 0, 0, direction == 1 ? 1 : 0);
                        if (chunk.getFluidState(left).is(net.minecraft.tags.FluidTags.WATER)
                                && neighbor.getFluidState(right).is(net.minecraft.tags.FluidTags.WATER)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static CompoundTag physicalSnapshot(ServerLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk,
                                                 RiverGenerationChunk metadata) {
        CompoundTag result = new CompoundTag();
        result.put("Reach", metadata.serializeNBT(level.registryAccess()));
        var blocks = new ArrayList<Integer>();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            var reach = metadata.reachAt(x, z);
            if (reach == null) continue;
            for (int y = reach.floorY(); y <= reach.topY() + 1; y++) {
                var position = new net.minecraft.core.BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                blocks.add(net.minecraft.world.level.block.Block.getId(chunk.getBlockState(position)));
            }
        }
        result.putIntArray("Physical", blocks);
        return result;
    }
}
