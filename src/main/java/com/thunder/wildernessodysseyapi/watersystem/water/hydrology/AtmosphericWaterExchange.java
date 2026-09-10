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
import java.util.WeakHashMap;

/**
 * Receipts from the regional water owner to the existing weather owner.
 *
 * <p>These counters are not water storage. Hydrology publishes only accepted
 * transfers; weather acknowledges a detached receipt only after its revision-
 * checked calculation commits. Rejected worker results cannot consume feedback.
 * Precipitation is an explicitly open atmospheric boundary: weather's normalized
 * cloud inventory is not a second finite cubic-metre reservoir.</p>
 */
public final class AtmosphericWaterExchange {
    public static final long MILLI_UNITS_PER_CUBIC_METRE = 4_096_000L;
    private static final Map<ServerLevel, ReceiptBook> LEVELS = new WeakHashMap<>();

    private AtmosphericWaterExchange() {
    }

    /** Publishes one completed hydrology interval; replayed/older intervals are ignored. */
    public static synchronized boolean publish(ServerLevel level, ChunkPos chunk,
            long precipitationMilliUnits, long evaporationMilliUnits,
            long snowWaterEquivalentMilliUnits, double areaSquareMetres, long throughTick) {
        return LEVELS.computeIfAbsent(level, ignored -> new ReceiptBook()).publish(chunk.toLong(),
                precipitationMilliUnits, evaporationMilliUnits, snowWaterEquivalentMilliUnits,
                areaSquareMetres, throughTick);
    }

    /** Reads already-published surface state without querying or loading a chunk. */
    public static synchronized SurfaceSnapshot query(ServerLevel level, ChunkPos chunk) {
        ReceiptBook book = LEVELS.get(level);
        return book == null ? SurfaceSnapshot.UNSUPPORTED : book.query(chunk.toLong());
    }

    /** Captures immutable outstanding feedback for a weather worker without consuming it. */
    public static synchronized Receipt capture(ServerLevel level, AtmosphereCellKey cell, int cellSize) {
        ReceiptBook book = LEVELS.get(level);
        return book == null ? Receipt.EMPTY : book.capture(cell, cellSize);
    }

    /** Acknowledges only the captured totals; publications made during calculation remain pending. */
    public static synchronized void acknowledge(ServerLevel level, Receipt receipt) {
        ReceiptBook book = LEVELS.get(level);
        if (book != null) {
            book.acknowledge(receipt);
        }
    }

    /** Releases optional weather feedback on dimension unload; no water volume is destroyed. */
    public static synchronized void clearLevel(ServerLevel level) {
        LEVELS.remove(level);
    }

    /** Clears ephemeral feedback when the server stops. Regional storage persists independently. */
    public static synchronized void clear() {
        LEVELS.clear();
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
         * a documented climate approximation, not a claim of atmospheric mass closure.
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
