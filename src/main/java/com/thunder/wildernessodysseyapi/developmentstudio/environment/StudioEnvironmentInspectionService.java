package com.thunder.wildernessodysseyapi.developmentstudio.environment;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionLine;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.WeatherForecast;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterBody;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSimulationDiagnostics;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Read-only adapters from real Water, ecosystem, Weather, and worldgen owners into Studio rows. */
public final class StudioEnvironmentInspectionService {
    private static final int ECOSYSTEM_INSPECTION_RADIUS = 32;
    private static final int MAX_ECOSYSTEM_MOBS = 128;

    private StudioEnvironmentInspectionService() {
    }

    /** Samples the singular custom-water authority and its real generated/hydrologic diagnostics. */
    public static StudioInspection water(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        WaterAccess water = WaterServices.access();
        List<StudioInspectionLine> lines = base(level, position);
        WildernessWaterRules.ModeStatus mode = WildernessWaterRules.status(level);
        lines.add(line("Water API", "v" + WaterServices.apiVersion()));
        lines.add(line("Authority mode", "active=" + mode.active() + ", gamerule=" + mode.gameRule()
                + ", startupConfig=" + mode.startupConfig()));
        lines.add(line("Authority water", Boolean.toString(water.isWaterAt(level, position))));
        lines.add(line("Units at block", Long.toString(water.getWaterUnits(level, position))));
        lines.add(line("Surface Y", decimalOrDry(water.getSurfaceHeight(
                level, position.getX() + 0.5D, position.getZ() + 0.5D))));
        lines.add(line("Depth at player", number(water.getDepth(level, player.position()))));
        lines.add(line("Current", vector(water.getCurrent(level, player.position()))));
        lines.add(line("Can accept volume", Boolean.toString(water.canAddWater(level, position))));

        Optional<WaterBody> body = water.getWaterBody(level, position);
        lines.add(line("Water body", body.map(value -> value.kind() + " region=" + value.regionKey())
                .orElse("none")));
        body.ifPresent(value -> lines.add(line("Body volume/current",
                value.estimatedVolumeUnits() + " units; " + vector(value.current()))));

        WatershedConditions watershed = water.getWatershedConditions(level, position);
        lines.add(line("Watershed", "basin=" + watershed.basinId() + ", feature=" + watershed.waterFeature()
                + ", downstream=" + watershed.downstreamDirection()));
        lines.add(line("Hydrology", "rain=" + number(watershed.recentRainfall())
                + ", snowmelt=" + number(watershed.recentSnowmelt())
                + ", storage=" + number(watershed.aquiferStorage())));
        lines.add(line("Discharge/flood", number(watershed.riverDischarge()) + " / "
                + number(watershed.floodRisk()) + " flooding=" + watershed.flooding()));
        WatershedLocalFlow flow = water.getLocalWatershedFlow(level, position);
        lines.add(line("Local flow", "cell=" + flow.cell() + ", " + flow.direction()
                + ", contributors=" + flow.contributingCells() + ", speed=" + number(flow.currentStrength())));

        LevelChunk chunk = level.getChunkAt(position);
        GeneratedWaterChunk generated = chunk.getExistingData(ModAttachments.GENERATED_WATER).orElse(null);
        if (generated == null) {
            lines.add(line("Generated attachment", "absent on current server chunk"));
        } else {
            GeneratedWaterChunk.WaterSpan span = generated.spanAt(position);
            GeneratedWaterChunk.WaterSpan top = generated.topSpan(position.getX() & 15, position.getZ() & 15);
            lines.add(line("Generated attachment", "revision=" + generated.revision()
                    + ", spans=" + generated.spanCount() + ", bytes~" + generated.estimatedBytes()));
            lines.add(line("Generated span", span == null ? "none at sampled block" : spanText(span)));
            lines.add(line("Column top span", top == null ? "none" : spanText(top)));
        }
        WatershedSimulationDiagnostics.Snapshot diagnostics = WatershedSimulationDiagnostics.snapshot(level);
        lines.add(line("Watershed tick", "queued=" + diagnostics.queuedChunks()
                + ", processed=" + diagnostics.processedChunks()
                + ", floods=" + diagnostics.activeFloodCells()
                + ", " + diagnostics.elapsedMicros() + " us"));
        return inspection("water", "Water Debug - Real Authority", lines);
    }

