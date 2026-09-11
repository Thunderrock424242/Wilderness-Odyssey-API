package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Receipts from the regional water owner to the existing weather owner.
 *
 * <p>These counters are not water storage. Hydrology publishes only accepted
 * transfers; weather acknowledges a detached receipt only after its revision-
 * checked calculation commits. Rejected worker results cannot consume feedback.
 * Physical precipitation is queued only after the atmosphere removes that mass.
 * The receipt book persists with regional water, including pending fractions.</p>
 */
public final class AtmosphericWaterExchange {
    public static final long MILLI_UNITS_PER_CUBIC_METRE = 4_096_000L;


    private AtmosphericWaterExchange() {
    }

    /** Publishes one completed hydrology interval; replayed/older intervals are ignored. */
    public static synchronized boolean publish(ServerLevel level, ChunkPos chunk,
            long precipitationMilliUnits, long evaporationMilliUnits,
            long snowWaterEquivalentMilliUnits, double areaSquareMetres, long throughTick) {
        RegionalHydrologySavedData data = RegionalHydrologySavedData.get(level);
        boolean changed = data.atmosphereExchange().publish(chunk.toLong(), precipitationMilliUnits, evaporationMilliUnits,
                snowWaterEquivalentMilliUnits, areaSquareMetres, throughTick);
        if (changed) data.setDirty();
        return changed;
    }

    /** Reads already-published surface state without querying or loading a chunk. */
    public static synchronized SurfaceSnapshot query(ServerLevel level, ChunkPos chunk) {
        ReceiptBook book = RegionalHydrologySavedData.get(level).atmosphereExchange();
        return book == null ? SurfaceSnapshot.UNSUPPORTED : book.query(chunk.toLong());
    }

    /** Captures immutable outstanding feedback for a weather worker without consuming it. */
    public static synchronized Receipt capture(ServerLevel level, AtmosphereCellKey cell, int cellSize) {
        ReceiptBook book = RegionalHydrologySavedData.get(level).atmosphereExchange();
        return book == null ? Receipt.EMPTY : book.capture(cell, cellSize);
    }

    /** Acknowledges only the captured totals; publications made during calculation remain pending. */
    public static synchronized void acknowledge(ServerLevel level, Receipt receipt) {
        ReceiptBook book = RegionalHydrologySavedData.get(level).atmosphereExchange();
        if (book != null) {
            book.acknowledge(receipt);
            RegionalHydrologySavedData.get(level).setDirty();
        }
    }

    /** Unload leaves durable receipts in the dimension's SavedData owner. */
    public static synchronized void clearLevel(ServerLevel level) {
        // The dimension SavedData owns durable receipts; unloading cannot discard them.
    }

    /** No process-global feedback needs clearing; regional SavedData owns persistence. */
    public static synchronized void clear() {
        // No static receipt owner remains.
    }

    /** Publishes only precipitation whose physical atmospheric debit has committed. */
    public static synchronized void publishPrecipitation(ServerLevel level, AtmosphereCellKey cell, int cellSize,
            com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericWaterFlux flux, long throughTick) {
        RegionalHydrologySavedData data = RegionalHydrologySavedData.get(level);
        if (data.atmosphereExchange().publishPrecipitation(cell, cellSize, flux, throughTick)) data.setDirty();
    }

    /** Applies accepted queued precipitation to the existing surface/SWE/ice owners exactly once. */
    public static synchronized long applyPrecipitation(ServerLevel level, RegionalHydrologyState state) {
        RegionalHydrologySavedData data = RegionalHydrologySavedData.get(level);
        long accepted = data.atmosphereExchange().applyPrecipitation(state);
        if (accepted > 0) data.setDirty();
        return accepted;
    }

    /** Read-only snow-water equivalent supplied by regional storage, not an independent snowpack. */
    public record SurfaceSnapshot(boolean supported, long snowWaterEquivalentMilliUnits,
                                  double areaSquareMetres, long throughTick) {
        public static final SurfaceSnapshot UNSUPPORTED = new SurfaceSnapshot(false, 0L, 0.0, 0L);

        /** Visual cover reaches one at 50 mm liquid equivalent; this conversion never moves water. */
        public double snowCoverage() {
            return supported && areaSquareMetres > 0.0
                    ? unit(snowWaterEquivalentMilliUnits
                    / (MILLI_UNITS_PER_CUBIC_METRE * areaSquareMetres * 0.05)) : 0.0;
        }
    }

