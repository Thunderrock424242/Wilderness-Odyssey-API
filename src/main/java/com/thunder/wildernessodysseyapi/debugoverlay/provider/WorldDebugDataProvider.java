package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import com.thunder.wildernessodysseyapi.ecosystem.client.EnvironmentalMemoryClientState;
import com.thunder.wildernessodysseyapi.ecosystem.network.EnvironmentalMemoryDebugPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Collects client-safe position, light, chunk, biome, and world state. */
public final class WorldDebugDataProvider implements DebugDataProvider {
    @Override
    public List<DebugSection> collect(DebugContext context) {
        return detailed(context);
    }

    /** Returns the compact world section used by the General page. */
    public List<DebugSection> summary(DebugContext context) {
        Minecraft minecraft = context.minecraft();
        if (minecraft.level == null) {
            return List.of(DebugSection.builder("WORLD")
                    .add("State", DebugValue.unavailable("No world loaded"))
                    .build());
        }

        Entity camera = DebugProviderSupport.camera(minecraft);
        BlockPos pos = DebugProviderSupport.cameraBlockPos(minecraft);
        ChunkPos chunk = new ChunkPos(pos);
        int rawLight = minecraft.level.getChunkSource().getLightEngine().getRawBrightness(pos, 0);

        return List.of(DebugSection.builder("WORLD")
                .add("XYZ", DebugProviderSupport.precisePosition(camera))
                .add("Block XYZ", DebugProviderSupport.blockPosition(pos))
                .add("Facing", DebugProviderSupport.shortFacing(camera))
                .add("Biome", DebugProviderSupport.biomeId(minecraft, pos))
                .add("Dimension", minecraft.level.dimension().location())
                .add("Chunk", chunk.x + " / " + chunk.z)
                .add("Region", chunk.getRegionX() + " / " + chunk.getRegionZ())
                .add("Light", rawLight)
                .add("Day", minecraft.level.getDayTime() / 24_000L)
                .add("Time", DebugProviderSupport.ticksAsClock(minecraft.level.getDayTime()))
                .build());
    }

    /** Returns the full World page data set. */
    public List<DebugSection> detailed(DebugContext context) {
        Minecraft minecraft = context.minecraft();
        if (minecraft.level == null) {
            return List.of(DebugSection.builder("WORLD")
                    .add("State", DebugValue.unavailable("No world loaded"))
                    .build());
        }

        Entity camera = DebugProviderSupport.camera(minecraft);
        BlockPos pos = DebugProviderSupport.cameraBlockPos(minecraft);
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk chunk = minecraft.level.getChunkSource()
                .getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);

        List<DebugSection> sections = new ArrayList<>();
        sections.add(DebugSection.builder("POSITION")
                .add("XYZ", DebugProviderSupport.precisePosition(camera))
                .add("Block XYZ", DebugProviderSupport.blockPosition(pos))
                .add("Chunk relative", (pos.getX() & 15) + " / " + (pos.getY() & 15) + " / " + (pos.getZ() & 15))
                .add("Chunk", chunkPos.x + " / " + chunkPos.z)
                .add("Chunk section", chunkPos.x + " / " + (pos.getY() >> 4) + " / " + chunkPos.z)
                .add("Region", chunkPos.getRegionX() + " / " + chunkPos.getRegionZ())
                .add("Region file", "r." + chunkPos.getRegionX() + "." + chunkPos.getRegionZ() + ".mca")
                .add("Facing", DebugProviderSupport.facing(camera))
                .build());

        sections.add(DebugSection.builder("ENVIRONMENT")
                .add("Dimension", minecraft.level.dimension().location())
                .add("Biome", DebugProviderSupport.biomeId(minecraft, pos))
                .add("Difficulty", minecraft.level.getDifficulty().getKey())
                .add("Day", minecraft.level.getDayTime() / 24_000L)
                .add("Time", DebugProviderSupport.ticksAsClock(minecraft.level.getDayTime()))
                .add("Game time", minecraft.level.getGameTime() + " ticks")
                .add("Tick rate", String.format(Locale.ROOT, "%.1f TPS / %.1f ms target",
                        minecraft.level.tickRateManager().tickrate(),
                        minecraft.level.tickRateManager().millisecondsPerTick()))
                .build());

