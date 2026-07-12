package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent compact baseline for water written while a chunk is generated.
 *
 * <p>Untouched oceans are stored as vertical runs rather than runtime water
 * cells. The attachment is created on a {@code ProtoChunk}, copied by NeoForge
 * during chunk promotion, saved with the chunk, and synchronized to clients.
 * Runtime disturbances remain in {@link WaterVolumeChunk}.</p>
 */
public final class GeneratedWaterChunk implements INBTSerializable<CompoundTag> {

    /** Current on-disk and network representation. */
    public static final int FORMAT_VERSION = 3;
    /** Primitive integers used by one encoded span. */
    public static final int SERIALIZED_SPAN_STRIDE = 6;
    private static final int LEGACY_SPAN_STRIDE = 5;
    /** Number of block columns in a Minecraft chunk. */
    public static final int COLUMN_COUNT = 16 * 16;

    private static final String FORMAT_KEY = "format";
    private static final String REVISION_KEY = "revision";
    private static final String SPANS_KEY = "spans";
    private static final String BOUNDS_KEY = "bounds";
    private static final String EDGES_KEY = "edges";
    private static final String COVERED_KEY = "covered";
    private static final int COVER_WORD_COUNT = COLUMN_COUNT / Integer.SIZE;
    private static final int FALLING_BIT = 1 << 4;
    private static final int BODY_SHIFT = 5;

    /** Full attachment codec used for initial chunk synchronization. */
    public static final StreamCodec<RegistryFriendlyByteBuf, GeneratedWaterChunk> STREAM_CODEC =
            StreamCodec.of(GeneratedWaterChunk::encode, GeneratedWaterChunk::decode);

    private final Map<Integer, ArrayList<WaterSpan>> mutableColumns = new HashMap<>();
    private final int[] mutableSurfaceCovered = new int[COVER_WORD_COUNT];
    private long revision;
    private boolean dirty;
    private Runnable dirtyListener = () -> { };
    private volatile Snapshot compactSnapshot = Snapshot.EMPTY;
    private boolean compactSnapshotValid = true;

