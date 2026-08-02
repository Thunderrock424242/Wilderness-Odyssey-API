package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;

import java.util.Arrays;

/**
 * Immutable render-thread view assembled from generated spans and sparse runtime overrides.
 *
 * <p>All column resolution happens when a network/chunk-local update arrives.
 * Render and camera code read primitive arrays only; they never scan blocks,
 * heightmaps, attachments, or mutable authority state.</p>
 */
public final class ClientWaterChunkSnapshot {

    private static final short NO_SURFACE = Short.MIN_VALUE;
    private static final int COLUMN_COUNT = 16 * 16;

    private final int chunkX;
    private final int chunkZ;
    private final GeneratedWaterChunk.Snapshot generated;
    private final long sparseRevision;
    private final int[] sparseCells;
    private final short[] surfaceY = new short[COLUMN_COUNT];
    private final short[] floorY = new short[COLUMN_COUNT];
    private final byte[] fillAmount = new byte[COLUMN_COUNT];
    private final byte[] oceanWeight = new byte[COLUMN_COUNT];
    private final byte[] riverWeight = new byte[COLUMN_COUNT];
    private final byte[] lakeWeight = new byte[COLUMN_COUNT];
    private final float[] velocityX = new float[COLUMN_COUNT];
    private final float[] velocityZ = new float[COLUMN_COUNT];
    private final byte[] bodyType = new byte[COLUMN_COUNT];
    private final int[] waterTint = new int[COLUMN_COUNT];
    private final boolean[] surfaceCovered = new boolean[COLUMN_COUNT];

    /** Builds and resolves one complete immutable chunk snapshot. */
    public ClientWaterChunkSnapshot(
            int chunkX,
            int chunkZ,
            GeneratedWaterChunk.Snapshot generated,
            long sparseRevision,
            int[] sparseCells
    ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.generated = generated;
        this.sparseRevision = Math.max(0L, sparseRevision);
        this.sparseCells = sortedSparseCopy(sparseCells);
        Arrays.fill(surfaceY, NO_SURFACE);
        Arrays.fill(floorY, NO_SURFACE);
        resolveColumns();
    }

    /** Returns a copy with a newer generated baseline and the same sparse overrides. */
    public ClientWaterChunkSnapshot withGenerated(GeneratedWaterChunk.Snapshot nextGenerated) {
        return new ClientWaterChunkSnapshot(chunkX, chunkZ, nextGenerated, sparseRevision, sparseCells);
    }

    /** Returns a copy with a newer sparse revision and the same generated baseline. */
    public ClientWaterChunkSnapshot withSparse(long nextRevision, int[] nextSparseCells) {
        if (nextRevision < sparseRevision) {
            return this;
        }
        return new ClientWaterChunkSnapshot(chunkX, chunkZ, generated, nextRevision, nextSparseCells);
    }

    /** Returns a copy with one exact contiguous sparse delta, or {@code null} on a revision gap. */
    public ClientWaterChunkSnapshot withSparseDelta(
            long fromRevision,
            long nextRevision,
            int[] upserts,
            int[] tombstones
    ) {
        if (nextRevision <= sparseRevision) {
            return this;
        }
        if (fromRevision != sparseRevision || nextRevision <= fromRevision) {
            return null;
        }
        int[] merged = WaterVolumeChunk.mergeNetworkDelta(sparseCells, upserts, tombstones);
        return new ClientWaterChunkSnapshot(chunkX, chunkZ, generated, nextRevision, merged);
    }

    /** Samples one resolved surface column, or {@link Column#DRY}. */
    public Column column(int localX, int localZ) {
        int index = columnIndex(localX, localZ);
        int top = surfaceY[index];
        if (top == NO_SURFACE) {
            return Column.DRY;
        }
        return new Column(
                true,
                top,
                floorY[index],
                Byte.toUnsignedInt(fillAmount[index]),
                Byte.toUnsignedInt(oceanWeight[index]),
                Byte.toUnsignedInt(riverWeight[index]),
                Byte.toUnsignedInt(lakeWeight[index]),
                velocityX[index],
                velocityZ[index],
                GeneratedWaterChunk.BodyType.values()[Byte.toUnsignedInt(bodyType[index])],
                waterTint[index],
                surfaceCovered[index]
        );
    }

    /** Returns whether resolved Wilderness water occupies a block cell. */
    public boolean contains(int localX, int worldY, int localZ) {
        int packed = pack(localX, worldY, localZ);
        int sparseOffset = sparseOffset(packed);
        if (sparseOffset >= 0) {
            return isVisibleSparseWater(sparseOffset);
        }
        return generated != null && generated.spanAt(localX, worldY, localZ) != null;
    }