    /** Immutable weather input. Exact fluxes remain visible even though vapor response is normalized. */
    public record Receipt(long precipitationMilliUnits, long evaporationMilliUnits,
                          double coveredFraction, double snowCoverage,
                          double cellAreaSquareMetres, List<Watermark> watermarks) {
        public static final Receipt EMPTY = new Receipt(0L, 0L, 0.0, 0.0, 1.0, List.of());

        public Receipt {
            if (precipitationMilliUnits < 0L || evaporationMilliUnits < 0L
                    || !Double.isFinite(cellAreaSquareMetres) || cellAreaSquareMetres <= 0.0) {
                throw new IllegalArgumentException("Invalid atmospheric water receipt");
            }
            coveredFraction = unit(coveredFraction);
            snowCoverage = unit(snowCoverage);
            watermarks = List.copyOf(watermarks);
        }

        /**
         * Maps accepted evaporation into the normalized atmospheric column.
         * One inventory unit represents 25 mm of precipitable water. This is
         * the compatibility conversion; the physical solver multiplies by 25
         * and retains the exact accepted water amount in kg/m2.
         */
        public double evaporatedVaporInventory() {
            return evaporationMilliUnits
                    / (MILLI_UNITS_PER_CUBIC_METRE * cellAreaSquareMetres * 0.025);
        }

        /** Retains regional surface ownership during catch-up without replaying the same transfer. */
        public Receipt withoutFlux() {
            return new Receipt(0L, 0L, coveredFraction, snowCoverage, cellAreaSquareMetres, List.of());
        }
    }

    /** Cumulative per-chunk counters captured together with a worker input. */
    public record Watermark(long chunk, long precipitation, long evaporation) {
    }

    /**
     * Pure receipt bookkeeping, separately testable without a Minecraft level.
     * Its entries are bounded by the regional owner's admitted node count.
     */
    public static final class ReceiptBook {
        private final Map<Long, Entry> entries = new HashMap<>();
        private final Map<Long, Set<Long>> cells = new HashMap<>();
        private int indexedCellSize;
        private final Map<Long, Long> precipitationClocks = new HashMap<>();
        private final Map<Long, double[]> pendingPrecipitation = new HashMap<>();

        /** Records an already-accepted interval atomically, rejecting negative amounts. */
        public boolean publish(long chunk, long precipitation, long evaporation,
                long snow, double area, long throughTick) {
            if (precipitation < 0L || evaporation < 0L || snow < 0L
                    || !Double.isFinite(area) || area <= 0.0 || area > 256.0) {
                throw new IllegalArgumentException("Invalid regional water receipt");
            }
            Entry entry = entries.get(chunk);
            if (entry != null && throughTick <= entry.surface.throughTick()) {
                return false;
            }
            long nextPrecipitation = Math.addExact(entry == null ? 0L : entry.precipitation, precipitation);
            long nextEvaporation = Math.addExact(entry == null ? 0L : entry.evaporation, evaporation);
            if (entry == null) {
                entry = new Entry();
                entries.put(chunk, entry);
                if (indexedCellSize > 0) {
                    index(chunk);
                }
            }
            entry.precipitation = nextPrecipitation;
            entry.evaporation = nextEvaporation;
            entry.surface = new SurfaceSnapshot(true, snow, area, throughTick);
            return true;
        }

        /** Queries an admitted node; missing nodes retain legacy weather behavior. */
        public SurfaceSnapshot query(long chunk) {
            Entry entry = entries.get(chunk);
            return entry == null ? SurfaceSnapshot.UNSUPPORTED : entry.surface;
        }