    /** Sets the callback that marks the owning chunk unsaved after generation writes. */
    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> { } : dirtyListener;
    }

    /** Returns the monotonically increasing content revision. */
    public long revision() {
        return revision;
    }

    /** Returns whether this attachment has unsaved generation writes. */
    public boolean isDirty() {
        return dirty;
    }

    /** Clears the local dirty marker after the owning chunk is persisted. */
    public void clearDirty() {
        dirty = false;
    }

    /**
     * Records the final generated state for one block without writing to the world.
     *
     * <p>Repeated identical writes are ignored. Overwrites split or merge the
     * affected run so late terrain/features cannot leave stale water metadata.</p>
     *
     * @return {@code true} when compact metadata changed
     */
    public boolean recordCell(BlockPos worldPos, Cell cell) {
        int column = columnIndex(worldPos.getX(), worldPos.getZ());
        int y = worldPos.getY();
        ArrayList<WaterSpan> spans = mutableColumns.computeIfAbsent(column, ignored -> new ArrayList<>());
        int previousTop = spans.isEmpty() ? Integer.MIN_VALUE : spans.get(spans.size() - 1).topY();
        WaterSpan containing = null;
        int containingIndex = -1;
        for (int index = 0; index < spans.size(); index++) {
            WaterSpan span = spans.get(index);
            if (y < span.bottomY()) {
                break;
            }
            if (y <= span.topY()) {
                containing = span;
                containingIndex = index;
                break;
            }
        }

        Cell normalized = cell == null || cell.amount() <= 0 ? null : cell.normalized();
        if (containing != null && normalized != null && containing.cell().equals(normalized)) {
            return false;
        }
        if (containing == null && normalized == null) {
            if (spans.isEmpty()) {
                mutableColumns.remove(column);
            }
            return false;
        }

        // Remove the old point from its run, retaining the portions above and below it.
        if (containing != null) {
            spans.remove(containingIndex);
            if (containing.bottomY() < y) {
                spans.add(new WaterSpan(containing.bottomY(), y - 1, containing.cell()));
            }
            if (y < containing.topY()) {
                spans.add(new WaterSpan(y + 1, containing.topY(), containing.cell()));
            }
        }
        if (normalized != null) {
            spans.add(new WaterSpan(y, y, normalized));
        }

        normalizeSpans(spans);
        int nextTop = spans.isEmpty() ? Integer.MIN_VALUE : spans.get(spans.size() - 1).topY();
        if (previousTop != nextTop) {
            mutableSurfaceCovered[column >>> 5] &= ~(1 << (column & 31));
        }
        if (spans.isEmpty()) {
            mutableColumns.remove(column);
        }
        changed();
        return true;
    }

    /**
     * Records whether the block immediately above a generated surface hides it.
     *
     * <p>Ice and other feature cover is written after terrain water in normal
     * generation. Capturing that write here prevents custom surface geometry
     * from drawing through the cover without a finished-chunk exposure scan.</p>
     */
    public boolean recordSurfaceCover(BlockPos worldPos, boolean covered) {
        int column = columnIndex(worldPos.getX(), worldPos.getZ());
        List<WaterSpan> spans = mutableColumns.get(column);
        if (spans == null || spans.isEmpty()
                || worldPos.getY() != spans.get(spans.size() - 1).topY() + 1) {
            return false;
        }
        int word = column >>> 5;
        int bit = 1 << (column & 31);
        boolean previous = (mutableSurfaceCovered[word] & bit) != 0;
        if (previous == covered) {
            return false;
        }
        if (covered) {
            mutableSurfaceCovered[word] |= bit;
        } else {
            mutableSurfaceCovered[word] &= ~bit;
        }
        changed();
        return true;
    }

    /** Returns the generated span occupying the position, or {@code null}. */
    public WaterSpan spanAt(BlockPos worldPos) {
        return spanAt(worldPos.getX() & 15, worldPos.getY(), worldPos.getZ() & 15);
    }

    /** Returns the generated span occupying local X/Z and world Y, or {@code null}. */
    public WaterSpan spanAt(int localX, int worldY, int localZ) {
        return snapshot().spanAt(localX, worldY, localZ);
    }

    /** Returns the highest generated water run in a column, or {@code null}. */
    public WaterSpan topSpan(int localX, int localZ) {
        return snapshot().topSpan(localX, localZ);
    }

    /** Returns a compact immutable view safe for networking and render snapshot construction. */
    public Snapshot snapshot() {
        if (!compactSnapshotValid) {
            compactSnapshot = buildSnapshot();
            compactSnapshotValid = true;
        }
        return compactSnapshot;
    }

    /** Encodes spans without object allocation at network and persistence boundaries. */
    public int[] toNetworkArray() {
        return snapshot().encodedSpans();
    }

    /** Estimated retained bytes for diagnostics, excluding JVM object headers. */
    public int estimatedBytes() {
        return snapshot().estimatedBytes();
    }

    /** Number of compact vertical runs in this chunk. */
    public int spanCount() {
        return snapshot().encodedSpans.length / SERIALIZED_SPAN_STRIDE;
    }

    /** Creates a detached compact copy used by attachment promotion/synchronization. */
    public GeneratedWaterChunk compactCopy() {
        GeneratedWaterChunk copy = new GeneratedWaterChunk();
        copy.applySnapshot(revision, snapshot().encodedSpans, snapshot().surfaceCovered);
        copy.dirty = dirty;
        return copy;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        Snapshot snapshot = snapshot();
        CompoundTag tag = new CompoundTag();
        tag.putInt(FORMAT_KEY, FORMAT_VERSION);
        tag.putLong(REVISION_KEY, revision);
        tag.putIntArray(SPANS_KEY, snapshot.encodedSpans);
        tag.putIntArray(BOUNDS_KEY, snapshot.columnBounds);
        tag.putIntArray(EDGES_KEY, new int[]{
                snapshot.northMask & 0xFFFF,
                snapshot.southMask & 0xFFFF,
                snapshot.westMask & 0xFFFF,
                snapshot.eastMask & 0xFFFF
        });
        tag.putIntArray(COVERED_KEY, snapshot.surfaceCovered);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        int format = tag.contains(FORMAT_KEY) ? tag.getInt(FORMAT_KEY) : 0;
        if (format > FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported generated water format " + format);
        }
        int[] covered = format >= 2 ? tag.getIntArray(COVERED_KEY) : new int[COVER_WORD_COUNT];
        int[] encoded = upgradeEncoded(format, tag.getIntArray(SPANS_KEY));
        applySnapshot(Math.max(0L, tag.getLong(REVISION_KEY)), encoded, covered);
        dirty = false;
    }

    private void applySnapshot(long revision, int[] encoded, int[] covered) {
        mutableColumns.clear();
        Arrays.fill(mutableSurfaceCovered, 0);
        System.arraycopy(covered, 0, mutableSurfaceCovered, 0,
                Math.min(covered.length, mutableSurfaceCovered.length));
        if (encoded.length % SERIALIZED_SPAN_STRIDE != 0) {
            throw new IllegalArgumentException("Invalid generated water span array");
        }
        for (int offset = 0; offset < encoded.length; offset += SERIALIZED_SPAN_STRIDE) {
            int column = encoded[offset];
            if (column < 0 || column >= COLUMN_COUNT) {
                continue;
            }
            int bottomY = encoded[offset + 1];
            int topY = encoded[offset + 2];
            int state = encoded[offset + 3];
            int weights = encoded[offset + 4];
            int waterTint = encoded[offset + 5];
            if (topY < bottomY) {
                continue;
            }
            Cell cell = decodeCell(state, weights, waterTint);
            mutableColumns.computeIfAbsent(column, ignored -> new ArrayList<>())
                    .add(new WaterSpan(bottomY, topY, cell));
        }
        for (ArrayList<WaterSpan> spans : mutableColumns.values()) {
            normalizeSpans(spans);
        }
        this.revision = revision;
        compactSnapshotValid = false;
        snapshot();
    }

    private Snapshot buildSnapshot() {
        int spanCount = mutableColumns.values().stream().mapToInt(List::size).sum();
        int[] encoded = new int[spanCount * SERIALIZED_SPAN_STRIDE];
        int[] offsets = new int[COLUMN_COUNT + 1];
        int[] bounds = new int[COLUMN_COUNT];
        Arrays.fill(bounds, packBounds(Short.MIN_VALUE, Short.MIN_VALUE));
        int outputSpan = 0;
        short north = 0;
        short south = 0;
        short west = 0;
        short east = 0;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            offsets[column] = outputSpan;
            List<WaterSpan> spans = mutableColumns.getOrDefault(column, new ArrayList<>());
            for (WaterSpan span : spans) {
                int output = outputSpan++ * SERIALIZED_SPAN_STRIDE;
                encoded[output] = column;
                encoded[output + 1] = span.bottomY();
                encoded[output + 2] = span.topY();
                encoded[output + 3] = encodeState(span.cell());
                encoded[output + 4] = encodeWeights(span.cell());
                encoded[output + 5] = span.cell().waterTint();
            }
            if (!spans.isEmpty()) {
                WaterSpan top = spans.get(spans.size() - 1);
                int contiguousBottom = top.bottomY();
                for (int spanIndex = spans.size() - 2; spanIndex >= 0; spanIndex--) {
                    WaterSpan below = spans.get(spanIndex);
                    if (below.topY() + 1 < contiguousBottom) {
                        break;
                    }
                    contiguousBottom = below.bottomY();
                }
                bounds[column] = packBounds(top.topY(), contiguousBottom - 1);
                int localX = column & 15;
                int localZ = column >>> 4;
                if (localZ == 0) north |= (short) (1 << localX);
                if (localZ == 15) south |= (short) (1 << localX);
                if (localX == 0) west |= (short) (1 << localZ);
                if (localX == 15) east |= (short) (1 << localZ);
            }
        }
        offsets[COLUMN_COUNT] = outputSpan;
        return new Snapshot(revision, offsets, encoded, bounds, mutableSurfaceCovered.clone(),
                north, south, west, east);
    }

    private void changed() {
        revision++;
        dirty = true;
        compactSnapshotValid = false;
        dirtyListener.run();
    }

    private static void normalizeSpans(ArrayList<WaterSpan> spans) {
        spans.sort(Comparator.comparingInt(WaterSpan::bottomY));
        for (int index = 1; index < spans.size();) {
            WaterSpan previous = spans.get(index - 1);
            WaterSpan current = spans.get(index);
            if (previous.topY() + 1 >= current.bottomY() && previous.cell().equals(current.cell())) {
                spans.set(index - 1, new WaterSpan(
                        previous.bottomY(),
                        Math.max(previous.topY(), current.topY()),
                        previous.cell()
                ));
                spans.remove(index);
            } else {
                index++;
            }
        }
    }

    private static int columnIndex(int x, int z) {
        return (x & 15) | ((z & 15) << 4);
    }

    private static int encodeState(Cell cell) {
        return cell.amount | (cell.falling ? FALLING_BIT : 0) | (cell.bodyType.ordinal() << BODY_SHIFT);
    }

    private static int encodeWeights(Cell cell) {
        return cell.oceanWeight | (cell.riverWeight << 8) | (cell.lakeWeight << 16);
    }

    private static Cell decodeCell(int state, int weights, int waterTint) {
        int bodyOrdinal = (state >>> BODY_SHIFT) & 7;
        BodyType[] types = BodyType.values();
        BodyType bodyType = bodyOrdinal < types.length ? types[bodyOrdinal] : BodyType.LAKE;
        return new Cell(
                state & 15,
                (state & FALLING_BIT) != 0,
                bodyType,
                weights & 0xFF,
                (weights >>> 8) & 0xFF,
                (weights >>> 16) & 0xFF,
                waterTint
        ).normalized();
    }

    private static int[] upgradeEncoded(int format, int[] encoded) {
        if (format >= 3 || encoded.length == 0) {
            return encoded;
        }
        if (encoded.length % LEGACY_SPAN_STRIDE != 0) {
            throw new IllegalArgumentException("Invalid legacy generated water span array");
        }
        int spans = encoded.length / LEGACY_SPAN_STRIDE;
        int[] upgraded = new int[spans * SERIALIZED_SPAN_STRIDE];
        for (int span = 0; span < spans; span++) {
            System.arraycopy(encoded, span * LEGACY_SPAN_STRIDE,
                    upgraded, span * SERIALIZED_SPAN_STRIDE, LEGACY_SPAN_STRIDE);
            upgraded[span * SERIALIZED_SPAN_STRIDE + 5] = Cell.DEFAULT_WATER_TINT;
        }
        return upgraded;
    }

    private static int packBounds(int surfaceY, int floorY) {
        return (surfaceY & 0xFFFF) | ((floorY & 0xFFFF) << 16);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, GeneratedWaterChunk water) {
        Snapshot snapshot = water.snapshot();
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeVarLong(water.revision);
        buffer.writeVarInt(snapshot.encodedSpans.length);
        for (int value : snapshot.encodedSpans) {
            buffer.writeInt(value);
        }
        for (int value : snapshot.surfaceCovered) {
            buffer.writeInt(value);
        }
    }

    private static GeneratedWaterChunk decode(RegistryFriendlyByteBuf buffer) {
        int format = buffer.readVarInt();
        if (format > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported generated water network format " + format);
        }
        long revision = buffer.readVarLong();
        int length = buffer.readVarInt();
        int wireStride = format >= 3 ? SERIALIZED_SPAN_STRIDE : LEGACY_SPAN_STRIDE;
        int maximumLength = COLUMN_COUNT * 384 * wireStride;
        if (length < 0 || length > maximumLength || length % wireStride != 0) {
            throw new IllegalArgumentException("Invalid generated water network span length " + length);
        }
        int[] encoded = new int[length];
        for (int index = 0; index < length; index++) {
            encoded[index] = buffer.readInt();
        }
        int[] covered = new int[COVER_WORD_COUNT];
        if (format >= 2) {
            for (int index = 0; index < covered.length; index++) {
                covered[index] = buffer.readInt();
            }
        }
        GeneratedWaterChunk result = new GeneratedWaterChunk();
        result.applySnapshot(revision, upgradeEncoded(format, encoded), covered);
        result.dirty = false;
        return result;
    }

    /** Broad origin classification used to blend shared surface wave profiles. */
    public enum BodyType {
        OCEAN,
        RIVER,
        LAKE,
        AQUIFER,
        SPRING
    }

    /** Compact water state shared by every block in a vertical run. */
    public record Cell(
            int amount,
            boolean falling,
            BodyType bodyType,
            int oceanWeight,
            int riverWeight,
            int lakeWeight,
            int waterTint
    ) {
        /** Vanilla's neutral water tint used when upgrading pre-tint metadata. */
        public static final int DEFAULT_WATER_TINT = 0x3F76E4;

        /** Creates a one-hot body blend for a generated fluid state. */
        public static Cell of(int amount, boolean falling, BodyType bodyType) {
            return of(amount, falling, bodyType, DEFAULT_WATER_TINT);
        }

        /** Creates a one-hot body blend with the generation biome's water tint. */
        public static Cell of(int amount, boolean falling, BodyType bodyType, int waterTint) {
            return switch (bodyType) {
                case OCEAN -> new Cell(amount, falling, bodyType, 255, 0, 0, waterTint);
                case RIVER -> new Cell(amount, falling, bodyType, 0, 255, 0, waterTint);
                case LAKE, AQUIFER, SPRING -> new Cell(amount, falling, bodyType, 0, 0, 255, waterTint);
            };
        }

        private Cell normalized() {
            int boundedOcean = clampByte(oceanWeight);
            int boundedRiver = clampByte(riverWeight);
            int boundedLake = clampByte(lakeWeight);
            if (boundedOcean + boundedRiver + boundedLake == 0) {
                return of(Math.max(1, Math.min(8, amount)), falling,
                        bodyType == null ? BodyType.LAKE : bodyType, waterTint);
            }
            return new Cell(
                    Math.max(1, Math.min(8, amount)),
                    falling,
                    bodyType == null ? BodyType.LAKE : bodyType,
                    boundedOcean,
                    boundedRiver,
                    boundedLake,
                    waterTint & 0xFFFFFF
            );
        }

        private static int clampByte(int value) {
            return Math.max(0, Math.min(255, value));
        }
    }

    /** Inclusive world-Y run in one chunk-local X/Z column. */
    public record WaterSpan(int bottomY, int topY, Cell cell) {
        /** Fixed-point amount represented by the top fluid block. */
        public int amountUnits() {
            return Math.max(1, Math.min(8, cell.amount())) * WaterVolumeChunk.UNITS_PER_BLOCK / 8;
        }
    }

    /** Immutable compact arrays safe to retain on render/network threads. */
    public static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(0L, new int[COLUMN_COUNT + 1],
                new int[0], new int[COLUMN_COUNT], new int[COVER_WORD_COUNT],
                (short) 0, (short) 0, (short) 0, (short) 0);

        private final long revision;
        private final int[] columnOffsets;
        private final int[] encodedSpans;
        private final int[] columnBounds;
        private final int[] surfaceCovered;
        private final short northMask;
        private final short southMask;
        private final short westMask;
        private final short eastMask;

        private Snapshot(long revision, int[] columnOffsets, int[] encodedSpans, int[] columnBounds,
                         int[] surfaceCovered,
                         short northMask, short southMask, short westMask, short eastMask) {
            this.revision = revision;
            this.columnOffsets = columnOffsets;
            this.encodedSpans = encodedSpans;
            this.columnBounds = columnBounds;
            this.surfaceCovered = surfaceCovered;
            this.northMask = northMask;
            this.southMask = southMask;
            this.westMask = westMask;
            this.eastMask = eastMask;
        }

        public long revision() { return revision; }
        public int[] columnOffsets() { return columnOffsets.clone(); }
        public int[] encodedSpans() { return encodedSpans.clone(); }
        public int[] columnBounds() { return columnBounds.clone(); }
        public int[] surfaceCovered() { return surfaceCovered.clone(); }
        public short northMask() { return northMask; }
        public short southMask() { return southMask; }
        public short westMask() { return westMask; }
        public short eastMask() { return eastMask; }

        /** Returns whether a generated block written above this surface covers it. */
        public boolean surfaceCovered(int localX, int localZ) {
            int column = columnIndex(localX, localZ);
            return (surfaceCovered[column >>> 5] & (1 << (column & 31))) != 0;
        }

        /** Approximate primitive storage without cloning diagnostic arrays. */
        public int estimatedBytes() {
            return Integer.BYTES * (encodedSpans.length + columnOffsets.length
                    + columnBounds.length + surfaceCovered.length + 4);
        }

        /** Returns the solid floor below the highest contiguous water body. */
        public int floorY(int localX, int localZ) {
            int packed = columnBounds[columnIndex(localX, localZ)];
            return (short) (packed >>> 16);
        }

        /** Returns the generated span occupying local X/Z and world Y without scanning blocks. */
        public WaterSpan spanAt(int localX, int worldY, int localZ) {
            int column = columnIndex(localX, localZ);
            int first = columnOffsets[column];
            int last = columnOffsets[column + 1];
            for (int spanIndex = first; spanIndex < last; spanIndex++) {
                int offset = spanIndex * SERIALIZED_SPAN_STRIDE;
                int bottom = encodedSpans[offset + 1];
                if (worldY < bottom) {
                    break;
                }
                int top = encodedSpans[offset + 2];
                if (worldY <= top) {
                    return new WaterSpan(bottom, top,
                            decodeCell(encodedSpans[offset + 3], encodedSpans[offset + 4],
                                    encodedSpans[offset + 5]));
                }
            }
            return null;
        }

        /** Returns the highest generated span in a local column. */
        public WaterSpan topSpan(int localX, int localZ) {
            int column = columnIndex(localX, localZ);
            int first = columnOffsets[column];
            int last = columnOffsets[column + 1];
            if (first == last) {
                return null;
            }
            int offset = (last - 1) * SERIALIZED_SPAN_STRIDE;
            return new WaterSpan(encodedSpans[offset + 1], encodedSpans[offset + 2],
                    decodeCell(encodedSpans[offset + 3], encodedSpans[offset + 4], encodedSpans[offset + 5]));
        }

        /** Returns decoded immutable runs for one column. */
        public List<WaterSpan> spansForColumn(int column) {
            if (column < 0 || column >= COLUMN_COUNT) {
                return List.of();
            }
            int first = columnOffsets[column];
            int last = columnOffsets[column + 1];
            if (first == last) {
                return List.of();
            }
            List<WaterSpan> result = new ArrayList<>(last - first);
            for (int spanIndex = first; spanIndex < last; spanIndex++) {
                int offset = spanIndex * SERIALIZED_SPAN_STRIDE;
                result.add(new WaterSpan(
                        encodedSpans[offset + 1],
                        encodedSpans[offset + 2],
                        decodeCell(encodedSpans[offset + 3], encodedSpans[offset + 4], encodedSpans[offset + 5])
                ));
            }
            return List.copyOf(result);
        }
    }
}
