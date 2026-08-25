package com.thunder.wildernessodysseyapi.ecosystem.debug.map;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeForm;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Bounded, server-authored regional wildlife snapshot used only by the
 * development ecosystem map.
 *
 * <p>The packet contains existing coarse cells and persisted distant groups.
 * It never transfers live entities, chunks, registries, or mutable owner
 * storage.</p>
 */
public record EcosystemDebugMapPayload(
        ResourceLocation dimension,
        int dataVersion,
        long serverGameTime,
        int playerBlockX,
        int playerBlockZ,
        int centerCellX,
        int centerCellZ,
        int cellSize,
        int radiusCells,
        boolean ecosystemEnabled,
        boolean distantWildlifeEnabled,
        boolean populationEcologyEnabled,
        int regionalCarryingCapacity,
        List<CellSnapshot> cells,
        List<GroupSnapshot> groups
) implements CustomPacketPayload {
    public static final int DATA_VERSION = 1;
    public static final int MAXIMUM_RADIUS_CELLS = 12;
    public static final int MAXIMUM_CELLS = square(MAXIMUM_RADIUS_CELLS * 2 + 1);
    public static final int MAXIMUM_GROUPS = 256;
    public static final int MAXIMUM_SPECIES_ENTRIES = 256;
    public static final int MAXIMUM_REPRESENTED_ANIMALS = 4_096;

    public static final Type<EcosystemDebugMapPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "ecosystem_debug_map")
    );
    public static final StreamCodec<FriendlyByteBuf, EcosystemDebugMapPayload> STREAM_CODEC =
            StreamCodec.of(EcosystemDebugMapPayload::write, EcosystemDebugMapPayload::read);

    public EcosystemDebugMapPayload {
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (dataVersion != DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported ecosystem map data version: " + dataVersion);
        }
        if (serverGameTime < 0L) {
            throw new IllegalArgumentException("Ecosystem map time cannot be negative");
        }
        requireWorldCoordinate(playerBlockX, "playerBlockX");
        requireWorldCoordinate(playerBlockZ, "playerBlockZ");
        if (cellSize < 16 || cellSize > 256) {
            throw new IllegalArgumentException("Invalid ecosystem map cell size: " + cellSize);
        }
        if (radiusCells < 1 || radiusCells > MAXIMUM_RADIUS_CELLS) {
            throw new IllegalArgumentException("Invalid ecosystem map radius: " + radiusCells);
        }
        if (regionalCarryingCapacity < 1 || regionalCarryingCapacity > MAXIMUM_REPRESENTED_ANIMALS) {
            throw new IllegalArgumentException(
                    "Invalid ecosystem map carrying capacity: " + regionalCarryingCapacity
            );
        }

        cells = cells == null ? List.of() : List.copyOf(cells);
        groups = groups == null ? List.of() : List.copyOf(groups);
        int expectedCells = square(radiusCells * 2 + 1);
        if (cells.size() != expectedCells || cells.size() > MAXIMUM_CELLS) {
            throw new IllegalArgumentException(
                    "Ecosystem map must contain exactly " + expectedCells + " cells"
            );
        }
        if (groups.size() > MAXIMUM_GROUPS) {
            throw new IllegalArgumentException("Ecosystem map exceeds " + MAXIMUM_GROUPS + " groups");
        }

        Set<Long> uniqueCells = new HashSet<>();
        int speciesEntries = 0;
        int cellGroups = 0;
        int cellPopulation = 0;
        for (CellSnapshot cell : cells) {
            if (Math.abs((long) cell.cellX - centerCellX) > radiusCells
                    || Math.abs((long) cell.cellZ - centerCellZ) > radiusCells) {
                throw new IllegalArgumentException("Ecosystem map cell lies outside its bounded window");
            }
            if (!uniqueCells.add(pack(cell.cellX, cell.cellZ))) {
                throw new IllegalArgumentException("Ecosystem map contains a duplicate cell");
            }
            speciesEntries += cell.species().size();
            cellGroups += cell.groupCount();
            cellPopulation += cell.totalPopulation();
        }
        if (speciesEntries > MAXIMUM_SPECIES_ENTRIES) {
            throw new IllegalArgumentException(
                    "Ecosystem map exceeds " + MAXIMUM_SPECIES_ENTRIES + " species entries"
            );
        }
        if (cellPopulation > MAXIMUM_REPRESENTED_ANIMALS) {
            throw new IllegalArgumentException(
                    "Ecosystem map exceeds " + MAXIMUM_REPRESENTED_ANIMALS + " represented animals"
            );
        }

        Map<Long, MarkerAggregate> markerAggregates = new HashMap<>();
        for (GroupSnapshot group : groups) {
            int groupCellX = Math.floorDiv(floorToInt(group.blockX), cellSize);
            int groupCellZ = Math.floorDiv(floorToInt(group.blockZ), cellSize);
            long packedCell = pack(groupCellX, groupCellZ);
            if (!uniqueCells.contains(packedCell)) {
                throw new IllegalArgumentException("Ecosystem map group lies outside its bounded window");
            }
            markerAggregates.computeIfAbsent(packedCell, ignored -> new MarkerAggregate())
                    .add(group);
        }
        for (CellSnapshot cell : cells) {
            MarkerAggregate aggregate = markerAggregates.get(pack(cell.cellX, cell.cellZ));
            int markerGroups = aggregate == null ? 0 : aggregate.groupCount;
            int markerPopulation = aggregate == null ? 0 : aggregate.population;
            Map<ResourceLocation, Integer> markerSpecies = aggregate == null
                    ? Map.of()
                    : Map.copyOf(aggregate.species);
            Map<ResourceLocation, Integer> cellSpecies = new HashMap<>();
            cell.species.forEach(species -> cellSpecies.put(species.species, species.population));
            if (cell.groupCount != markerGroups
                    || cell.totalPopulation != markerPopulation
                    || !cellSpecies.equals(markerSpecies)) {
                throw new IllegalArgumentException(
                        "Ecosystem map cell aggregates do not match group markers"
                );
            }
        }
        int groupPopulation = groups.stream().mapToInt(GroupSnapshot::populationEstimate).sum();
        if (cellGroups != groups.size() || cellPopulation != groupPopulation) {
            throw new IllegalArgumentException("Ecosystem map totals do not match group markers");
        }
    }

    private static void write(FriendlyByteBuf buffer, EcosystemDebugMapPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeVarInt(payload.dataVersion);
        buffer.writeVarLong(payload.serverGameTime);
        buffer.writeInt(payload.playerBlockX);
        buffer.writeInt(payload.playerBlockZ);
        buffer.writeInt(payload.centerCellX);
        buffer.writeInt(payload.centerCellZ);
        buffer.writeVarInt(payload.cellSize);
        buffer.writeVarInt(payload.radiusCells);
        int flags = (payload.ecosystemEnabled ? 1 : 0)
                | (payload.distantWildlifeEnabled ? 2 : 0)
                | (payload.populationEcologyEnabled ? 4 : 0);
        buffer.writeByte(flags);
        buffer.writeVarInt(payload.regionalCarryingCapacity);
        buffer.writeVarInt(payload.cells.size());
        payload.cells.forEach(cell -> writeCell(buffer, cell));
        buffer.writeVarInt(payload.groups.size());
        payload.groups.forEach(group -> writeGroup(buffer, group));
    }

    private static EcosystemDebugMapPayload read(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int dataVersion = buffer.readVarInt();
        if (dataVersion != DATA_VERSION) {
            throw new DecoderException("Unsupported ecosystem map data version: " + dataVersion);
        }
        long gameTime = buffer.readVarLong();
        int playerBlockX = buffer.readInt();
        int playerBlockZ = buffer.readInt();
        int centerCellX = buffer.readInt();
        int centerCellZ = buffer.readInt();
        int cellSize = buffer.readVarInt();
        int radiusCells = buffer.readVarInt();
        int flags = buffer.readUnsignedByte();
        if ((flags & ~7) != 0) {
            throw new DecoderException("Invalid ecosystem map flags: " + flags);
        }
        int carryingCapacity = buffer.readVarInt();
        int cellCount = checkedCount(buffer.readVarInt(), MAXIMUM_CELLS, "cells");
        List<CellSnapshot> cells = new ArrayList<>(cellCount);
        int speciesEntries = 0;
        for (int index = 0; index < cellCount; index++) {
            CellSnapshot cell = readCell(buffer);
            speciesEntries += cell.species().size();
            if (speciesEntries > MAXIMUM_SPECIES_ENTRIES) {
                throw new DecoderException("Ecosystem map contains too many species entries");
            }
            cells.add(cell);
        }
        int groupCount = checkedCount(buffer.readVarInt(), MAXIMUM_GROUPS, "groups");
        List<GroupSnapshot> groups = new ArrayList<>(groupCount);
        for (int index = 0; index < groupCount; index++) {
            groups.add(readGroup(buffer));
        }
        return new EcosystemDebugMapPayload(
                dimension,
                dataVersion,
                gameTime,
                playerBlockX,
                playerBlockZ,
                centerCellX,
                centerCellZ,
                cellSize,
                radiusCells,
                (flags & 1) != 0,
                (flags & 2) != 0,
                (flags & 4) != 0,
                carryingCapacity,
                cells,
                groups
        );
    }

    private static void writeCell(FriendlyByteBuf buffer, CellSnapshot cell) {
        buffer.writeInt(cell.cellX);
        buffer.writeInt(cell.cellZ);
        buffer.writeEnum(cell.simulationLevel);
        buffer.writeVarInt(cell.groupCount);
        buffer.writeVarInt(cell.totalPopulation);
        buffer.writeVarInt(cell.species.size());
        cell.species.forEach(species -> {
            buffer.writeResourceLocation(species.species);
            buffer.writeVarInt(species.population);
        });
        buffer.writeBoolean(cell.hasMigrationTarget);
        if (cell.hasMigrationTarget) {
            buffer.writeInt(cell.migrationCellX);
            buffer.writeInt(cell.migrationCellZ);
        }
        buffer.writeFloat(cell.foodAvailability);
        buffer.writeFloat(cell.waterAvailability);
        buffer.writeFloat(cell.foodPressure);
        buffer.writeFloat(cell.disturbance);
        buffer.writeFloat(cell.weatherImpact);
        buffer.writeVarLong(cell.lastUpdatedTick);
    }

    private static CellSnapshot readCell(FriendlyByteBuf buffer) {
        int cellX = buffer.readInt();
        int cellZ = buffer.readInt();
        WildlifeSimulationLod simulationLevel = buffer.readEnum(WildlifeSimulationLod.class);
        int groupCount = buffer.readVarInt();
        int totalPopulation = buffer.readVarInt();
        int speciesCount = checkedCount(buffer.readVarInt(), MAXIMUM_SPECIES_ENTRIES, "cell species");
        List<SpeciesPopulation> species = new ArrayList<>(speciesCount);
        for (int index = 0; index < speciesCount; index++) {
            species.add(new SpeciesPopulation(buffer.readResourceLocation(), buffer.readVarInt()));
        }
        boolean hasMigrationTarget = buffer.readBoolean();
        int migrationCellX = hasMigrationTarget ? buffer.readInt() : cellX;
        int migrationCellZ = hasMigrationTarget ? buffer.readInt() : cellZ;
        return new CellSnapshot(
                cellX,
                cellZ,
                simulationLevel,
                groupCount,
                totalPopulation,
                species,
                hasMigrationTarget,
                migrationCellX,
                migrationCellZ,
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarLong()
        );
    }

    private static void writeGroup(FriendlyByteBuf buffer, GroupSnapshot group) {
        buffer.writeVarLong(group.id);
        buffer.writeResourceLocation(group.species);
        buffer.writeVarInt(group.populationEstimate);
        buffer.writeFloat(group.populationRemainder);
        buffer.writeDouble(group.blockX);
        buffer.writeDouble(group.blockZ);
        buffer.writeFloat(group.directionX);
        buffer.writeFloat(group.directionZ);
        buffer.writeEnum(group.form);
    }

    private static GroupSnapshot readGroup(FriendlyByteBuf buffer) {
        return new GroupSnapshot(
                buffer.readVarLong(),
                buffer.readResourceLocation(),
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readEnum(DistantWildlifeForm.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** One complete map cell, including aggregate ecosystem pressure channels. */
    public record CellSnapshot(
            int cellX,
            int cellZ,
            WildlifeSimulationLod simulationLevel,
            int groupCount,
            int totalPopulation,
            List<SpeciesPopulation> species,
            boolean hasMigrationTarget,
            int migrationCellX,
            int migrationCellZ,
            float foodAvailability,
            float waterAvailability,
            float foodPressure,
            float disturbance,
            float weatherImpact,
            long lastUpdatedTick
    ) {
        public CellSnapshot {
            simulationLevel = Objects.requireNonNull(simulationLevel, "simulationLevel");
            if (groupCount < 0 || groupCount > MAXIMUM_GROUPS) {
                throw new IllegalArgumentException("Invalid ecosystem map group count: " + groupCount);
            }
            if (totalPopulation < 0 || totalPopulation > MAXIMUM_REPRESENTED_ANIMALS) {
                throw new IllegalArgumentException(
                        "Invalid ecosystem map population: " + totalPopulation
                );
            }
            species = species == null ? List.of() : List.copyOf(species);
            if (species.size() > MAXIMUM_SPECIES_ENTRIES) {
                throw new IllegalArgumentException("Ecosystem map cell contains too many species");
            }
            int speciesPopulation = species.stream().mapToInt(SpeciesPopulation::population).sum();
            if (speciesPopulation != totalPopulation) {
                throw new IllegalArgumentException("Ecosystem map species do not sum to cell population");
            }
            if (species.stream().map(SpeciesPopulation::species).distinct().count() != species.size()) {
                throw new IllegalArgumentException("Ecosystem map cell contains duplicate species");
            }
            foodAvailability = requireUnit(foodAvailability, "foodAvailability");
            waterAvailability = requireUnit(waterAvailability, "waterAvailability");
            foodPressure = requireUnit(foodPressure, "foodPressure");
            disturbance = requireUnit(disturbance, "disturbance");
            weatherImpact = requireUnit(weatherImpact, "weatherImpact");
            if (lastUpdatedTick < 0L) {
                throw new IllegalArgumentException("Ecosystem map update time cannot be negative");
            }
            if (!hasMigrationTarget) {
                migrationCellX = cellX;
                migrationCellZ = cellZ;
            }
        }
    }

    /** Population total for one entity type inside a map cell. */
    public record SpeciesPopulation(ResourceLocation species, int population) {
        public SpeciesPopulation {
            species = Objects.requireNonNull(species, "species");
            if (population <= 0 || population > MAXIMUM_REPRESENTED_ANIMALS) {
                throw new IllegalArgumentException("Invalid mapped species population: " + population);
            }
        }
    }

    /** Exact marker for one persisted ecosystem-owned group. */
    public record GroupSnapshot(
            long id,
            ResourceLocation species,
            int populationEstimate,
            float populationRemainder,
            double blockX,
            double blockZ,
            float directionX,
            float directionZ,
            DistantWildlifeForm form
    ) {
        public GroupSnapshot {
            if (id <= 0L) {
                throw new IllegalArgumentException("Mapped group id must be positive");
            }
            species = Objects.requireNonNull(species, "species");
            if (populationEstimate <= 0
                    || populationEstimate > DistantWildlifeGroup.MAXIMUM_GROUP_POPULATION) {
                throw new IllegalArgumentException("Invalid mapped group population: " + populationEstimate);
            }
            if (!Float.isFinite(populationRemainder)
                    || populationRemainder < -0.5F
                    || populationRemainder > 0.5F) {
                throw new IllegalArgumentException("Invalid mapped population remainder");
            }
            requireFiniteCoordinate(blockX, "blockX");
            requireFiniteCoordinate(blockZ, "blockZ");
            double directionLength = Math.hypot(directionX, directionZ);
            if (!Float.isFinite(directionX) || !Float.isFinite(directionZ)
                    || directionLength < 0.5 || directionLength > 1.5) {
                throw new IllegalArgumentException("Invalid mapped group direction");
            }
            form = Objects.requireNonNull(form, "form");
        }
    }

    private static int checkedCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new DecoderException("Invalid ecosystem map " + label + " count: " + count);
        }
        return count;
    }

    private static float requireUnit(float value, String label) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            throw new IllegalArgumentException("Invalid ecosystem map " + label + ": " + value);
        }
        return value;
    }

    private static void requireFiniteCoordinate(double value, String label) {
        if (!Double.isFinite(value) || Math.abs(value) > 30_000_000.0) {
            throw new IllegalArgumentException("Invalid ecosystem map " + label + ": " + value);
        }
    }

    private static void requireWorldCoordinate(int value, String label) {
        if (Math.abs((long) value) > 30_000_000L) {
            throw new IllegalArgumentException("Invalid ecosystem map " + label + ": " + value);
        }
    }

    private static int floorToInt(double value) {
        return (int) Math.floor(value);
    }

    private static int square(int value) {
        return Math.multiplyExact(value, value);
    }

    private static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    private static final class MarkerAggregate {
        private final Map<ResourceLocation, Integer> species = new HashMap<>();
        private int groupCount;
        private int population;

        private void add(GroupSnapshot group) {
            groupCount++;
            population += group.populationEstimate;
            species.merge(group.species, group.populationEstimate, Integer::sum);
        }
    }
}