    /** Returns fixed-point volume for one resolved block cell. */
    public int amountUnits(int localX, int worldY, int localZ) {
        int sparseOffset = sparseOffset(pack(localX, worldY, localZ));
        if (sparseOffset >= 0) {
            return isVisibleSparseWater(sparseOffset)
                    ? Math.max(0, sparseCells[sparseOffset + 1])
                    : 0;
        }
        GeneratedWaterChunk.WaterSpan span = generated == null
                ? null
                : generated.spanAt(localX, worldY, localZ);
        return span == null ? 0 : span.amountUnits();
    }

    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public long generatedRevision() { return generated == null ? 0L : generated.revision(); }
    public long sparseRevision() { return sparseRevision; }
    public short northMask() { return generated == null ? 0 : generated.northMask(); }
    public short southMask() { return generated == null ? 0 : generated.southMask(); }
    public short westMask() { return generated == null ? 0 : generated.westMask(); }
    public short eastMask() { return generated == null ? 0 : generated.eastMask(); }

    /** Approximate primitive memory retained by this snapshot. */
    public int estimatedBytes() {
        int generatedBytes = generatedEstimatedBytes();
        return generatedBytes + Integer.BYTES * sparseCells.length
                + Short.BYTES * (surfaceY.length + floorY.length)
                + fillAmount.length + oceanWeight.length + riverWeight.length + lakeWeight.length
                + Float.BYTES * (velocityX.length + velocityZ.length)
                + bodyType.length + Integer.BYTES * waterTint.length + surfaceCovered.length;
    }

    /** Compact generated-baseline bytes retained by this snapshot. */
    public int generatedEstimatedBytes() {
        return generated == null ? 0 : generated.estimatedBytes();
    }

    /** Returns a defensive sparse-array copy for atomic snapshot replacement. */
    public int[] sparseCells() {
        return sparseCells.clone();
    }