        /** Aggregates only indexed admitted nodes, without scanning terrain or the whole world. */
        public Receipt capture(AtmosphereCellKey cell, int cellSize) {
            if (cellSize < 16) {
                throw new IllegalArgumentException("Atmospheric cells must be at least one chunk wide");
            }
            if (cellSize % 16 != 0) {
                throw new IllegalArgumentException("Hydrology exchange requires chunk-aligned atmosphere cells");
            }
            if (indexedCellSize != cellSize) {
                cells.clear();
                indexedCellSize = cellSize;
                entries.keySet().forEach(this::index);
            }
            Set<Long> keys = cells.get(cell.packed());
            if (keys == null) {
                return Receipt.EMPTY;
            }
            long precipitation = 0L;
            long evaporation = 0L;
            double area = 0.0;
            double coveredSnowArea = 0.0;
            List<Watermark> marks = new ArrayList<>(keys.size());
            for (long key : keys) {
                Entry entry = entries.get(key);
                precipitation = Math.addExact(precipitation, entry.precipitation - entry.acknowledgedPrecipitation);
                evaporation = Math.addExact(evaporation, entry.evaporation - entry.acknowledgedEvaporation);
                area += entry.surface.areaSquareMetres();
                coveredSnowArea += entry.surface.snowCoverage() * entry.surface.areaSquareMetres();
                marks.add(new Watermark(key, entry.precipitation, entry.evaporation));
            }
            double cellArea = (double) cellSize * cellSize;
            return new Receipt(precipitation, evaporation, area / cellArea,
                    area == 0.0 ? 0.0 : coveredSnowArea / area, cellArea, marks);
        }

        /** Advances acknowledgement watermarks monotonically, including on duplicate acknowledgements. */
        public void acknowledge(Receipt receipt) {
            for (Watermark mark : receipt.watermarks()) {
                Entry entry = entries.get(mark.chunk());
                if (entry != null) {
                    entry.acknowledgedPrecipitation = Math.max(entry.acknowledgedPrecipitation,
                            Math.min(entry.precipitation, mark.precipitation()));
                    entry.acknowledgedEvaporation = Math.max(entry.acknowledgedEvaporation,
                            Math.min(entry.evaporation, mark.evaporation()));
                }
            }
        }

        /** Queues exact cell-depth times admitted chunk area, retaining fractional milli-units. */
        public boolean publishPrecipitation(AtmosphereCellKey cell, int size,
                com.thunder.wildernessodysseyapi.weather.simulation.AtmosphericWaterFlux flux, long throughTick) {
            if (throughTick <= precipitationClocks.getOrDefault(cell.packed(), Long.MIN_VALUE)) return false;
            capture(cell, size); // builds the bounded spatial index
            Set<Long> members = cells.getOrDefault(cell.packed(), Set.of());
            for (long key : members) {
                Entry entry = entries.get(key);
                double factor = entry.surface.areaSquareMetres() * MILLI_UNITS_PER_CUBIC_METRE / 1000.0;
                double[] amounts = pendingPrecipitation.computeIfAbsent(key, ignored -> new double[5]);
                amounts[0] += flux.rainfallMm() * factor;
                amounts[1] += flux.snowfallMm() * factor;
                amounts[2] += flux.sleetMm() * factor;
                amounts[3] += flux.freezingRainMm() * factor;
                amounts[4] += flux.hailMm() * factor;
            }
            // No admitted nodes means explicit precipitation export to unmodeled terrain.
            if (!members.isEmpty()) precipitationClocks.put(cell.packed(), throughTick);
            return !members.isEmpty();
        }

        /** The same saved book and regional store are dirtied together, including capacity-rejected remainder. */
        public long applyPrecipitation(RegionalHydrologyState state) {
            double[] amounts = pendingPrecipitation.get(state.key);
            if (amounts == null) return 0;
            long accepted = 0;
            HydrologicReservoir[] reservoirs = { HydrologicReservoir.SURFACE_RUNOFF, HydrologicReservoir.SNOW,
                    HydrologicReservoir.SNOW, HydrologicReservoir.ICE, HydrologicReservoir.SNOW };
            for (int i = 0; i < amounts.length; i++) {
                long requested = (long) Math.floor(Math.min(Long.MAX_VALUE, amounts[i]));
                long moved = state.credit(reservoirs[i], requested, RegionalHydrologyState.Boundary.PRECIPITATION);
                amounts[i] -= moved;
                accepted = Math.addExact(accepted, moved);
            }
            state.physicalPrecipitation = true;
            return accepted;
        }