    /** Reports loaded ecosystem profiles, budget use, and nearby real profiled animals. */
    public static StudioInspection ecosystem(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        List<StudioInspectionLine> lines = base(level, position);
        lines.add(line("Loaded profiles", SpeciesBehaviorProfileManager.profiles().size()
                + " JSON, " + SpeciesBehaviorProfileManager.configuredProfiles().size()
                + " configured, " + SpeciesBehaviorProfileManager.autoDetectedProfiles().size() + " inferred"));

        AABB bounds = new AABB(position).inflate(ECOSYSTEM_INSPECTION_RADIUS);
        List<ProfiledMob> profiled = new ArrayList<>();
        List<PathfinderMob> nearby = level.getEntitiesOfClass(PathfinderMob.class, bounds).stream()
                .sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)))
                .limit(MAX_ECOSYSTEM_MOBS)
                .toList();
        for (PathfinderMob mob : nearby) {
            SpeciesBehaviorProfileManager.profileFor(mob)
                    .ifPresent(profile -> profiled.add(new ProfiledMob(mob, profile)));
        }
        lines.add(line("Bounded sample", nearby.size() + " pathfinding mobs checked within "
                + ECOSYSTEM_INSPECTION_RADIUS + " blocks"));
        lines.add(line("Profiled mobs", Integer.toString(profiled.size())));

        if (!profiled.isEmpty()) {
            ProfiledMob nearest = profiled.getFirst();
            PathfinderMob mob = nearest.mob();
            lines.add(line("Nearest profiled mob", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                    + " at " + mob.blockPosition().toShortString()));
            lines.add(line("Profile", nearest.profile().id().toString()));
            AnimalNeedsState needs = mob.getExistingData(ModAttachments.ANIMAL_NEEDS).orElse(null);
            if (needs == null) {
                lines.add(line("Needs state", "attachment not initialized"));
            } else {
                lines.add(line("Behavior", needs.behavior() + " target=" + position(needs.behaviorTarget())));
                lines.add(line("Needs", "thirst=" + number(needs.thirst()) + ", hunger=" + number(needs.hunger())
                        + ", rest=" + number(needs.rest()) + ", social=" + number(needs.social())));
                lines.add(line("Safety/evaluation", number(needs.safetyConcern()) + "; last="
                        + needs.lastEvaluatedAt() + ", next=" + needs.nextEvaluationAt()
                        + ", " + needs.lastEvaluationNanos() / 1_000L + " us"));
            }
        }
        var budget = EcosystemServices.budget().snapshot(level);
        lines.add(line("Update budget", budget.used() + "/" + budget.limit()
                + " used; denied=" + budget.denied() + "; tick=" + budget.tick()));
        lines.add(line("Simulation stepping", "deferred; global tick rate is never changed"));
        return inspection("ecosystem", "Ecosystem - Bounded Live Inspection", lines);
    }

    /** Samples the stable weather query plus existing cell and forecast diagnostics. */
    public static StudioInspection weather(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        List<StudioInspectionLine> lines = base(level, position);
        WeatherSample sample = WeatherServices.query().sample(level, position);
        lines.add(line("Conditions", sample.precipitationType() + " "
                + number(sample.precipitationIntensity()) + "; thunder=" + number(sample.thunderIntensity())));
        lines.add(line("Temperature/humidity", number(sample.temperature()) + " C / " + number(sample.humidity())));
        lines.add(line("Pressure", number(sample.pressure())));
        lines.add(line("Wind", number(sample.wind().x()) + ", " + number(sample.wind().z())
                + " speed=" + number(sample.wind().magnitude())));
        lines.add(line("Cloud", sample.cloudType() + "; water=" + number(sample.cloudWater())
                + ", depth=" + number(sample.cloudDepth())));
        lines.add(line("Storm", sample.stormStage() + "; energy=" + number(sample.stormEnergy())
                + ", instability=" + number(sample.instability())));
        lines.add(line("Surface", "wetness=" + number(sample.surface().wetness())
                + ", puddles=" + number(sample.surface().puddleCoverage())
                + ", snow=" + number(sample.surface().snowpack())));

        WeatherAuthority authority = WeatherAuthority.get();
        AtmosphereView cell = authority.cellAt(level, position);
        lines.add(line("Atmosphere cell", cell == null ? "disabled/unavailable"
                : cell.key().x() + "," + cell.key().z() + " rev=" + cell.revision()
                + " simulated=" + cell.lastSimulatedTick()));
        WeatherForecast forecast = authority.forecast(level, position);
        lines.add(line("Forecast", forecast.currentPhenomenon() + " " + number(forecast.currentIntensity())
                + "; pressure " + forecast.pressureTendency()));
        lines.add(line("Approaching system", forecast.approachingSystem() == null ? "none"
                : forecast.approachingSystem() + " in " + number(forecast.distanceBlocks())
                + " blocks; confidence=" + number(forecast.confidence())));
        lines.add(line("Tracked systems", Integer.toString(authority.systems(level).size())));
        lines.add(line("Control scope", "Clear/rain/snow/hail use the authority's local 3x3 cells"));
        return inspection("weather", "Weather - Local Authority", lines);
    }

    /** Inspects only the loaded current chunk and current-position structure starts. */
    public static StudioInspection worldgen(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(position);
        LevelChunk chunk = level.getChunkAt(position);
        List<StudioInspectionLine> lines = base(level, position);
        ResourceLocation biome = level.getBiome(position).unwrapKey()
                .map(key -> key.location())
                .orElse(ResourceLocation.withDefaultNamespace("unknown"));
        lines.add(line("Seed", Long.toString(level.getSeed())));
        lines.add(line("Chunk/region", chunkPos.x + "," + chunkPos.z + " / "
                + chunkPos.getRegionX() + "," + chunkPos.getRegionZ()));
        lines.add(line("Chunk status", chunk.getPersistedStatus().getName()));
        lines.add(line("Biome", biome.toString()));
        lines.add(line("Surface Y", Integer.toString(level.getHeight(
                Heightmap.Types.WORLD_SURFACE, position.getX(), position.getZ()))));
        lines.add(line("Sea/build height", level.getSeaLevel() + " / "
                + level.getMinBuildHeight() + ".." + (level.getMaxBuildHeight() - 1)));
        lines.add(line("Chunk generator", level.getChunkSource().getGenerator().getClass().getName()));
        lines.add(line("Biome source", level.getChunkSource().getGenerator().getBiomeSource().getClass().getName()));

        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<ResourceLocation> present = structures.entrySet().stream()
                .filter(entry -> level.structureManager().getStructureAt(position, entry.getValue()).isValid())
                .map(entry -> entry.getKey().location())
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .limit(16)
                .toList();
        lines.add(line("Structures at block", present.isEmpty() ? "none" : present.toString()));
        lines.add(line("Discovery scope", "current loaded chunk only; no chunk generation or broad scan"));
        return inspection("worldgen", "Worldgen - Current Loaded Chunk", lines);
    }

    private static List<StudioInspectionLine> base(ServerLevel level, BlockPos position) {
        List<StudioInspectionLine> lines = new ArrayList<>();
        lines.add(line("Dimension", level.dimension().location().toString()));
        lines.add(line("Position", position.toShortString()));
        return lines;
    }

    private static StudioInspection inspection(String path, String title, List<StudioInspectionLine> lines) {
        return new StudioInspection(
                ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path), title, lines
        );
    }

    private static StudioInspectionLine line(String label, String value) {
        return new StudioInspectionLine(label, value);
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }

    private static String decimalOrDry(double value) {
        return Double.isFinite(value) ? number(value) : "dry";
    }

    private static String vector(Vec3 value) {
        return number(value.x) + ", " + number(value.y) + ", " + number(value.z);
    }

    private static String spanText(GeneratedWaterChunk.WaterSpan span) {
        return span.bottomY() + ".." + span.topY() + ", units=" + span.amountUnits()
                + ", type=" + span.cell().bodyType();
    }

    private static String position(BlockPos value) {
        return value == null ? "none" : value.toShortString();
    }

    private record ProfiledMob(PathfinderMob mob, SpeciesBehaviorProfile profile) {
    }
}
