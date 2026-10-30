package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WaterMutationSafety;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericWaterFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
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
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.*;
import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.*;

/**
 * Explicit storm-pulse acceptance scenario for the isolated persistence profile.
 * Existing regional and canonical owners do all evolution; expected NBT is read
 * only for assertions and is never loaded into gameplay state.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class StormRunoffPersistenceProbe {
    private static final String PREFIX = "wildernessodysseyapi.waterPersistence.";
    private static final boolean ENABLED = "storm".equals(System.getProperty(PREFIX + "scenario"));
    private static final ChunkPos SOURCE = new ChunkPos(62_500, 62_500);
    private static final ChunkPos LAKE_CHUNK = new ChunkPos(SOURCE.x + 1, SOURCE.z);
    private static final BlockPos SEED = new BlockPos(SOURCE.getMiddleBlockX(), 131, SOURCE.getMiddleBlockZ());
    private static final BlockPos PROTECTED = SEED.offset(2, 0, 0);
    private static final long INPUT = 134_217_728;
    private static final long M3 = HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wo_storm_runoff_probe",
            Comparator.comparingLong(ChunkPos::toLong));
    private static volatile Probe active;

    private StormRunoffPersistenceProbe() { }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!ENABLED) return;
        active = new Probe(event.getServer());
        try { active.start(); }
        catch (Exception error) { active.fail(error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void diskLoaded(ChunkDataEvent.Load event) {
        Probe probe = active;
        if (probe == null || !"verify".equals(probe.stage) || event.getLevel() != probe.level
                || !(event.getChunk() instanceof LevelChunk chunk) || !chunk.getPos().equals(SOURCE)) return;
        // ChunkDataEvent.Load runs on the IO worker before main-thread chunk
        // activation. Copy only this worker-owned NBT/attachment; publish immutable
        // observations, and leave every assertion and world operation on the server.
        try {
            probe.diskCanonical = event.getData().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                    .getCompound(ModAttachments.WATER_VOLUME.getId().toString()).copy();
            probe.decodedCanonical = canonical(chunk, probe.registries);
        } catch (Exception error) { probe.captureFailure = error.toString(); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void loaded(ChunkEvent.Load event) {
        if (active == null || event.getLevel() != active.level
                || !(event.getChunk() instanceof LevelChunk chunk) || !chunk.getPos().equals(SOURCE)) return;
        try {
            active.loadedCanonical = canonical(chunk, active.registries);
            active.loads++;
        } catch (Exception error) { active.fail(error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (active == null || active.server != event.getServer() || active.finished) return;
        try { active.tick(); }
        catch (Exception error) { active.fail(error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        if (active == null || active.server != event.getServer() || !active.success) return;
        try { active.checkpoint(); }
        catch (Exception error) { active.fail(error); }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (active == null || active.server != event.getServer()) return;
        Probe probe = active;
        active = null;
        try { probe.receipt(); }
        catch (IOException error) { throw new IllegalStateException("Could not write storm acceptance receipt", error); }
    }

    private static final class Probe {
        final MinecraftServer server;
        final ServerLevel level;
        final HolderLookup.Provider registries;
        final String stage = System.getProperty(PREFIX + "stage");
        final String run = System.getProperty(PREFIX + "run");
        final String execution = System.getProperty(PREFIX + "execution");
        final Path directory;
        boolean isolated;
        boolean terrainBuilt;
        boolean ready;
        boolean seeded;
        boolean success;
        boolean finished;
        boolean checkpointWritten;
        boolean automaticResume;
        boolean receded;
        int ticks;
        int loads;
        int stable;
        long peakClaims;
        double peakStage;
        double peakDischarge;
        String failure;
        CompoundTag expected;
        CompoundTag loadedCanonical;
        volatile CompoundTag diskCanonical;
        volatile CompoundTag decodedCanonical;
        volatile String captureFailure;

        Probe(MinecraftServer server) {
            this.server = server;
            level = server.overworld();
            registries = level.registryAccess();
            directory = server.getServerDirectory().toAbsolutePath().normalize().resolve("water-persistence");
        }

        void start() throws IOException {
            Path gameDirectory = directory.getParent();
            check(server.isDedicatedServer() && ("prepare".equals(stage) || "verify".equals(stage)), "Invalid storm dedicated profile");
            check(run != null && run.matches("[a-z0-9-]{1,64}") && execution != null && !execution.isBlank()
                    && gameDirectory.getFileName().toString().equals("run-water-persistence-" + run), "Storm probe requires its isolated profile directory");
            check(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(gameDirectory.resolve("world")), "Unexpected storm world directory");
            isolated = true;
            Files.createDirectories(directory);
            check(RegionalHydrologyConfig.bankfullDepth() == .25 && RegionalHydrologyConfig.evaporation() == 0
                    && !RegionalHydrologyConfig.thermal(), "Storm profile did not load its controlled regional config");
            WaterSimulationConfig.ENABLE_WEATHER_WATER_COUPLING.set(false);
            WaterSimulationConfig.ENABLE_WATERSHED_SIMULATION.set(true);
            WaterSimulationConfig.ENABLE_WATERSHED_GROUNDWATER.set(false);
            WaterSimulationConfig.ENABLE_LOCALIZED_FLOODING.set(true);
            WaterSimulationConfig.ENABLE_RAIN_FED_SURFACE_WATER.set(false);
            WaterSimulationConfig.ENABLE_WATERSHED_SEDIMENT_EFFECTS.set(false);
            WaterSimulationConfig.WATERSHED_CHUNKS_PER_TICK.set(64);
            WaterSimulationConfig.WATERSHED_MAX_TEMPORARY_FLOOD_CELLS.set(128);
            WaterSimulationConfig.FLOOD_MAX_REMOVALS_PER_TICK.set(16);
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
            level.setWeatherParameters(0, 20_000_000, false, false);
            // Faster ordinary ticks shorten the recession experiment; no world
            // clock jump, manual catch-up or extra hydrology ticker is used.
            server.tickRateManager().setTickRate(500);
            Path checkpoint = directory.resolve("storm-active.nbt");
            if ("prepare".equals(stage)) {
                check(!Files.exists(checkpoint), "Storm prepare cannot overwrite an existing checkpoint");
            } else {
                check(Files.isRegularFile(checkpoint), "Missing prerequisite storm-active.nbt");
                expected = NbtIo.readCompressed(checkpoint, NbtAccounter.create(8 * 1024 * 1024));
                check(expected.getInt("schema") == 1 && expected.getString("run").equals(run)
                        && !expected.getString("execution").equals(execution)
                        && expected.getLong("pid") != ProcessHandle.current().pid()
                        && expected.getLong("seed") == level.getSeed()
                        && expected.getLong("gameTime") <= level.getGameTime(), "Storm checkpoint does not match this restart");
                check(expected.getCompound("source").equals(source().save()), "Restart changed exact source region fields");
                check(expected.getCompound("lake").equals(lake().save()), "Restart changed exact lake region fields");
                check(expected.getCompound("atmosphere").equals(data().atmosphereExchange().save()), "Restart changed atmospheric receipts or clocks");
                check(expected.getCompound("claims").equals(ledger().save(new CompoundTag(), level.registryAccess())), "Restart changed funded claim fields");
                peakStage = expected.getDouble("peakStage");
                peakDischarge = expected.getDouble("peakDischarge");
                peakClaims = expected.getLong("peakClaims");
                seeded = true;
                ModConstants.LOGGER.info("[Storm runoff] exact regional/receipt/claim restart verified previousPid={} pid={}",
                        expected.getLong("pid"), ProcessHandle.current().pid());
            }
            // The known fixture's tickets are validation-only and not persisted.
            level.getChunkSource().addRegionTicket(TICKET, SOURCE, 1, SOURCE, true);
            level.getChunkSource().addRegionTicket(TICKET, LAKE_CHUNK, 1, LAKE_CHUNK, true);
            ModConstants.LOGGER.info("[Storm runoff] START stage={} run={} pid={} tick={} targetTps=500",
                    stage, run, ProcessHandle.current().pid(), level.getGameTime());
        }

        void tick() throws IOException {
            check(++ticks < 100_000, "Storm scenario timed out; ready=" + ready + ", terrain=" + terrainBuilt
                    + ", sourceFlood=" + (source() == null ? -1 : source().stored(FLOODPLAIN)));
            check(server.getPlayerCount() == 0, "Storm fixture requires an empty disposable server");
            check(captureFailure == null, "Could not observe the native chunk read: " + captureFailure);
            if (!ready) {
                if (loadedCanonical == null || !WaterMutationSafety.isReady(level, SEED, 16)
                        || !WaterMutationSafety.isReady(level, SEED.east(16), 16)) return;
                if ("prepare".equals(stage)) {
                    if (!terrainBuilt) build();
                    if (source().downstream != LAKE_CHUNK.toLong() || lake().downstream != RegionalHydrologyState.NO_OUTLET) {
                        if (ticks % 2000 == 0) ModConstants.LOGGER.info("[Storm runoff] waiting for topology source={} lake={}", source().downstream, lake().downstream);
                        return;
                    }
                    rain();
                }
                else {
                    if (diskCanonical == null || decodedCanonical == null) return;
                    verifyCanonical(diskCanonical, "disk");
                    verifyCanonical(decodedCanonical, "decoded");
                    check(source().receipt(PRECIPITATION) == INPUT, "Restart lost or duplicated the finite rainfall receipt");
                    ModConstants.LOGGER.info("[Storm runoff] exact canonical restart verified cells={} claimedMilliUnits={}",
                            decodedCanonical.getInt("cell_count"), ledger().ownedMilliUnits(SOURCE.toLong()));
                }
                ready = true;
            }
            if (ticks % 20 != 0) return;
            audit();
            peakStage = Math.max(peakStage, source().stageMetres());
            peakDischarge = Math.max(peakDischarge, source().dischargeCubicMetresPerSecond());
            peakClaims = Math.max(peakClaims, ledger().ownedMilliUnits(SOURCE.toLong()));
            if (!seeded && source().stored(FLOODPLAIN) >= 2 * M3) {
                var view = WatershedSavedData.get(level).state(SOURCE.toLong());
                check(TemporaryFloodManager.safeTemporaryWaterTarget(level, chunk(SOURCE), SEED), "Unsafe storm seed target");
                check(TemporaryFloodManager.placeTrackedSurfaceWater(level, WatershedSavedData.get(level), SOURCE.toLong(),
                        view, SEED, SurfaceWaterKind.FLOOD, 4096, 0, 0), "Could not bootstrap one exactly funded flood cell");
                seeded = true;
                audit();
            }
            if ("prepare".equals(stage)) {
                if (fundedExpansion() && ledger().ownedMilliUnits(SOURCE.toLong()) > 0
                        && source().stored(FLOODPLAIN) > 0 && source().receipt(DOWNSTREAM) > 0 && lake().stored(LAKE) > 0) succeed();
            } else {
                automaticResume |= source().receipt(DOWNSTREAM) > expected.getCompound("source").getLongArray("receipts")[DOWNSTREAM.ordinal()]
                        && lake().stored(LAKE) > expected.getCompound("lake").getLongArray("stores")[LAKE.ordinal()];
                receded = source().stored(FLOODPLAIN) == 0 && ledger().ownedMilliUnits(SOURCE.toLong()) == 0
                        && source().receipt(PROJECTION_IN) == source().receipt(PROJECTION_OUT)
                        && canonicalUnits() == 0;
                stable = receded && automaticResume && source().dischargeCubicMetresPerSecond() < peakDischarge ? stable + 1 : 0;
                if (stable >= 20) succeed();
            }
            if (ticks % 10_000 == 0) ModConstants.LOGGER.info("[Storm runoff] progress tick={} flood={} claims={} downstream={} lake={}",
                    level.getGameTime(), source().stored(FLOODPLAIN), ledger().ownedMilliUnits(SOURCE.toLong()),
                    source().receipt(DOWNSTREAM), lake().stored(LAKE));
        }

        void build() {
            check(data().regions().values().stream().mapToLong(RegionalHydrologyState::totalStored).sum() == 0
                    && ledger().size() == 0 && canonicalUnits() == 0, "Cannot overwrite an already wet storm fixture");
            for (var region : data().regions().values()) {
                for (long receipt : region.receipts) check(receipt == 0, "Cannot replace committed regional receipts");
            }
            for (Tag saved : data().atmosphereExchange().save().getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) saved;
                check(entry.getLong("rain") == 0 && entry.getLong("et") == 0, "Cannot replace committed atmospheric receipts");
                for (long bits : entry.getLongArray("pending")) check(Double.longBitsToDouble(bits) == 0, "Cannot discard pending rainfall");
            }
            // Native chunk readiness admits a wider terrain ring than the ticket
            // footprint. Bound this dry test catalogue before adding the two
            // loaded catchment nodes, using the existing minimum admission cap.
            // Retain actual dry spawn nodes, rather than inventing terrain nodes.
            check(RegionalHydrologyConfig.maximumRegions() == 64, "Unexpected storm admission cap");
            var bounded = new RegionalHydrologySavedData();
            for (var retained : data().regions().values()) {
                if (bounded.size() == 62) break;
                if (Math.abs(ChunkPos.getX(retained.key) - SOURCE.x) < 16
                        || Math.abs(ChunkPos.getZ(retained.key) - SOURCE.z) < 16) continue;
                check(retained.receipt(PRECIPITATION) == 0 && retained.receipt(PROJECTION_OUT) == 0,
                        "Cannot replace a previously funded storm catalogue");
                bounded.admit(retained, 64);
            }
            check(bounded.size() == 62, "Missing dry native spawn records for the bounded fixture");
            level.getDataStorage().set(ModConstants.MOD_ID + "_regional_water", bounded);
            // Reserve the two admitted slots before terrain callbacks can promote
            // additional native chunks around the construction footprint.
            for (ChunkPos position : new ChunkPos[]{SOURCE, LAKE_CHUNK}) {
                RegionalHydrologyManager.onChunkLoad(level, chunk(position), WatershedSavedData.get(level).getOrCreate(level, chunk(position)));
                check(data().state(position.toLong()) != null, "Could not admit a loaded catchment node before construction");
            }
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) {
                ChunkPos position = new ChunkPos(SOURCE.x + x, SOURCE.z + z);
                LevelChunk chunk = chunk(position);
                check(chunk != null, "Storm fixture halo was not loaded");
                int floor = position.equals(SOURCE) ? 130 : position.equals(LAKE_CHUNK) ? 128 : 140;
                for (int localX = 0; localX < 16; localX++) for (int localZ = 0; localZ < 16; localZ++) {
                    BlockPos ground = new BlockPos(chunk.getPos().getMinBlockX() + localX, floor, chunk.getPos().getMinBlockZ() + localZ);
                    level.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
                    if (position.equals(SOURCE) && (localX == 0 || localX == 15 || localZ == 0 || localZ == 15)) {
                        for (int height = 1; height <= 3; height++) level.setBlock(ground.above(height), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
                var feature = position.equals(SOURCE) ? WatershedConditions.WaterFeature.RIVER
                        : position.equals(LAKE_CHUNK) ? WatershedConditions.WaterFeature.LAKE : WatershedConditions.WaterFeature.NONE;
                WatershedSavedData.get(level).store(position.toLong(), WatershedChunkState.create(position.toLong(), floor,
                        WatershedConditions.DrainageDirection.EAST, feature, 1, position.equals(SOURCE) ? SEED.asLong()
                                : WatershedChunkState.NO_REPRESENTATIVE, .88f, level.getGameTime()));
                if (position.equals(SOURCE) || position.equals(LAKE_CHUNK)) {
                    RegionalHydrologyManager.onChunkLoad(level, chunk, WatershedSavedData.get(level).state(position.toLong()));
                    var region = data().state(position.toLong());
                    check(region != null, "Normal chunk admission missed a storm terrain node");
                    region.elevation = region.spillElevation = floor;
                    region.feature = feature;
                    region.ocean = false;
                    region.soil = SoilHydrologyModel.SoilProfile.IMPERMEABLE;
                    region.vegetation = 0;
                }
            }
            level.setBlock(PROTECTED, Blocks.STONE.defaultBlockState(), 2);
            data().terrainChanged();
            terrainBuilt = true;
        }

        void rain() {
            AtmosphericWaterExchange.ReceiptBook book = data().atmosphereExchange();
            book.publish(SOURCE.toLong(), 0, 0, 0, 256, level.getGameTime());
            var cell = AtmosphereCellKey.fromBlock(SEED.getX(), SEED.getZ(), 16);
            var pulse = new AtmosphericWaterFlux(2, 0, 0, 0, 128, 0, 0, 0, 0, 0, 0);
            check(book.publishPrecipitation(cell, 16, pulse, level.getGameTime()), "Storm receipt was not accepted");
            check(book.applyPrecipitation(source()) == INPUT && book.applyPrecipitation(source()) == 0, "Storm receipt was lost or applied twice");
            data().setDirty();
            ModConstants.LOGGER.info("[Storm runoff] measured input=128mm area=256m2 milliUnits={}", INPUT);
        }

        void audit() {
            long regional = 0;
            long rainfall = 0;
            for (var state : data().regions().values()) {
                check(state.residualMilliUnits() == 0, "Nonzero regional residual at " + state.key);
                regional = Math.addExact(regional, state.totalStored());
                rainfall = Math.addExact(rainfall, state.receipt(PRECIPITATION));
            }
            long detailed = canonicalUnits() * 1000L;
            check(regional + detailed == INPUT && rainfall == INPUT, "Storm combined inventory or rainfall receipt changed");
            check(detailed == ledger().ownedMilliUnits(SOURCE.toLong())
                    && detailed == source().receipt(PROJECTION_OUT) - source().receipt(PROJECTION_IN), "Canonical funding and projection receipts disagree");
            check(source().receipt(DOWNSTREAM) == lake().receipt(UPSTREAM), "River transfer receipts disagree");
            check(source().downstream == LAKE_CHUNK.toLong() && lake().downstream == RegionalHydrologyState.NO_OUTLET,
                    "Cached drainage graph did not route the known catchment into its closed lake");
            check(level.getBlockState(PROTECTED).is(Blocks.STONE), "Flooding replaced protected solid terrain");
        }

        long canonicalUnits() {
            long total = 0;
            for (ChunkPos position : new ChunkPos[]{SOURCE, LAKE_CHUNK}) {
                LevelChunk chunk = chunk(position);
                if (chunk == null) continue;
                var volume = chunk.getExistingData(ModAttachments.WATER_VOLUME);
                if (volume.isEmpty()) continue;
                for (var entry : volume.get().snapshot()) {
                    var cell = entry.cell();
                    if (cell.volumeUnits() <= 0) continue;
                    BlockPos point = WaterVolumeChunk.unpack(position.x, position.z, entry.packedPosition());
                    check(cell.temporaryFlood() && ledger().ownedUnits(point.asLong()) == cell.volumeUnits()
                            && ledger().fundingRegion(point.asLong()) == SOURCE.toLong()
                            && WildernessWaterAuthority.isPlainWaterProjection(chunk.getBlockState(point)), "Detailed flood cell lost its exact funding/projection");
                    total += cell.volumeUnits();
                }
            }
            return total;
        }

        boolean fundedExpansion() { return source().receipt(PROJECTION_OUT) > M3; }

        void verifyCanonical(CompoundTag observed, String boundary) throws IOException {
            var prior = expected.getCompound("canonical");
            if (prior.equals(observed)) return;
            NbtIo.writeCompressed(observed, directory.resolve("storm-observed-" + boundary + ".nbt"));
            int[] priorCells = prior.getIntArray("cells");
            int[] observedCells = observed.getIntArray("cells");
            int difference = 0;
            while (difference < Math.min(priorCells.length, observedCells.length)
                    && priorCells[difference] == observedCells[difference]) difference++;
            throw new IllegalStateException("Restart changed canonical " + boundary + ": expectedCount=" + prior.getInt("cell_count")
                    + ", observedCount=" + observed.getInt("cell_count") + ", expectedRevision=" + prior.getLong("revision")
                    + ", observedRevision=" + observed.getLong("revision") + ", firstCellField=" + difference
                    + ", expected=" + (difference < priorCells.length ? priorCells[difference] : "end")
                    + ", observed=" + (difference < observedCells.length ? observedCells[difference] : "end"));
        }
        RegionalHydrologySavedData data() { return RegionalHydrologySavedData.get(level); }
        RegionalHydrologyState source() { return data().state(SOURCE.toLong()); }
        RegionalHydrologyState lake() { return data().state(LAKE_CHUNK.toLong()); }
        TemporaryFloodSavedData ledger() { return TemporaryFloodSavedData.get(level); }
        LevelChunk chunk(ChunkPos position) { return level.getChunkSource().getChunkNow(position.x, position.z); }

        void succeed() { success = true; finished = true; server.halt(false); }

        void fail(Exception error) {
            failure = error.toString();
            success = false;
            finished = true;
            ModConstants.LOGGER.error("[Storm runoff] FAILED stage={}: {}", stage, failure, error);
            server.halt(false);
        }

        void checkpoint() throws IOException {
            audit();
            check(fundedExpansion() && peakStage > .25, "Storm never produced funded channel overflow");
            if ("prepare".equals(stage)) check(source().stored(FLOODPLAIN) > 0 && ledger().ownedMilliUnits(SOURCE.toLong()) > 0,
                    "Storm checkpoint is not actively flooding");
            CompoundTag snapshot = new CompoundTag();
            snapshot.putInt("schema", 1);
            snapshot.putString("run", run);
            snapshot.putString("execution", execution);
            snapshot.putLong("pid", ProcessHandle.current().pid());
            snapshot.putLong("seed", level.getSeed());
            snapshot.putLong("gameTime", level.getGameTime());
            snapshot.putDouble("peakStage", peakStage);
            snapshot.putDouble("peakDischarge", peakDischarge);
            snapshot.putLong("peakClaims", peakClaims);
            snapshot.put("source", source().save());
            snapshot.put("lake", lake().save());
            snapshot.put("atmosphere", data().atmosphereExchange().save());
            snapshot.put("claims", ledger().save(new CompoundTag(), level.registryAccess()));
            snapshot.put("canonical", canonical(chunk(SOURCE), registries));
            NbtIo.writeCompressed(snapshot, directory.resolve("prepare".equals(stage) ? "storm-active.nbt" : "storm-receded.nbt"));
            checkpointWritten = true;
            ModConstants.LOGGER.info("[Storm runoff] checkpoint={} pid={} tick={} stored={} detailed={} lake={} returns={} residual=0",
                    stage, ProcessHandle.current().pid(), level.getGameTime(), data().regions().values().stream().mapToLong(RegionalHydrologyState::totalStored).sum(),
                    canonicalUnits() * 1000, lake().stored(LAKE), source().receipt(PROJECTION_IN));
        }

        void receipt() throws IOException {
            if (!isolated) return;
            Properties report = new Properties();
            boolean passed = success && failure == null && checkpointWritten
                    && Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve("level.dat"));
            report.setProperty("status", passed ? "PASS" : "FAIL");
            report.setProperty("scenario", "storm");
            report.setProperty("stage", stage);
            report.setProperty("run", run);
            report.setProperty("execution", execution);
            report.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            report.setProperty("milliUnits", passed ? Long.toString(INPUT) : "0");
            report.setProperty("fundedExpansion", Boolean.toString(passed && fundedExpansion()));
            report.setProperty("automaticResume", Boolean.toString(automaticResume));
            report.setProperty("receded", Boolean.toString(receded));
            report.setProperty("loads", Integer.toString(loads));
            if (failure != null) report.setProperty("failure", failure);
            try (OutputStream output = Files.newOutputStream(directory.resolve("receipt-" + stage + ".properties"))) {
                report.store(output, "Disposable storm acceptance evidence; never restore water from this file.");
            }
            ModConstants.LOGGER.info("[Storm runoff] {} stage={} pid={} resume={} receded={}",
                    report.getProperty("status"), stage, ProcessHandle.current().pid(), automaticResume, receded);
        }
    }

    private static CompoundTag canonical(LevelChunk chunk, HolderLookup.Provider registries) {
        return chunk.getExistingData(ModAttachments.WATER_VOLUME).map(data -> data.serializeNBT(registries)).orElseGet(CompoundTag::new);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