        /** Exact receipts and pending fractional precipitation survive orderly save/load. */
        public net.minecraft.nbt.CompoundTag save() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("version", 1);
            net.minecraft.nbt.ListTag saved = new net.minecraft.nbt.ListTag();
            for (long key : new java.util.TreeSet<>(entries.keySet())) {
                Entry e = entries.get(key);
                net.minecraft.nbt.CompoundTag item = new net.minecraft.nbt.CompoundTag();
                item.putLong("key", key); item.putLong("rain", e.precipitation); item.putLong("et", e.evaporation);
                item.putLong("ack_rain", e.acknowledgedPrecipitation); item.putLong("ack_et", e.acknowledgedEvaporation);
                item.putLong("snow", e.surface.snowWaterEquivalentMilliUnits()); item.putDouble("area", e.surface.areaSquareMetres());
                item.putLong("time", e.surface.throughTick());
                double[] pending = pendingPrecipitation.getOrDefault(key, new double[5]);
                long[] bits = new long[5];
                for (int i = 0; i < bits.length; i++) bits[i] = Double.doubleToLongBits(pending[i]);
                item.putLongArray("pending", bits); saved.add(item);
            }
            tag.put("entries", saved);
            net.minecraft.nbt.ListTag clocks = new net.minecraft.nbt.ListTag();
            precipitationClocks.forEach((key, time) -> {
                net.minecraft.nbt.CompoundTag clock = new net.minecraft.nbt.CompoundTag();
                clock.putLong("key", key); clock.putLong("time", time); clocks.add(clock);
            });
            tag.put("clocks", clocks);
            return tag;
        }

        public static ReceiptBook load(net.minecraft.nbt.CompoundTag tag) {
            if (tag.getInt("version") != 1) throw new IllegalArgumentException("Unsupported atmospheric exchange save");
            ReceiptBook book = new ReceiptBook();
            net.minecraft.nbt.ListTag saved = tag.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < saved.size(); i++) {
                net.minecraft.nbt.CompoundTag item = saved.getCompound(i);
                long key = item.getLong("key");
                book.publish(key, item.getLong("rain"), item.getLong("et"), item.getLong("snow"),
                        item.getDouble("area"), item.getLong("time"));
                Entry entry = book.entries.get(key);
                entry.acknowledgedPrecipitation = Math.max(0, Math.min(entry.precipitation, item.getLong("ack_rain")));
                entry.acknowledgedEvaporation = Math.max(0, Math.min(entry.evaporation, item.getLong("ack_et")));
                long[] bits = item.getLongArray("pending");
                if (bits.length != 5) throw new IllegalArgumentException("Malformed precipitation receipt");
                double[] pending = new double[5];
                for (int n = 0; n < 5; n++) {
                    pending[n] = Double.longBitsToDouble(bits[n]);
                    if (!Double.isFinite(pending[n]) || pending[n] < 0) throw new IllegalArgumentException("Invalid pending precipitation");
                }
                book.pendingPrecipitation.put(key, pending);
            }
            net.minecraft.nbt.ListTag clocks = tag.getList("clocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < clocks.size(); i++) book.precipitationClocks.put(clocks.getCompound(i).getLong("key"),
                    clocks.getCompound(i).getLong("time"));
            return book;
        }

        private void index(long chunkKey) {
            ChunkPos chunk = new ChunkPos(chunkKey);
            AtmosphereCellKey cell = AtmosphereCellKey.fromBlock(chunk.getMiddleBlockX(),
                    chunk.getMiddleBlockZ(), indexedCellSize);
            cells.computeIfAbsent(cell.packed(), ignored -> new HashSet<>()).add(chunkKey);
        }
    }

    private static final class Entry {
        private long precipitation;
        private long evaporation;
        private long acknowledgedPrecipitation;
        private long acknowledgedEvaporation;
        private SurfaceSnapshot surface = SurfaceSnapshot.UNSUPPORTED;
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
