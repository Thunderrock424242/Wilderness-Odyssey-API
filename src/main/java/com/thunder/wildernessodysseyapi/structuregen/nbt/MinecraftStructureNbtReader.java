package com.thunder.wildernessodysseyapi.structuregen.nbt;

import com.thunder.wildernessodysseyapi.structuregen.StructureGenConstants;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads compressed or raw Minecraft structure-template NBT into the canonical StructureGen model.
 *
 * <p>The reader deliberately parses palette compounds itself instead of resolving them through a
 * block registry. That keeps unknown or temporarily unavailable modded block IDs intact rather
 * than allowing Minecraft's runtime loader to replace them with air.</p>
 */
public final class MinecraftStructureNbtReader {

    private static final long MAX_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_ACCOUNTED_NBT_BYTES = 1024L * 1024L * 1024L;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "size", "blocks", "palette", "palettes", "entities", "DataVersion", "structuregen"
    );
    private static final Set<String> PALETTE_FIELDS = Set.of("Name", "Properties");
    private static final Set<String> BLOCK_FIELDS = Set.of("pos", "state", "nbt");
    private static final Set<String> ENTITY_FIELDS = Set.of("pos", "blockPos", "nbt");
    private static final Set<String> STRUCTUREGEN_FIELDS = Set.of(
            "formatVersion", "name", "metadata", "markers", "blockMarkers"
    );

    /** Reads one structure, deriving its model name from the NBT filename. */
    public StructureModel read(Path path) throws IOException {
        return read(path, structureName(path));
    }

    /** Reads one structure using an explicit logical name for the resulting model. */
    public StructureModel read(Path path, String structureName) throws IOException {
        Objects.requireNonNull(path, "path");
        long fileSize = Files.size(path);
        if (fileSize > MAX_FILE_BYTES) {
            throw new IOException("Structure NBT exceeds the " + MAX_FILE_BYTES + " byte file safety limit: " + path);
        }
        return read(readCompressedThenRaw(path), structureName);
    }

    /**
     * Converts an already decoded root compound into a canonical model.
     *
     * @param root decoded structure root
     * @param structureName fallback name when no StructureGen metadata is present
     */
    public StructureModel read(CompoundTag root, String structureName) throws StructureNbtFormatException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(structureName, "structureName");

        TreeSet<String> unsupported = new TreeSet<>();
        recordUnknownFields(root, ROOT_FIELDS, "root", unsupported);
        StructureGenData structureGen = readStructureGenData(root, unsupported);
        StructureSize size = readSize(root);
        List<List<StructureBlockState>> palettes = readPalettes(root, unsupported);
        Map<StructurePosition, List<String>> blockMarkers = new HashMap<>(structureGen.blockMarkers());
        List<StructureBlock> blocks = readBlocks(root, size, palettes, blockMarkers, unsupported);
        List<StructureEntity> entities = readEntities(root, size, unsupported);

        // Marker entries with no corresponding block stay preserved in rawRootSnbt and are called out explicitly.
        blockMarkers.keySet().stream().sorted().forEach(position ->
                unsupported.add("structuregen.blockMarkers" + position.display()));

        int dataVersion = readDataVersion(root);
        String name = structureGen.name() == null || structureGen.name().isBlank()
                ? structureName
                : structureGen.name();

        return new StructureModel(
                name,
                size,
                blocks,
                entities,
                dataVersion,
                structureGen.metadata(),
                structureGen.markers(),
                palettes,
                rawRootSnbt(root, blockMarkers.keySet()),
                List.copyOf(unsupported)
        );
    }

    private CompoundTag readCompressedThenRaw(Path path) throws IOException {
        IOException compressedFailure;
        try {
            return NbtIo.readCompressed(path, NbtAccounter.create(MAX_ACCOUNTED_NBT_BYTES));
        } catch (IOException exception) {
            compressedFailure = exception;
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            return NbtIo.read(input, NbtAccounter.create(MAX_ACCOUNTED_NBT_BYTES));
        } catch (IOException rawFailure) {
            IOException failure = new IOException(
                    "Unable to read structure as gzip-compressed or raw NBT: " + path,
                    rawFailure
            );
            failure.addSuppressed(compressedFailure);
            throw failure;
        }
    }

    private StructureSize readSize(CompoundTag root) throws StructureNbtFormatException {
        ListTag values = requireList(root, "size", Tag.TAG_INT, 3, "root.size");
        StructureSize size = new StructureSize(values.getInt(0), values.getInt(1), values.getInt(2));
        if (size.x() < 0 || size.y() < 0 || size.z() < 0) {
            throw format("root.size", "dimensions may not be negative; got " + size.display());
        }
        return size;
    }

    private int readDataVersion(CompoundTag root) throws StructureNbtFormatException {
        if (!root.contains("DataVersion")) {
            return -1;
        }
        if (!root.contains("DataVersion", Tag.TAG_ANY_NUMERIC)) {
            throw format("root.DataVersion", "must be a numeric tag");
        }
        return root.getInt("DataVersion");
    }

    private List<List<StructureBlockState>> readPalettes(
            CompoundTag root,
            Set<String> unsupported
    ) throws StructureNbtFormatException {
        boolean hasSingle = root.contains("palette");
        boolean hasMultiple = root.contains("palettes");
        if (hasSingle == hasMultiple) {
            throw format("root", "must contain exactly one of 'palette' or 'palettes'");
        }

        List<List<StructureBlockState>> palettes = new ArrayList<>();
        if (hasSingle) {
            palettes.add(readPalette(requireList(root, "palette", Tag.TAG_COMPOUND, -1, "root.palette"),
                    "palette", unsupported));
        } else {
            ListTag outer = requireList(root, "palettes", Tag.TAG_LIST, -1, "root.palettes");
            if (outer.isEmpty()) {
                throw format("root.palettes", "must contain at least one palette");
            }
            for (int paletteIndex = 0; paletteIndex < outer.size(); paletteIndex++) {
                Tag entry = outer.get(paletteIndex);
                if (!(entry instanceof ListTag paletteTag)) {
                    throw format("root.palettes[" + paletteIndex + "]", "must be a list of compounds");
                }
                requireElementType(paletteTag, Tag.TAG_COMPOUND, "root.palettes[" + paletteIndex + "]");
                palettes.add(readPalette(paletteTag, "palettes[" + paletteIndex + "]", unsupported));
            }
        }

        int expectedSize = palettes.get(0).size();
        for (int index = 1; index < palettes.size(); index++) {
            if (palettes.get(index).size() != expectedSize) {
                throw format("root.palettes[" + index + "]",
                        "palette size " + palettes.get(index).size()
                                + " does not match primary palette size " + expectedSize);
            }
        }
        return List.copyOf(palettes);
    }

    private List<StructureBlockState> readPalette(
            ListTag palette,
            String location,
            Set<String> unsupported
    ) throws StructureNbtFormatException {
        List<StructureBlockState> states = new ArrayList<>(palette.size());
        for (int stateIndex = 0; stateIndex < palette.size(); stateIndex++) {
            CompoundTag stateTag = requireCompound(palette.get(stateIndex), location + "[" + stateIndex + "]");
            String stateLocation = location + "[" + stateIndex + "]";
            requireString(stateTag, "Name", stateLocation + ".Name");
            String blockId = stateTag.getString("Name");
            ResourceLocation parsed = ResourceLocation.tryParse(blockId);
            if (parsed == null || !blockId.contains(":") || !parsed.toString().equals(blockId)) {
                throw format(stateLocation + ".Name", "invalid explicit resource location '" + blockId + "'");
            }

            Map<String, String> properties = new TreeMap<>();
            if (stateTag.contains("Properties")) {
                if (!stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
                    throw format(stateLocation + ".Properties", "must be a compound of string values");
                }
                CompoundTag propertyTag = stateTag.getCompound("Properties");
                for (String propertyName : propertyTag.getAllKeys()) {
                    if (!(propertyTag.get(propertyName) instanceof StringTag)) {
                        throw format(stateLocation + ".Properties." + propertyName, "must be a string tag");
                    }
                    properties.put(propertyName, propertyTag.getString(propertyName));
                }
            }
            recordUnknownFields(stateTag, PALETTE_FIELDS, stateLocation, unsupported);
            states.add(new StructureBlockState(blockId, properties, unknownSnbt(stateTag, PALETTE_FIELDS)));
        }
        return List.copyOf(states);
    }

    private List<StructureBlock> readBlocks(
            CompoundTag root,
            StructureSize size,
            List<List<StructureBlockState>> palettes,
            Map<StructurePosition, List<String>> blockMarkers,
            Set<String> unsupported
    ) throws StructureNbtFormatException {
        ListTag blockTags = requireList(root, "blocks", Tag.TAG_COMPOUND, -1, "root.blocks");
        List<StructureBlock> blocks = new ArrayList<>(blockTags.size());
        Set<StructurePosition> positions = new HashSet<>();

        for (int blockIndex = 0; blockIndex < blockTags.size(); blockIndex++) {
            String location = "blocks[" + blockIndex + "]";
            CompoundTag entry = requireCompound(blockTags.get(blockIndex), location);
            StructurePosition position = readIntegerPosition(entry, "pos", location + ".pos");
            if (!size.contains(position)) {
                throw format(location + ".pos",
                        "position " + position.display() + " lies outside size " + size.display());
            }
            if (!positions.add(position)) {
                throw format(location + ".pos", "duplicate block coordinate " + position.display());
            }
            if (!entry.contains("state", Tag.TAG_INT)) {
                throw format(location + ".state", "must be an integer palette index");
            }
            int stateIndex = entry.getInt("state");
            for (int paletteIndex = 0; paletteIndex < palettes.size(); paletteIndex++) {
                if (stateIndex < 0 || stateIndex >= palettes.get(paletteIndex).size()) {
                    throw format(location + ".state", "palette index " + stateIndex
                            + " is outside palette " + paletteIndex + " size " + palettes.get(paletteIndex).size());
                }
            }

            String blockEntitySnbt = null;
            if (entry.contains("nbt")) {
                if (!entry.contains("nbt", Tag.TAG_COMPOUND)) {
                    throw format(location + ".nbt", "must be a compound tag");
                }
                blockEntitySnbt = entry.getCompound("nbt").toString();
            }
            recordUnknownFields(entry, BLOCK_FIELDS, location, unsupported);
            List<String> markers = blockMarkers.remove(position);
            blocks.add(new StructureBlock(
                    position,
                    palettes.get(0).get(stateIndex),
                    blockEntitySnbt,
                    markers == null ? List.of() : markers,
                    unknownSnbt(entry, BLOCK_FIELDS),
                    stateIndex
            ));
        }
        return List.copyOf(blocks);
    }

    private List<StructureEntity> readEntities(
            CompoundTag root,
            StructureSize size,
            Set<String> unsupported
    ) throws StructureNbtFormatException {
        ListTag entityTags = requireList(root, "entities", Tag.TAG_COMPOUND, -1, "root.entities");
        List<StructureEntity> entities = new ArrayList<>(entityTags.size());
        for (int entityIndex = 0; entityIndex < entityTags.size(); entityIndex++) {
            String location = "entities[" + entityIndex + "]";
            CompoundTag entry = requireCompound(entityTags.get(entityIndex), location);
            ListTag positionTag = requireList(entry, "pos", Tag.TAG_DOUBLE, 3, location + ".pos");
            List<Double> position = List.of(
                    positionTag.getDouble(0), positionTag.getDouble(1), positionTag.getDouble(2)
            );
            if (position.stream().anyMatch(value -> !Double.isFinite(value))) {
                throw format(location + ".pos", "coordinates must be finite doubles");
            }
            if (!containsEntity(size, position)) {
                throw format(location + ".pos", "position " + position + " lies outside size " + size.display());
            }
            StructurePosition blockPosition = readIntegerPosition(entry, "blockPos", location + ".blockPos");
            if (!size.contains(blockPosition)) {
                throw format(location + ".blockPos",
                        "position " + blockPosition.display() + " lies outside size " + size.display());
            }
            if (!entry.contains("nbt", Tag.TAG_COMPOUND)) {
                throw format(location + ".nbt", "must be a compound tag");
            }
            recordUnknownFields(entry, ENTITY_FIELDS, location, unsupported);
            entities.add(new StructureEntity(
                    position,
                    blockPosition,
                    entry.getCompound("nbt").toString(),
                    unknownSnbt(entry, ENTITY_FIELDS)
            ));
        }
        return List.copyOf(entities);
    }

    private StructureGenData readStructureGenData(
            CompoundTag root,
            Set<String> unsupported
    ) throws StructureNbtFormatException {
        if (!root.contains("structuregen")) {
            return StructureGenData.empty();
        }
        if (!root.contains("structuregen", Tag.TAG_COMPOUND)) {
            unsupported.add("root.structuregen");
            return StructureGenData.empty();
        }

        CompoundTag tag = root.getCompound("structuregen");
        recordUnknownFields(tag, STRUCTUREGEN_FIELDS, "structuregen", unsupported);
        String name = readOptionalString(tag, "name", "structuregen.name", unsupported);

        if (tag.contains("formatVersion") && !tag.contains("formatVersion", Tag.TAG_ANY_NUMERIC)) {
            unsupported.add("structuregen.formatVersion");
        } else if (tag.contains("formatVersion")
                && tag.getInt("formatVersion") != StructureGenConstants.BLUEPRINT_FORMAT_VERSION) {
            unsupported.add("structuregen.formatVersion=" + tag.getInt("formatVersion"));
        }

        Map<String, String> metadata = new TreeMap<>();
        if (tag.contains("metadata")) {
            if (!tag.contains("metadata", Tag.TAG_COMPOUND)) {
                unsupported.add("structuregen.metadata");
            } else {
                CompoundTag metadataTag = tag.getCompound("metadata");
                for (String key : metadataTag.getAllKeys()) {
                    if (metadataTag.get(key) instanceof StringTag) {
                        metadata.put(key, metadataTag.getString(key));
                    } else {
                        unsupported.add("structuregen.metadata." + key);
                    }
                }
            }
        }

        List<String> markers = readOptionalStringList(tag, "markers", "structuregen.markers", unsupported);
        Map<StructurePosition, List<String>> blockMarkers = readBlockMarkers(tag, unsupported);
        return new StructureGenData(name, metadata, markers, blockMarkers);
    }

    private Map<StructurePosition, List<String>> readBlockMarkers(
            CompoundTag structureGen,
            Set<String> unsupported
    ) throws StructureNbtFormatException {
        Map<StructurePosition, List<String>> markers = new LinkedHashMap<>();
        if (!structureGen.contains("blockMarkers")) {
            return markers;
        }
        if (!structureGen.contains("blockMarkers", Tag.TAG_LIST)) {
            unsupported.add("structuregen.blockMarkers");
            return markers;
        }
        ListTag entries = (ListTag) structureGen.get("blockMarkers");
        if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            unsupported.add("structuregen.blockMarkers");
            return markers;
        }
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            String location = "structuregen.blockMarkers[" + index + "]";
            try {
                StructurePosition position = readIntegerPosition(entry, "pos", location + ".pos");
                List<String> values = readOptionalStringList(entry, "markers", location + ".markers", unsupported);
                if (markers.putIfAbsent(position, values) != null) {
                    unsupported.add(location + ".pos(duplicate)");
                }
                recordUnknownFields(entry, Set.of("pos", "markers"), location, unsupported);
            } catch (StructureNbtFormatException exception) {
                unsupported.add(location);
            }
        }
        return markers;
    }

    private String rawRootSnbt(CompoundTag root, Set<StructurePosition> unmatchedBlockMarkers) {
        CompoundTag rawRoot = new CompoundTag();
        for (String key : root.getAllKeys()) {
            if (!ROOT_FIELDS.contains(key)) {
                rawRoot.put(key, Objects.requireNonNull(root.get(key)).copy());
            }
        }
        Tag structureGenTag = root.get("structuregen");
        if (structureGenTag instanceof CompoundTag structureGen) {
            CompoundTag unknownStructureGen = rawStructureGenSnbt(structureGen, unmatchedBlockMarkers);
            if (!unknownStructureGen.isEmpty()) {
                rawRoot.put("structuregen", unknownStructureGen);
            }
        } else if (structureGenTag != null) {
            // A malformed extension cannot be modeled, so retain it verbatim for an explicit writer failure.
            rawRoot.put("structuregen", structureGenTag.copy());
        }
        return rawRoot.isEmpty() ? null : rawRoot.toString();
    }

    private CompoundTag rawStructureGenSnbt(
            CompoundTag structureGen,
            Set<StructurePosition> unmatchedBlockMarkers
    ) {
        CompoundTag unknown = new CompoundTag();
        for (String key : structureGen.getAllKeys()) {
            if (!STRUCTUREGEN_FIELDS.contains(key)) {
                unknown.put(key, Objects.requireNonNull(structureGen.get(key)).copy());
            }
        }

        copyMalformedKnownField(structureGen, unknown, "formatVersion", Tag.TAG_ANY_NUMERIC);
        copyMalformedKnownField(structureGen, unknown, "name", Tag.TAG_STRING);
        copyMalformedKnownField(structureGen, unknown, "markers", Tag.TAG_LIST);
        Tag structureMarkers = structureGen.get("markers");
        if (structureMarkers instanceof ListTag markerValues
                && !markerValues.isEmpty()
                && markerValues.getElementType() != Tag.TAG_STRING) {
            unknown.put("markers", markerValues.copy());
        }

        Tag metadataTag = structureGen.get("metadata");
        if (metadataTag instanceof CompoundTag metadata) {
            CompoundTag unknownMetadata = new CompoundTag();
            for (String key : metadata.getAllKeys()) {
                Tag value = metadata.get(key);
                if (!(value instanceof StringTag)) {
                    unknownMetadata.put(key, Objects.requireNonNull(value).copy());
                }
            }
            if (!unknownMetadata.isEmpty()) {
                unknown.put("metadata", unknownMetadata);
            }
        } else if (metadataTag != null) {
            unknown.put("metadata", metadataTag.copy());
        }

        Tag markerTag = structureGen.get("blockMarkers");
        if (markerTag instanceof ListTag markerList &&
                (markerList.isEmpty() || markerList.getElementType() == Tag.TAG_COMPOUND)) {
            ListTag rawMarkers = new ListTag();
            for (int index = 0; index < markerList.size(); index++) {
                CompoundTag marker = markerList.getCompound(index);
                CompoundTag rawMarker = new CompoundTag();
                for (String key : marker.getAllKeys()) {
                    if (!Set.of("pos", "markers").contains(key)) {
                        rawMarker.put(key, Objects.requireNonNull(marker.get(key)).copy());
                    }
                }
                StructurePosition position = tryReadMarkerPosition(marker);
                if (position == null || unmatchedBlockMarkers.contains(position)) {
                    rawMarker = marker.copy();
                } else if (!rawMarker.isEmpty()) {
                    rawMarker.put("pos", Objects.requireNonNull(marker.get("pos")).copy());
                }
                if (!rawMarker.isEmpty()) {
                    rawMarkers.add(rawMarker);
                }
            }
            if (!rawMarkers.isEmpty()) {
                unknown.put("blockMarkers", rawMarkers);
            }
        } else if (markerTag != null) {
            unknown.put("blockMarkers", markerTag.copy());
        }
        return unknown;
    }

    private void copyMalformedKnownField(CompoundTag source, CompoundTag target, String key, int expectedType) {
        if (source.contains(key) && !source.contains(key, expectedType)) {
            target.put(key, Objects.requireNonNull(source.get(key)).copy());
        }
    }

    private StructurePosition tryReadMarkerPosition(CompoundTag marker) {
        Tag raw = marker.get("pos");
        if (!(raw instanceof ListTag values)
                || values.size() != 3
                || values.getElementType() != Tag.TAG_INT) {
            return null;
        }
        return new StructurePosition(values.getInt(0), values.getInt(1), values.getInt(2));
    }

    private ListTag requireList(
            CompoundTag owner,
            String key,
            int elementType,
            int expectedSize,
            String location
    ) throws StructureNbtFormatException {
        Tag raw = owner.get(key);
        if (!(raw instanceof ListTag list)) {
            throw format(location, "must be a list tag");
        }
        requireElementType(list, elementType, location);
        if (expectedSize >= 0 && list.size() != expectedSize) {
            throw format(location, "must contain exactly " + expectedSize + " entries; got " + list.size());
        }
        return list;
    }

    private void requireElementType(ListTag list, int elementType, String location) throws StructureNbtFormatException {
        if (!list.isEmpty() && list.getElementType() != elementType) {
            throw format(location, "has element type " + list.getElementType() + "; expected " + elementType);
        }
    }

    private CompoundTag requireCompound(Tag tag, String location) throws StructureNbtFormatException {
        if (!(tag instanceof CompoundTag compound)) {
            throw format(location, "must be a compound tag");
        }
        return compound;
    }

    private void requireString(CompoundTag owner, String key, String location) throws StructureNbtFormatException {
        if (!owner.contains(key, Tag.TAG_STRING)) {
            throw format(location, "must be a string tag");
        }
    }

    private StructurePosition readIntegerPosition(
            CompoundTag owner,
            String key,
            String location
    ) throws StructureNbtFormatException {
        ListTag values = requireList(owner, key, Tag.TAG_INT, 3, location);
        return new StructurePosition(values.getInt(0), values.getInt(1), values.getInt(2));
    }

    private String readOptionalString(
            CompoundTag owner,
            String key,
            String location,
            Set<String> unsupported
    ) {
        if (!owner.contains(key)) {
            return null;
        }
        if (!owner.contains(key, Tag.TAG_STRING)) {
            unsupported.add(location);
            return null;
        }
        return owner.getString(key);
    }

    private List<String> readOptionalStringList(
            CompoundTag owner,
            String key,
            String location,
            Set<String> unsupported
    ) {
        if (!owner.contains(key)) {
            return List.of();
        }
        if (!owner.contains(key, Tag.TAG_LIST)) {
            unsupported.add(location);
            return List.of();
        }
        ListTag list = (ListTag) owner.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING) {
            unsupported.add(location);
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            values.add(list.getString(index));
        }
        return List.copyOf(values);
    }

    private void recordUnknownFields(
            CompoundTag tag,
            Set<String> supported,
            String location,
            Set<String> unsupported
    ) {
        tag.getAllKeys().stream()
                .filter(key -> !supported.contains(key))
                .map(key -> location + "." + key)
                .forEach(unsupported::add);
    }

    private String unknownSnbt(CompoundTag source, Set<String> supported) {
        CompoundTag unknown = new CompoundTag();
        for (String key : source.getAllKeys()) {
            if (!supported.contains(key)) {
                unknown.put(key, Objects.requireNonNull(source.get(key)).copy());
            }
        }
        return unknown.isEmpty() ? null : unknown.toString();
    }

    private boolean containsEntity(StructureSize size, List<Double> position) {
        return position.get(0) >= 0.0D && position.get(0) < size.x()
                && position.get(1) >= 0.0D && position.get(1) < size.y()
                && position.get(2) >= 0.0D && position.get(2) < size.z();
    }

    private StructureNbtFormatException format(String location, String message) {
        return new StructureNbtFormatException(location + ": " + message);
    }

    private String structureName(Path path) {
        String filename = path.getFileName() == null ? "structure" : path.getFileName().toString();
        return filename.toLowerCase().endsWith(".nbt")
                ? filename.substring(0, filename.length() - 4)
                : filename;
    }

    private record StructureGenData(
            String name,
            Map<String, String> metadata,
            List<String> markers,
            Map<StructurePosition, List<String>> blockMarkers
    ) {
        private static StructureGenData empty() {
            return new StructureGenData(null, Map.of(), List.of(), Map.of());
        }
    }
}