    private void resolveColumns() {
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                resolveColumn(localX, localZ);
            }
        }
    }

    private void resolveColumn(int localX, int localZ) {
        int column = columnIndex(localX, localZ);
        GeneratedWaterChunk.WaterSpan generatedTop = generated == null
                ? null
                : generated.topSpan(localX, localZ);
        int candidateTop = generatedTop == null ? Integer.MIN_VALUE : generatedTop.topY();
        int generatedFloor = generatedTop == null
                ? Integer.MAX_VALUE : generated.floorY(localX, localZ);
        int searchFloor = generatedFloor;

        // Sparse chunks remain small. This pass runs only on snapshot replacement
        // and finds runtime water above the immutable generated surface.
        for (int offset = 0; offset < sparseCells.length; offset += WaterVolumeChunk.SERIALIZED_CELL_STRIDE) {
            int packed = sparseCells[offset];
            if ((packed & 0xFF) != column) {
                continue;
            }
            int y = unpackY(packed);
            if (isVisibleSparseWater(offset)) {
                candidateTop = Math.max(candidateTop, y);
                searchFloor = Math.min(searchFloor, y - 1);
            }
        }
        if (candidateTop == Integer.MIN_VALUE) {
            return;
        }

        for (int y = candidateTop; y >= searchFloor && y >= -2048; y--) {
            int sparseOffset = sparseOffset(pack(localX, y, localZ));
            if (sparseOffset >= 0) {
                if (isVisibleSparseWater(sparseOffset)) {
                    int sparseVolume = Math.max(0, sparseCells[sparseOffset + 1]);
                    GeneratedWaterChunk.WaterSpan atY = generated == null
                            ? null : generated.spanAt(localX, y, localZ);
                    setResolvedColumn(column, y, searchFloor,
                            amountToLevel(sparseVolume), atY,
                            atY != null && generated.surfaceCovered(localX, localZ)
                                    && generatedTop != null && y == generatedTop.topY(),
                            finiteOrZero(Float.intBitsToFloat(sparseCells[sparseOffset + 2])),
                            finiteOrZero(Float.intBitsToFloat(sparseCells[sparseOffset + 4])));
                    return;
                }
                continue;
            }
            GeneratedWaterChunk.WaterSpan span = generated == null
                    ? null
                    : generated.spanAt(localX, y, localZ);
            if (span != null) {
                setResolvedColumn(column, y, generatedFloor, span.cell().amount(), span,
                        generated.surfaceCovered(localX, localZ), 0.0f, 0.0f);
                return;
            }
        }
    }

    private void setResolvedColumn(
            int column,
            int topY,
            int resolvedFloorY,
            int amount,
            GeneratedWaterChunk.WaterSpan bodySpan,
            boolean covered,
            float resolvedVelocityX,
            float resolvedVelocityZ
    ) {
        surfaceY[column] = (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, topY));
        floorY[column] = (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, resolvedFloorY));
        fillAmount[column] = (byte) Math.max(1, Math.min(8, amount));
        surfaceCovered[column] = covered;
        velocityX[column] = finiteOrZero(resolvedVelocityX);
        velocityZ[column] = finiteOrZero(resolvedVelocityZ);
        if (bodySpan == null) {
            lakeWeight[column] = (byte) 255;
            bodyType[column] = (byte) GeneratedWaterChunk.BodyType.LAKE.ordinal();
            waterTint[column] = GeneratedWaterChunk.Cell.DEFAULT_WATER_TINT;
        } else {
            oceanWeight[column] = (byte) bodySpan.cell().oceanWeight();
            riverWeight[column] = (byte) bodySpan.cell().riverWeight();
            lakeWeight[column] = (byte) bodySpan.cell().lakeWeight();
            bodyType[column] = (byte) bodySpan.cell().bodyType().ordinal();
            waterTint[column] = bodySpan.cell().waterTint();
        }
    }

    private int sparseOffset(int packedPosition) {
        int low = 0;
        int high = sparseCells.length / WaterVolumeChunk.SERIALIZED_CELL_STRIDE - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int offset = middle * WaterVolumeChunk.SERIALIZED_CELL_STRIDE;
            int comparison = Integer.compareUnsigned(sparseCells[offset], packedPosition);
            if (comparison < 0) {
                low = middle + 1;
            } else if (comparison > 0) {
                high = middle - 1;
            } else {
                return offset;
            }
        }
        return -1;
    }

    private boolean isVisibleSparseWater(int offset) {
        return sparseCells[offset + 1] > 0
                && (sparseCells[offset + 5] & WaterVolumeChunk.FLAG_DISPLACEMENT_RESERVOIR) == 0;
    }

    private static int[] sortedSparseCopy(int[] source) {
        if (source == null || source.length == 0) {
            return new int[0];
        }
        if (source.length % WaterVolumeChunk.SERIALIZED_CELL_STRIDE != 0) {
            throw new IllegalArgumentException("Invalid sparse water snapshot");
        }
        int entries = source.length / WaterVolumeChunk.SERIALIZED_CELL_STRIDE;
        Integer[] order = new Integer[entries];
        for (int index = 0; index < entries; index++) {
            order[index] = index;
        }
        Arrays.sort(order, (first, second) -> Integer.compareUnsigned(
                source[first * WaterVolumeChunk.SERIALIZED_CELL_STRIDE],
                source[second * WaterVolumeChunk.SERIALIZED_CELL_STRIDE]
        ));
        int[] sorted = new int[source.length];
        for (int output = 0; output < entries; output++) {
            System.arraycopy(source,
                    order[output] * WaterVolumeChunk.SERIALIZED_CELL_STRIDE,
                    sorted,
                    output * WaterVolumeChunk.SERIALIZED_CELL_STRIDE,
                    WaterVolumeChunk.SERIALIZED_CELL_STRIDE);
        }
        return sorted;
    }

    private static int amountToLevel(int volumeUnits) {
        return Math.max(1, Math.min(8, (volumeUnits * 8 + WaterVolumeChunk.UNITS_PER_BLOCK - 1)
                / WaterVolumeChunk.UNITS_PER_BLOCK));
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static int columnIndex(int localX, int localZ) {
        return (localX & 15) | ((localZ & 15) << 4);
    }

    private static int pack(int localX, int worldY, int localZ) {
        return (localX & 15) | ((localZ & 15) << 4) | ((worldY & 0xFFF) << 8);
    }

    private static int unpackY(int packed) {
        int y = (packed >>> 8) & 0xFFF;
        return (y & 0x800) == 0 ? y : y - 0x1000;
    }

    /** Resolved top surface, optical body blend, and sparse canonical current for one X/Z column. */
    public record Column(
            boolean wet,
            int surfaceBlockY,
            int floorY,
            int amount,
            int oceanWeight,
            int riverWeight,
            int lakeWeight,
            float velocityX,
            float velocityZ,
            GeneratedWaterChunk.BodyType bodyType,
            int waterTint,
            boolean surfaceCovered
    ) {
        public static final Column DRY = new Column(false, 0, 0, 0, 0, 0, 0, 0.0f, 0.0f,
                GeneratedWaterChunk.BodyType.LAKE, GeneratedWaterChunk.Cell.DEFAULT_WATER_TINT, false);

        /** Flat Minecraft fluid height before GPU/CPU wave displacement. */
        public float baseSurfaceY() {
            return wet ? surfaceBlockY + (amount >= 8 ? 8.0f / 9.0f : amount / 8.0f) : Float.NaN;
        }

        /** Approximate water-column thickness used for absorption. */
        public float depth() {
            return wet ? Math.max(0.0f, baseSurfaceY() - (floorY + 1.0f)) : 0.0f;
        }

        /** Horizontal canonical flow magnitude retained for client-only material motion. */
        public float currentSpeed() {
            return wet ? (float) Math.sqrt(velocityX * velocityX + velocityZ * velocityZ) : 0.0f;
        }
    }
}
