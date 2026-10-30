package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WaterMutationSafety;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.reactive.ReactiveWaterController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;

/**
 * Explicit dedicated-server acceptance probe. Inactive during ordinary launches.
 * Expected files are assertions only: resume stages never write them back into water.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class ReservoirPersistenceProbe {

    private static final String PREFIX = "wildernessodysseyapi.waterPersistence.";
    private static final boolean ENABLED = System.getProperty(PREFIX + "stage") != null
            && "reservoir".equals(System.getProperty(PREFIX + "scenario", "reservoir"));
    private static final BlockPos ORIGIN = new BlockPos(1_000_008, 128, 1_000_008);
    private static final ChunkPos CHUNK = new ChunkPos(ORIGIN);
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wo_water_persistence_probe",
            Comparator.comparingLong(ChunkPos::toLong));
    private static Probe active;

    private ReservoirPersistenceProbe() { }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!ENABLED) return;
        active = new Probe(event.getServer());
        try { active.start(); }
        catch (Exception error) { active.fail(error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (active == null || active.server != event.getServer() || active.finished) return;
        try { active.tick(); }
        catch (Exception error) { active.fail(error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void loaded(ChunkEvent.Load event) {
        if (active != null && event.getLevel() == active.level && event.getChunk() instanceof LevelChunk chunk
                && chunk.getPos().equals(CHUNK)) {
            try {
                active.loadedChunk = chunk;
                active.loadedCells = cells(chunk);
                active.loadedWork = chunk.getExistingData(ModAttachments.WATER_VOLUME)
                        .map(WaterVolumeChunk::pendingWork).orElse(0);
                active.loads++;
            } catch (Exception error) { active.fail(error); }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void unloaded(ChunkEvent.Unload event) {
        if (active != null && event.getLevel() == active.level && event.getChunk() instanceof LevelChunk chunk
                && chunk.getPos().equals(CHUNK)) {
            try {
                active.unloadedChunk = chunk;
                active.unloadedCells = cells(chunk);
                active.unloads++;
            } catch (Exception error) { active.fail(error); }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        if (active == null || active.server != event.getServer() || !active.successRequested) return;
        try {
            active.checkBalance(ReservoirPersistenceSnapshot.FUNDED_UNITS);
            LevelChunk chunk = active.level.getChunkSource().getChunkNow(CHUNK.x, CHUNK.z);
            check(chunk != null, "Fixture vanished before the stop checkpoint");
            var snapshot = new ReservoirPersistenceSnapshot(active.runId, active.checkpointStage,
                    active.execution, ProcessHandle.current().pid(), active.level.getSeed(), active.level.getGameTime(), cells(chunk));
            if ("draining".equals(active.checkpointStage)) {
                check(awake(snapshot.cells()) > 0, "Mid-drain checkpoint has no awake work");
                check(active.upstream() > 8192 && active.downstream() < 8192, "Drainage finished before its restart checkpoint");
                check(CanonicalWater.get(active.level, ORIGIN.east(3)).volumeUnits() > 0,
                        "Mid-drain checkpoint must cover a water-occupied breach");
            }
            write(active.directory.resolve(active.checkpointStage + ".properties"), snapshot.encode());
            active.stopSnapshot = snapshot;
            ModConstants.LOGGER.info("[Water persistence] checkpoint={} pid={} tick={} units={} upstream={} downstream={}",
                    active.checkpointStage, snapshot.processId(), snapshot.gameTime(), total(snapshot.cells()),
                    active.upstream(), active.downstream());
        } catch (Exception error) { active.fail(error); }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (active == null || active.server != event.getServer()) return;
        Probe probe = active;
        active = null;
        try { probe.receipt(); }
        catch (IOException error) { throw new IllegalStateException("Could not write water persistence receipt", error); }
    }

    private static final class Probe {
        final MinecraftServer server;
        final ServerLevel level;
        final String stage = System.getProperty(PREFIX + "stage");
        final String runId = System.getProperty(PREFIX + "run");
        final String execution = System.getProperty(PREFIX + "execution");
        final Path directory;
        String mode = "loading";
        String checkpointStage;
        String failure;
        boolean finished;
        boolean successRequested;
        boolean physicalAfterRestart;
        boolean isolated;
        boolean metadataUpdateVerified;
        int ticks;
        int funded;
        int stable;
        int loads;
        int unloads;
        int[] previous;
        int[] loadedCells;
        int[] unloadedCells;
        int loadedWork;
        LevelChunk loadedChunk;
        LevelChunk unloadedChunk;
        ReservoirPersistenceSnapshot expected;
        ReservoirPersistenceSnapshot stopSnapshot;

        Probe(MinecraftServer server) {
            this.server = server;
            level = server.overworld();
            directory = server.getServerDirectory().toAbsolutePath().resolve("water-persistence");
        }

        void start() throws IOException {
            check(server.isDedicatedServer(), "Persistence proof requires a normal dedicated server");
            check(Arrays.asList("prepare", "breach", "verify").contains(stage), "Invalid persistence stage");
            Path gameDirectory = server.getServerDirectory().toAbsolutePath().normalize();
            ReservoirPersistenceSnapshot.verifyDirectory(runId, execution, gameDirectory);
            check(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(gameDirectory.resolve("world")),
                    "Persistence profile must own its disposable world directory");
            isolated = true;
            Files.createDirectories(directory);
            WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
            level.setWeatherParameters(0, 20_000_000, false, false);
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
            if ("prepare".equals(stage)) {
                check(!Files.exists(directory.resolve("filled.properties")), "Prepare cannot overwrite an existing checkpoint; use a fresh run ID");
            } else {
                expected = ReservoirPersistenceSnapshot.decode(read(directory.resolve("breach".equals(stage) ? "filled.properties" : "draining.properties")));
                expected.verifyPrevious(runId, "breach".equals(stage) ? "filled" : "draining", execution, level.getSeed());
                check(expected.processId() != ProcessHandle.current().pid(), "Resume did not use a fresh server process");
                check(level.getGameTime() >= expected.gameTime(), "World level metadata did not survive restart");
            }
            ticket(true);
            ModConstants.LOGGER.info("[Water persistence] START stage={} run={} pid={} seed={} tick={}",
                    stage, runId, ProcessHandle.current().pid(), level.getSeed(), level.getGameTime());
        }

        void ticket(boolean add) {
            // Validation alone admits a known disposable fixture. This ticket
            // is ephemeral and never becomes a persisted /forceload entry.
            if (add) level.getChunkSource().addRegionTicket(TICKET, CHUNK, 1, CHUNK, true);
            else level.getChunkSource().removeRegionTicket(TICKET, CHUNK, 1, CHUNK, true);
        }

        void tick() throws IOException {
            check(++ticks < 1400, "Persistence scenario timed out in " + mode + "; loaded="
                    + (level.getChunkSource().getChunkNow(CHUNK.x, CHUNK.z) != null) + ", loads=" + loads + ", unloads=" + unloads);
            check(server.getPlayerCount() == 0, "Disposable persistence server must have no players");
            if ("loading".equals(mode)) {
                if (!ready()) return;
                check(loadedCells != null, "Fixture loaded without an observed chunk-load event");
                if ("prepare".equals(stage)) { build(); mode = "filling"; }
                else {
                    expected.verifyCells(loadedCells);
                    check(awake(expected.cells()) == 0 || loadedWork > 0, "Deserialized awake cells did not rebuild pending work");
                    checkBalance(ReservoirPersistenceSnapshot.FUNDED_UNITS);
                    ModConstants.LOGGER.info("[Water persistence] exact restart load verified stage={} previousPid={} pid={} awake={} pending={}",
                            stage, expected.processId(), ProcessHandle.current().pid(), awake(expected.cells()), loadedWork);
                    if ("breach".equals(stage)) {
                        check(level.getBlockState(ORIGIN.east(3)).is(Blocks.STONE), "Filled checkpoint lost its closed barrier");
                        check(level.setBlock(ORIGIN.east(3), Blocks.AIR.defaultBlockState(), 2), "Could not open persisted barrier");
                        mode = "breaching";
                    } else {
                        var breach = level.getBlockState(ORIGIN.east(3));
                        check(breach.isAir() || WildernessWaterAuthority.isPlainWaterProjection(breach),
                                "Draining checkpoint has an obstructed breach");
                        mode = "resuming";
                    }
                }
                return;
            }
            if ("unloading".equals(mode)) {
                if (level.getChunkSource().getChunkNow(CHUNK.x, CHUNK.z) != null || unloadedCells == null) return;
                check(total(unloadedCells) == ReservoirPersistenceSnapshot.FUNDED_UNITS, "Eviction changed the finite ledger");
                check(ReactiveWaterController.regionAt(level, ORIGIN).cells() == 0, "Eviction retained a physical cache");
                level.getChunkSource().save(true); // Flush normal chunk IO before requesting a new object.
                ticket(true);
                mode = "reloading";
                return;
            }
            if ("reloading".equals(mode)) {
                if (!ready() || loadedCells == null) return;
                check(loadedChunk != unloadedChunk, "Chunk reload reused the evicted runtime object");
                check(Arrays.equals(unloadedCells, loadedCells), "Disk chunk reload changed exact canonical cell fields");
                check(awake(unloadedCells) == 0 || loadedWork > 0, "Disk reload lost pending awake work");
                checkBalance(ReservoirPersistenceSnapshot.FUNDED_UNITS);
                ModConstants.LOGGER.info("[Water persistence] exact disk eviction/reload verified stage={} loads={} unloads={} units={}",
                        stage, loads, unloads, total(loadedCells));
                succeed("prepare".equals(stage) ? "filled" : "drained");
                return;
            }
            checkBalance("filling".equals(mode) ? funded : ReservoirPersistenceSnapshot.FUNDED_UNITS);
            if ("filling".equals(mode)) {
                funded += CanonicalWater.addVolume(level, ORIGIN.above(),
                        Math.min(1024, ReservoirPersistenceSnapshot.FUNDED_UNITS - funded), 0, 0, 0);
                checkBalance(funded);
                check(downstream() == 0, "Closed dam leaked before restart");
                int[] current = levels();
                stable = Arrays.equals(previous, current) ? stable + 1 : 0;
                previous = current;
                if (funded == ReservoirPersistenceSnapshot.FUNDED_UNITS && stable >= 20
                        && ReactiveWaterController.regionAt(level, ORIGIN).cells() > 0
                        && ReactiveWaterController.regionAt(level, ORIGIN.above()).cells() > 0) beginUnload();
            } else if ("breaching".equals(mode)) {
                if (downstream() >= 512 && upstream() > 8192 && physical()) succeed("draining");
            } else if ("resuming".equals(mode)) {
                // No set/schedule/wake calls in this stage: restored work must
                // resume through the ordinary chunk-load and canonical ticker.
                physicalAfterRestart |= physical();
                boolean progressed = downstream() >= storedDownstream(expected.cells()) + 1024
                        && upstream() <= storedUpstream(expected.cells()) - 1024;
                int[] current = levels();
                stable = progressed && Arrays.equals(previous, current) ? stable + 1 : 0;
                previous = current;
                if (stable >= 20 && physicalAfterRestart) beginUnload();
            }
        }

        boolean ready() { return WaterMutationSafety.isReady(level, ORIGIN, 16); }

        boolean physical() { return ReactiveWaterController.regionAt(level, ORIGIN).cells() > 0
                && ReactiveWaterController.regionAt(level, ORIGIN.east(4)).cells() > 0; }

        void beginUnload() {
            if ("prepare".equals(stage) && !metadataUpdateVerified) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(CHUNK.x, CHUNK.z);
                level.getChunkSource().save(true);
                check(!chunk.isUnsaved(), "Fixture was not clean after normal disk flush");
                var block = level.getBlockState(ORIGIN);
                var water = CanonicalWater.get(level, ORIGIN);
                CanonicalWater.set(level, ORIGIN, water.withTemperature(288_000), false, false);
                check(chunk.isUnsaved(), "Canonical-only temperature update did not dirty its owning chunk");
                check(level.getBlockState(ORIGIN).equals(block), "Metadata-only persistence check changed a block");
                checkBalance(ReservoirPersistenceSnapshot.FUNDED_UNITS);
                metadataUpdateVerified = true;
                ModConstants.LOGGER.info("[Water persistence] canonical-only dirty/save check verified");
            }
            unloadedCells = null;
            loadedCells = null;
            ticket(false);
            mode = "unloading";
            ModConstants.LOGGER.info("[Water persistence] releasing fixture ticket stage={} tick={}", stage, level.getGameTime());
        }

        void build() {
            check(total(cells(loadedChunk)) == 0, "Prepare cannot overwrite pre-existing canonical fixture water");
            for (BlockPos position : BlockPos.betweenClosed(ORIGIN.offset(-1, -2, -1), ORIGIN.offset(6, 2, 1))) {
                level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
            }
            for (int x = 0; x <= 2; x++) for (int y = 0; y <= 1; y++) {
                level.setBlock(ORIGIN.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 2);
            }
            for (int x = 4; x <= 5; x++) for (int y = -1; y <= 0; y++) {
                level.setBlock(ORIGIN.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 2);
            }
        }

        int[] levels() {
            int[] result = new int[18];
            for (int i = 0; i < result.length; i++) result[i] = CanonicalWater.get(level, position(i)).volumeUnits();
            return result;
        }

        void checkBalance(int expectedUnits) {
            check(Arrays.stream(levels()).sum() == expectedUnits, "Reservoir conservation mismatch in " + mode);
        }

        int upstream() { return Arrays.stream(levels(), 0, 9).sum(); }
        int downstream() { return Arrays.stream(levels(), 12, 18).sum(); }

        void succeed(String checkpoint) {
            checkpointStage = checkpoint;
            successRequested = true;
            finished = true;
            server.halt(false);
        }

        void fail(Exception error) {
            failure = error.toString();
            finished = true;
            successRequested = false;
            ModConstants.LOGGER.error("[Water persistence] FAILED stage={} mode={}: {}", stage, mode, failure, error);
            server.halt(false);
        }

        void receipt() throws IOException {
            if (!isolated) return;
            Properties report = new Properties();
            boolean passed = successRequested && failure == null && stopSnapshot != null
                    && Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve("level.dat"));
            report.setProperty("status", passed ? "PASS" : "FAIL");
            report.setProperty("stage", stage);
            report.setProperty("run", runId);
            report.setProperty("execution", execution);
            report.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            report.setProperty("units", stopSnapshot == null ? "0" : Integer.toString(total(stopSnapshot.cells())));
            report.setProperty("loads", Integer.toString(loads));
            report.setProperty("unloads", Integer.toString(unloads));
            report.setProperty("automaticResume", Boolean.toString(physicalAfterRestart));
            report.setProperty("metadataOnlyDirty", Boolean.toString(metadataUpdateVerified));
            if (failure != null) report.setProperty("failure", failure);
            write(directory.resolve("receipt-" + stage + ".properties"), report);
            ModConstants.LOGGER.info("[Water persistence] {} stage={} pid={} loads={} unloads={}",
                    report.getProperty("status"), stage, ProcessHandle.current().pid(), loads, unloads);
        }
    }

    private static BlockPos position(int index) { return ORIGIN.offset(index / 3, index % 3 - 1, 0); }

    private static int[] cells(LevelChunk chunk) {
        int[] result = new int[ReservoirPersistenceSnapshot.CELL_COUNT * ReservoirPersistenceSnapshot.FIELDS];
        var volume = chunk.getExistingData(ModAttachments.WATER_VOLUME);
        for (int i = 0; i < ReservoirPersistenceSnapshot.CELL_COUNT; i++) {
            BlockPos position = position(i);
            var cell = volume.map(data -> data.get(position)).orElse(WaterVolumeChunk.WaterCell.EMPTY);
            int start = i * 6;
            result[start] = cell.volumeUnits();
            result[start + 1] = Float.floatToIntBits(cell.velocityX());
            result[start + 2] = Float.floatToIntBits(cell.velocityY());
            result[start + 3] = Float.floatToIntBits(cell.velocityZ());
            result[start + 4] = cell.flags();
            result[start + 5] = cell.temperatureMilliKelvin();
            check(cell.volumeUnits() <= 0 || WildernessWaterAuthority.isPlainWaterProjection(chunk.getBlockState(position(i))),
                    "Persisted positive canonical cell lacks its block projection");
        }
        return result;
    }

    private static int total(int[] data) {
        int result = 0;
        for (int i = 0; i < data.length; i += 6) result += data[i];
        return result;
    }

    private static int awake(int[] data) {
        int count = 0;
        for (int i = 0; i < data.length; i += 6) {
            if (data[i] > 0 && (data[i + 4] & WaterVolumeChunk.FLAG_SLEEPING) == 0) count++;
        }
        return count;
    }

    private static int storedUpstream(int[] data) { int n = 0; for (int i = 0; i < 9; i++) n += data[i * 6]; return n; }
    private static int storedDownstream(int[] data) { int n = 0; for (int i = 12; i < 18; i++) n += data[i * 6]; return n; }

    private static Properties read(Path path) throws IOException {
        check(Files.isRegularFile(path), "Missing prerequisite checkpoint " + path.getFileName());
        check(Files.size(path) <= 65_536, "Oversized persistence checkpoint");
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(path)) { data.load(input); }
        return data;
    }

    private static void write(Path path, Properties data) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream output = Files.newOutputStream(path)) { data.store(output, "Disposable reservoir acceptance evidence; never restore water from this file."); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