        int rawLight = minecraft.level.getChunkSource().getLightEngine().getRawBrightness(pos, 0);
        int skyLight = minecraft.level.getBrightness(LightLayer.SKY, pos);
        int blockLight = minecraft.level.getBrightness(LightLayer.BLOCK, pos);
        sections.add(DebugSection.builder("LIGHT")
                .add("Client raw", rawLight)
                .add("Sky", skyLight)
                .add("Block", blockLight)
                .add("Server", DebugValue.unavailable("Not synchronized separately"))
                .build());

        DebugSection.Builder chunkSection = DebugSection.builder("CHUNK")
                .add("Client loaded chunks", minecraft.level.getChunkSource().getLoadedChunksCount())
                .add("Chunk source", minecraft.level.gatherChunkSourceStats());
        if (chunk == null || chunk.isEmpty()) {
            chunkSection.add("Status", DebugValue.unavailable("Waiting for client chunk"));
        } else {
            long inhabitedTicks = chunk.getInhabitedTime();
            float moonBrightness = minecraft.level.getMoonBrightness();
            DifficultyInstance localDifficulty = new DifficultyInstance(
                    minecraft.level.getDifficulty(), minecraft.level.getDayTime(), inhabitedTicks, moonBrightness
            );
            chunkSection
                    .add("Status", chunk.getPersistedStatus().getName())
                    .add("Inhabited", String.format(Locale.ROOT, "%d ticks (%.1f h)",
                            inhabitedTicks, inhabitedTicks / 72_000.0D))
                    .add("Local difficulty", String.format(Locale.ROOT, "%.2f / %.2f",
                            localDifficulty.getEffectiveDifficulty(), localDifficulty.getSpecialMultiplier()))
                    .add("Old generation", chunk.isOldNoiseGeneration() ? DebugValue.warning("Yes") : DebugValue.normal("No"));
        }
        sections.add(chunkSection.build());

        Optional<EnvironmentalMemoryDebugPayload> memory = EnvironmentalMemoryClientState.current(
                minecraft.level.dimension().location(), chunkPos);
        if (memory.isEmpty()) {
            sections.add(DebugSection.builder("ENVIRONMENTAL MEMORY")
                    .add("State", DebugValue.unavailable("Enable ecosystem debug commands for server data"))
                    .build());
        } else if (!memory.get().present()) {
            sections.add(DebugSection.builder("ENVIRONMENTAL MEMORY")
                    .add("Cell", memory.get().chunkX() + " / " + memory.get().chunkZ())
                    .add("Disturbance", "0.000 (no stored activity)")
                    .add("Stored cells", memory.get().activeCellCount())
                    .build());
        } else {
            EnvironmentalMemoryDebugPayload snapshot = memory.get();
            sections.add(DebugSection.builder("ENVIRONMENTAL MEMORY")
                    .add("Cell", snapshot.chunkX() + " / " + snapshot.chunkZ())
                    .add("Disturbance", decimal(snapshot.disturbance()))
                    .add("Player traffic", decimal(snapshot.playerTraffic()))
                    .add("Combat / fire", decimal(snapshot.combatActivity()) + " / " + decimal(snapshot.fireActivity()))
                    .add("Last source", snapshot.lastSource())
                    .add("Source position", snapshot.sourceX() + " / " + snapshot.sourceY() + " / " + snapshot.sourceZ())
                    .add("Last update", snapshot.lastUpdatedGameTime() + " (" + snapshot.elapsedTicks() + " ticks ago)")
                    .add("Lazy decay", decimal(snapshot.decayApplied()))
                    .add("Stored cells", snapshot.activeCellCount())
                    .build());
        }

        if (chunk != null && !chunk.isEmpty()) {
            DebugSection.Builder heightmaps = DebugSection.builder("CLIENT HEIGHTMAPS");
            for (Heightmap.Types type : Heightmap.Types.values()) {
                if (type.sendToClient()) {
                    heightmaps.add(type.getSerializationKey(), chunk.getHeight(type, pos.getX(), pos.getZ()));
                }
            }
            sections.add(heightmaps.build());
        }

        String serverBrand = minecraft.getConnection() == null
                ? "N/A"
                : minecraft.getConnection().serverBrand();
        sections.add(DebugSection.builder("SESSION")
                .add("Game mode", minecraft.gameMode == null ? DebugValue.unavailable() : minecraft.gameMode.getPlayerMode().getName())
                .add("Server brand", serverBrand)
                .add("Reduced debug", minecraft.showOnlyReducedInfo() ? DebugValue.warning("Enabled") : DebugValue.normal("Disabled"))
                .build());
        return List.copyOf(sections);
    }

    private static String decimal(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
