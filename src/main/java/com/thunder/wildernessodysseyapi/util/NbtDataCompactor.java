package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Compacts mod-owned NBT payloads before they are written to disk and restores them
 * after reads. This keeps the on-disk format small without changing how callers
 * interact with the in-memory data.
 */
public final class NbtDataCompactor {

    private static final String MOD_DATA_KEY = ModConstants.MOD_ID;
    private static final String RESOURCE_TABLE_KEY = "_wo_rl_table";
    private static final String RESOURCE_REF_KEY = "_wo_rl_ref";
    private static final String FLATTENED_KEY = "_wo_flattened";
    private static final String FLATTENED_PATH_KEY = "path";
    private static final String FLATTENED_COMPRESSION_KEY = "compression";
    private static final String FLATTENED_PAYLOAD_KEY = "payload";
    private static final String COMPRESSION_DEFLATE = "deflate";
    private static final String BLOB_WRAPPER_TYPE = "_wo_compression";
    private static final String BLOB_DATA_KEY = "_wo_data";
    private static final String BLOB_ORIGINAL_SIZE_KEY = "_wo_size";
    private static final String BLOB_ORIGINAL_TYPE_KEY = "_wo_type";

    private static final int RESOURCE_LENGTH_THRESHOLD = 64;
    private static final int MAX_HISTORY_ENTRIES = 64;
    private static final long HISTORY_RETENTION_MS = TimeUnit.DAYS.toMillis(45);
    private static final int MAX_FLATTEN_DEPTH = 6;
    private static final int BLOB_COMPRESSION_THRESHOLD = 2048;

    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NbtDataCompactor() {
    }

    /**
     * Applies size-saving transforms to mod-owned payloads prior to writing them to disk.
     */
    public static void compactModPayload(CompoundTag root) {
        if (root == null || !root.contains(MOD_DATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag modData = root.getCompound(MOD_DATA_KEY);

        pruneHistories(modData);

        List<String> rlTable = new ArrayList<>();
        Map<String, Integer> rlIndex = new HashMap<>();
        encodeResourceLocations(modData, rlTable, rlIndex);
        if (!rlTable.isEmpty()) {
            ListTag tableTag = new ListTag();
            rlTable.forEach(id -> tableTag.add(StringTag.valueOf(id)));
            modData.put(RESOURCE_TABLE_KEY, tableTag);
        }

        ListTag flattened = new ListTag();
        flatten(modData, "", 0, flattened);
        if (!flattened.isEmpty()) {
            modData.put(FLATTENED_KEY, flattened);
        }

        compressLargeBlobs(modData);
        root.put(MOD_DATA_KEY, modData);
    }

    /**
     * Restores any compacted data back to its expanded form after reading from disk.
     */
    public static void expandModPayload(CompoundTag root) {
        if (root == null || !root.contains(MOD_DATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag modData = root.getCompound(MOD_DATA_KEY);

        decodeFlattened(modData);
        decompressBlobs(modData);
        decodeResourceLocations(modData);

        root.put(MOD_DATA_KEY, modData);
    }

    private static void pruneHistories(CompoundTag tag) {
        for (String key : new HashSet<>(tag.getAllKeys())) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                pruneHistories(compound);
            } else if (child instanceof ListTag list
                    && list.getElementType() == Tag.TAG_STRING
                    && isHistoryKey(key)) {
                List<String> trimmed = filterHistory(list);
                ListTag replacement = new ListTag();
                trimmed.forEach(entry -> replacement.add(StringTag.valueOf(entry)));
                tag.put(key, replacement);
            }
        }
    }

    private static boolean isHistoryKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("history") || lower.contains("log");
    }

    private static List<String> filterHistory(ListTag list) {
        List<String> entries = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - HISTORY_RETENTION_MS;
        for (Tag element : list) {
            String value = element.getAsString();
            long timestamp = extractTimestamp(value);
            if (timestamp > 0 && timestamp < cutoff) {
                continue;
            }
            entries.add(value);
        }
        int start = Math.max(0, entries.size() - MAX_HISTORY_ENTRIES);
        return entries.subList(start, entries.size());
    }

    private static long extractTimestamp(String value) {
        try {
            if (value.length() >= 19 && Character.isDigit(value.charAt(0))) {
                LocalDateTime parsed = LocalDateTime.parse(value.substring(0, 19), LOG_TIMESTAMP);
                return parsed.toInstant(ZoneOffset.UTC).toEpochMilli();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static void encodeResourceLocations(CompoundTag tag, List<String> rlTable, Map<String, Integer> rlIndex) {
        for (String key : new HashSet<>(tag.getAllKeys())) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                encodeResourceLocations(compound, rlTable, rlIndex);
            } else if (child instanceof ListTag list && list.getElementType() == Tag.TAG_COMPOUND) {
                for (Tag element : list) {
                    if (element instanceof CompoundTag compoundElement) {
                        encodeResourceLocations(compoundElement, rlTable, rlIndex);
                    }
                }
            } else if (child instanceof StringTag stringTag) {
                String value = stringTag.getAsString();
                if (value.length() < RESOURCE_LENGTH_THRESHOLD) {
                    continue;
                }
                ResourceLocation rl = ResourceLocation.tryParse(value);
                if (rl == null) {
                    continue;
                }
                String id = rl.toString();
                Integer idx = rlIndex.get(id);
                if (idx == null) {
                    idx = rlTable.size();
                    rlTable.add(id);
                    rlIndex.put(id, idx);
                }
                CompoundTag ref = new CompoundTag();
                ref.putInt(RESOURCE_REF_KEY, idx);
                tag.put(key, ref);
            }
        }
    }

    private static void decodeResourceLocations(CompoundTag tag) {
        if (!tag.contains(RESOURCE_TABLE_KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag tableTag = tag.getList(RESOURCE_TABLE_KEY, Tag.TAG_STRING);
        List<String> table = new ArrayList<>(tableTag.size());
        tableTag.forEach(entry -> table.add(entry.getAsString()));

        decodeResourceLocationsRecursive(tag, table);
        tag.remove(RESOURCE_TABLE_KEY);
    }

    private static void decodeResourceLocationsRecursive(CompoundTag tag, List<String> table) {
        for (String key : new HashSet<>(tag.getAllKeys())) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                if (compound.contains(RESOURCE_REF_KEY, Tag.TAG_INT)) {
                    int idx = compound.getInt(RESOURCE_REF_KEY);
                    if (idx >= 0 && idx < table.size()) {
                        tag.putString(key, table.get(idx));
                    } else {
                        tag.remove(key);
                    }
                    continue;
                }
                decodeResourceLocationsRecursive(compound, table);
            } else if (child instanceof ListTag list && list.getElementType() == Tag.TAG_COMPOUND) {
                for (Tag element : list) {
                    if (element instanceof CompoundTag compoundElement) {
                        decodeResourceLocationsRecursive(compoundElement, table);
                    }
                }
            }
        }
    }

    private static void flatten(CompoundTag tag, String path, int depth, ListTag sink) {
        for (String key : new HashSet<>(tag.getAllKeys())) {
            Tag child = tag.get(key);
            if (!(child instanceof CompoundTag compound)) {
                continue;
            }
            String childPath = path.isEmpty() ? key : path + "." + key;
            if (depth >= MAX_FLATTEN_DEPTH) {
                byte[] payload = serializeCompound(compound);
                if (payload.length == 0) {
                    continue;
                }
                CompoundTag flattened = new CompoundTag();
                flattened.putString(FLATTENED_PATH_KEY, childPath);
                flattened.putString(FLATTENED_COMPRESSION_KEY, COMPRESSION_DEFLATE);
                flattened.putByteArray(FLATTENED_PAYLOAD_KEY, payload);
                sink.add(flattened);
                tag.remove(key);
            } else {
                flatten(compound, childPath, depth + 1, sink);
            }
        }
    }

    private static void decodeFlattened(CompoundTag tag) {
        if (!tag.contains(FLATTENED_KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag flattened = tag.getList(FLATTENED_KEY, Tag.TAG_COMPOUND);
        for (Tag element : flattened) {
            if (!(element instanceof CompoundTag entry)) {
                continue;
            }
            String path = entry.getString(FLATTENED_PATH_KEY);
            byte[] payload = entry.getByteArray(FLATTENED_PAYLOAD_KEY);
            String compression = entry.getString(FLATTENED_COMPRESSION_KEY);
            try {
                CompoundTag restored = deserializeCompound(payload, compression);
                if (restored != null) {
                    insertAtPath(tag, path, restored);
                }
            } catch (IOException e) {
                ModConstants.LOGGER.debug("Failed to restore flattened tag {}.", path, e);
            }
        }
        tag.remove(FLATTENED_KEY);
    }

    private static void insertAtPath(CompoundTag root, String path, CompoundTag payload) {
        String[] parts = path.split("\\.");
        CompoundTag current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String segment = parts[i];
            CompoundTag next = current.contains(segment, Tag.TAG_COMPOUND)
                    ? current.getCompound(segment)
                    : new CompoundTag();
            current.put(segment, next);
            current = next;
        }
        current.put(parts[parts.length - 1], payload);
    }

    private static byte[] serializeCompound(CompoundTag compound) {
        try (ByteArrayOutputStream raw = new ByteArrayOutputStream();
             DeflaterOutputStream deflater = new DeflaterOutputStream(raw);
             DataOutputStream data = new DataOutputStream(deflater)) {
            NbtIo.write(compound, data);
            deflater.finish();
            return raw.toByteArray();
        } catch (IOException e) {
            ModConstants.LOGGER.debug("Failed to serialize nested compound for compaction.", e);
            return new byte[0];
        }
    }

    private static CompoundTag deserializeCompound(byte[] payload, String compression) throws IOException {
        try (ByteArrayInputStream raw = new ByteArrayInputStream(payload);
             InputStreamWrapper wrapper = new InputStreamWrapper(raw, compression);
             DataInputStream data = new DataInputStream(wrapper.stream())) {
            return NbtIo.read(data);
        }
    }

    private static void compressLargeBlobs(CompoundTag tag) {
        for (String key : new HashSet<>(tag.getAllKeys())) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                compressLargeBlobs(compound);
            } else if (child instanceof ByteArrayTag bytes && bytes.size() >= BLOB_COMPRESSION_THRESHOLD) {
                tag.put(key, wrapCompressed(bytes.getAsByteArray(), Tag.TAG_BYTE_ARRAY));
            } else if (child instanceof IntArrayTag ints && ints.getAsIntArray().length * Integer.BYTES >= BLOB_COMPRESSION_THRESHOLD) {
                tag.put(key, wrapCompressed(toByteArray(ints.getAsIntArray()), Tag.TAG_INT_ARRAY));
            }
        }
    }

    private static void decompressBlobs(CompoundTag tag) {
        for (String key : new HashSet<>(tag.getAllKeys())) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                if (isCompressedBlob(compound)) {
                    Tag restored = unwrapCompressed(compound);
                    if (restored != null) {
                        tag.put(key, restored);
                        continue;
                    }
                }
                decompressBlobs(compound);
            }
        }
    }

    private static boolean isCompressedBlob(CompoundTag tag) {
        return tag.contains(BLOB_WRAPPER_TYPE, Tag.TAG_STRING) && tag.contains(BLOB_DATA_KEY, Tag.TAG_BYTE_ARRAY);
    }

    private static CompoundTag wrapCompressed(byte[] data, byte originalType) {
        CompoundTag wrapper = new CompoundTag();
        wrapper.putString(BLOB_WRAPPER_TYPE, COMPRESSION_DEFLATE);
        wrapper.putInt(BLOB_ORIGINAL_SIZE_KEY, data.length);
        wrapper.putByte(BLOB_ORIGINAL_TYPE_KEY, originalType);
        wrapper.putByteArray(BLOB_DATA_KEY, compressBytes(data));
        return wrapper;
    }

    private static Tag unwrapCompressed(CompoundTag wrapper) {
        String compression = wrapper.getString(BLOB_WRAPPER_TYPE);
        byte[] compressed = wrapper.getByteArray(BLOB_DATA_KEY);
        byte originalType = wrapper.getByte(BLOB_ORIGINAL_TYPE_KEY);
        try {
            byte[] restored = decompressBytes(compressed, compression);
            return switch (originalType) {
                case Tag.TAG_BYTE_ARRAY -> new ByteArrayTag(restored);
                case Tag.TAG_INT_ARRAY -> new IntArrayTag(toIntArray(restored));
                default -> null;
            };
        } catch (IOException e) {
            ModConstants.LOGGER.debug("Failed to decompress custom blob.", e);
            return null;
        }
    }

    private static byte[] compressBytes(byte[] data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DeflaterOutputStream deflater = new DeflaterOutputStream(out)) {
            deflater.write(data);
            deflater.finish();
            return out.toByteArray();
        } catch (IOException e) {
            ModConstants.LOGGER.debug("Failed to compress blob.", e);
            return data;
        }
    }

    private static byte[] decompressBytes(byte[] data, String compression) throws IOException {
        ByteArrayInputStream raw = new ByteArrayInputStream(data);
        try (InputStreamWrapper wrapper = new InputStreamWrapper(raw, compression);
             DataInputStream in = new DataInputStream(wrapper.stream())) {
            return in.readAllBytes();
        }
    }

    private static byte[] toByteArray(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(ints.length * Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        for (int value : ints) {
            buffer.putInt(value);
        }
        return buffer.array();
    }

    private static int[] toIntArray(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int[] ints = new int[buffer.remaining() / Integer.BYTES];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = buffer.getInt();
        }
        return ints;
    }

    /**
     * Simple wrapper to defer inflater allocation until needed and still participate in
     * try-with-resources cleanup.
     */
    private static final class InputStreamWrapper implements AutoCloseable {
        private final ByteArrayInputStream raw;
        private final InflaterInputStream inflater;

        private InputStreamWrapper(ByteArrayInputStream raw, String compression) {
            this.raw = raw;
            this.inflater = COMPRESSION_DEFLATE.equals(compression) ? new InflaterInputStream(raw) : null;
        }

        public java.io.InputStream stream() {
            return inflater != null ? inflater : raw;
        }

        @Override
        public void close() throws IOException {
            if (inflater != null) {
                inflater.close();
            }
            raw.close();
        }
    }
}
