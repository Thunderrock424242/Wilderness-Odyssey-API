package com.thunder.wildernessodysseyapi.structuregen.nbt;

import com.thunder.wildernessodysseyapi.structuregen.StructureGenConstants;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compiles a validated StructureGen model into standard Minecraft structure-template NBT.
 *
 * <p>Authored models receive a canonical palette and coordinate ordering. Imported models retain
 * their original palette ordering and raw entry compounds so alternate palettes and unknown entry
 * fields survive a supported semantic round trip.</p>
 */
public final class MinecraftStructureNbtWriter {

    /** Builds the uncompressed root compound without touching the filesystem. */
    public CompoundTag compile(StructureModel model) {
        Objects.requireNonNull(model, "model");
        validateModelShape(model);

        CompoundTag root = StructureNbtSupport.parseCompound(model.rawRootSnbt(), "raw root");
        removeStandardFields(root);
        root.put("size", StructureNbtSupport.integerList(
                model.size().x(), model.size().y(), model.size().z()
        ));

        PaletteLayout paletteLayout = model.sourcePalettes().isEmpty()
                ? authoredPalette(model.blocks())
                : importedPalettes(model.sourcePalettes());
        writePalettes(root, paletteLayout.palettes());
        root.put("blocks", writeBlocks(model, paletteLayout));
        root.put("entities", writeEntities(model));
        root.putInt("DataVersion", model.dataVersion() >= 0
                ? model.dataVersion()
                : StructureGenConstants.MINECRAFT_DATA_VERSION);
        root.put("structuregen", writeStructureGenMetadata(root, model));
        return root;
    }

    /**
     * Writes compressed NBT to the supplied serialization path.
     *
     * <p>This method intentionally performs no destination selection, collision check, or final
     * replacement. The safe-output layer supplies a temporary path, re-reads it, and promotes it
     * only after verification.</p>
     */
    public void writeCompressed(StructureModel model, Path serializationPath) throws IOException {
        Objects.requireNonNull(serializationPath, "serializationPath");
        NbtIo.writeCompressed(compile(model), serializationPath);
    }

    private void validateModelShape(StructureModel model) {
        Objects.requireNonNull(model.name(), "model.name");
        Objects.requireNonNull(model.size(), "model.size");
        if (model.size().x() <= 0 || model.size().y() <= 0 || model.size().z() <= 0) {
            throw new IllegalArgumentException("Structure dimensions must be greater than zero: "
                    + model.size().display());
        }
        if (model.blocks().isEmpty()) {
            throw new IllegalArgumentException("Structure must contain at least one explicit block record");
        }
        Set<StructurePosition> positions = new HashSet<>();
        for (StructureBlock block : model.blocks()) {
            if (!model.size().contains(block.position())) {
                throw new IllegalArgumentException("Block at " + block.position().display()
                        + " lies outside structure size " + model.size().display());
            }
            if (!positions.add(block.position())) {
                throw new IllegalArgumentException("Duplicate block position " + block.position().display());
            }
        }
        for (StructureEntity entity : model.entities()) {
            if (entity.position().size() != 3 || entity.position().stream().anyMatch(value -> !Double.isFinite(value))) {
                throw new IllegalArgumentException("Entity positions must contain three finite doubles: " + entity.position());
            }
            if (entity.position().get(0) < 0.0D || entity.position().get(0) >= model.size().x()
                    || entity.position().get(1) < 0.0D || entity.position().get(1) >= model.size().y()
                    || entity.position().get(2) < 0.0D || entity.position().get(2) >= model.size().z()) {
                throw new IllegalArgumentException("Entity position " + entity.position()
                        + " lies outside structure size " + model.size().display());
            }
            if (!model.size().contains(entity.blockPosition())) {
                throw new IllegalArgumentException("Entity block position " + entity.blockPosition().display()
                        + " lies outside structure size " + model.size().display());
            }
        }
    }

    private PaletteLayout authoredPalette(List<StructureBlock> blocks) {
        TreeMap<String, StructureBlockState> statesByKey = new TreeMap<>();
        for (StructureBlock block : blocks) {
            statesByKey.putIfAbsent(block.state().canonicalKey(), block.state());
        }
        List<StructureBlockState> palette = List.copyOf(statesByKey.values());
        Map<String, Integer> indexByKey = new HashMap<>();
        for (int index = 0; index < palette.size(); index++) {
            indexByKey.put(palette.get(index).canonicalKey(), index);
        }
        return new PaletteLayout(List.of(palette), indexByKey, false);
    }

    private PaletteLayout importedPalettes(List<List<StructureBlockState>> sourcePalettes) {
        if (sourcePalettes.isEmpty()) {
            throw new IllegalArgumentException("Imported palette list may not be empty");
        }
        int paletteSize = sourcePalettes.get(0).size();
        if (paletteSize == 0) {
            throw new IllegalArgumentException("Imported palettes may not be empty");
        }
        List<List<StructureBlockState>> palettes = new ArrayList<>(sourcePalettes.size());
        for (int paletteIndex = 0; paletteIndex < sourcePalettes.size(); paletteIndex++) {
            List<StructureBlockState> palette = List.copyOf(sourcePalettes.get(paletteIndex));
            if (palette.size() != paletteSize) {
                throw new IllegalArgumentException("Imported palette " + paletteIndex + " has size " + palette.size()
                        + "; expected " + paletteSize);
            }
            palettes.add(palette);
        }
        Map<String, Integer> primaryIndexByKey = new LinkedHashMap<>();
        for (int index = 0; index < palettes.get(0).size(); index++) {
            primaryIndexByKey.putIfAbsent(palettes.get(0).get(index).canonicalKey(), index);
        }
        return new PaletteLayout(List.copyOf(palettes), primaryIndexByKey, true);
    }

    private void writePalettes(CompoundTag root, List<List<StructureBlockState>> palettes) {
        root.remove("palette");
        root.remove("palettes");
        if (palettes.size() == 1) {
            root.put("palette", writePalette(palettes.get(0)));
            return;
        }

        ListTag outer = new ListTag();
        palettes.forEach(palette -> outer.add(writePalette(palette)));
        root.put("palettes", outer);
    }

    private ListTag writePalette(List<StructureBlockState> palette) {
        ListTag output = new ListTag();
        for (StructureBlockState state : palette) {
            CompoundTag stateTag = StructureNbtSupport.parseCompound(
                    state.rawPaletteEntrySnbt(), "raw palette entry"
            );
            stateTag.putString("Name", state.blockId());
            if (state.properties().isEmpty()) {
                stateTag.remove("Properties");
            } else {
                CompoundTag properties = new CompoundTag();
                state.properties().forEach(properties::putString);
                stateTag.put("Properties", properties);
            }
            output.add(stateTag);
        }
        return output;
    }

    private ListTag writeBlocks(StructureModel model, PaletteLayout paletteLayout) {
        List<StructureBlock> blocks = paletteLayout.imported()
                ? model.blocks()
                : model.blocks().stream().sorted(Comparator.comparing(StructureBlock::position)).toList();
        ListTag output = new ListTag();
        for (StructureBlock block : blocks) {
            CompoundTag entry = StructureNbtSupport.parseCompound(block.rawEntrySnbt(), "raw block entry");
            entry.put("pos", StructureNbtSupport.integerList(
                    block.position().x(), block.position().y(), block.position().z()
            ));
            entry.putInt("state", paletteIndex(block, paletteLayout));
            if (block.blockEntitySnbt() == null) {
                entry.remove("nbt");
            } else {
                entry.put("nbt", StructureNbtSupport.parseCompound(block.blockEntitySnbt(), "block entity"));
            }
            output.add(entry);
        }
        return output;
    }

    private int paletteIndex(StructureBlock block, PaletteLayout layout) {
        List<StructureBlockState> primaryPalette = layout.palettes().get(0);
        int originalIndex = block.sourcePaletteIndex();
        if (layout.imported()
                && originalIndex >= 0
                && originalIndex < primaryPalette.size()
                && primaryPalette.get(originalIndex).canonicalKey().equals(block.state().canonicalKey())) {
            return originalIndex;
        }
        Integer resolved = layout.primaryIndexByKey().get(block.state().canonicalKey());
        if (resolved == null) {
            throw new IllegalArgumentException("Block state " + block.state().canonicalKey()
                    + " is not represented by the imported primary palette");
        }
        return resolved;
    }

    private ListTag writeEntities(StructureModel model) {
        List<StructureEntity> entities = model.sourcePalettes().isEmpty()
                ? model.entities().stream().sorted(entityComparator()).toList()
                : model.entities();
        ListTag output = new ListTag();
        for (StructureEntity entity : entities) {
            CompoundTag entry = StructureNbtSupport.parseCompound(entity.rawEntrySnbt(), "raw entity entry");
            entry.put("pos", StructureNbtSupport.doubleList(
                    entity.position().get(0), entity.position().get(1), entity.position().get(2)
            ));
            entry.put("blockPos", StructureNbtSupport.integerList(
                    entity.blockPosition().x(), entity.blockPosition().y(), entity.blockPosition().z()
            ));
            entry.put("nbt", StructureNbtSupport.parseCompound(entity.entityNbtSnbt(), "entity payload"));
            output.add(entry);
        }
        return output;
    }

    private Comparator<StructureEntity> entityComparator() {
        return Comparator.comparing(StructureEntity::blockPosition)
                .thenComparing(entity -> entity.position().get(1))
                .thenComparing(entity -> entity.position().get(2))
                .thenComparing(entity -> entity.position().get(0))
                .thenComparing(StructureEntity::entityNbtSnbt);
    }

    private CompoundTag writeStructureGenMetadata(CompoundTag root, StructureModel model) {
        Tag rawStructureGen = root.get("structuregen");
        if (rawStructureGen != null && !(rawStructureGen instanceof CompoundTag)) {
            throw new IllegalArgumentException("Cannot preserve malformed raw root.structuregen tag: expected a compound");
        }
        CompoundTag tag = rawStructureGen instanceof CompoundTag existing ? existing.copy() : new CompoundTag();
        validateRawStructureGenFields(tag);
        tag.putInt("formatVersion", StructureGenConstants.BLUEPRINT_FORMAT_VERSION);
        tag.putString("name", model.name());

        // Retain non-string imported metadata fields while making the canonical string map authoritative.
        CompoundTag metadata = tag.get("metadata") instanceof CompoundTag existingMetadata
                ? existingMetadata.copy()
                : new CompoundTag();
        for (String key : Set.copyOf(metadata.getAllKeys())) {
            if (metadata.get(key) instanceof StringTag && !model.metadata().containsKey(key)) {
                metadata.remove(key);
            }
        }
        model.metadata().forEach(metadata::putString);
        tag.put("metadata", metadata);
        tag.put("markers", StructureNbtSupport.stringList(model.markers()));

        RawBlockMarkers rawBlockMarkers = readRawBlockMarkers(tag);
        Set<StructurePosition> modelPositions = new HashSet<>();
        model.blocks().forEach(block -> modelPositions.add(block.position()));
        List<StructureBlock> markedBlocks = model.blocks().stream()
                .filter(block -> !block.markers().isEmpty())
                .sorted(Comparator.comparing(StructureBlock::position))
                .toList();
        ListTag blockMarkers = new ListTag();
        for (StructureBlock block : markedBlocks) {
            CompoundTag marker = rawBlockMarkers.byPosition().remove(block.position());
            if (marker == null) {
                marker = new CompoundTag();
            }
            marker.put("pos", StructureNbtSupport.integerList(
                    block.position().x(), block.position().y(), block.position().z()
            ));
            marker.put("markers", StructureNbtSupport.stringList(block.markers()));
            blockMarkers.add(marker);
        }
        rawBlockMarkers.byPosition().forEach((position, marker) -> {
            if (!modelPositions.contains(position)) {
                blockMarkers.add(marker);
            }
        });
        rawBlockMarkers.unpositioned().forEach(blockMarkers::add);
        tag.put("blockMarkers", blockMarkers);
        return tag;
    }

    private void validateRawStructureGenFields(CompoundTag tag) {
        requireRawType(tag, "formatVersion", Tag.TAG_ANY_NUMERIC);
        requireRawType(tag, "name", Tag.TAG_STRING);
        requireRawType(tag, "metadata", Tag.TAG_COMPOUND);
        requireRawType(tag, "markers", Tag.TAG_LIST);
        requireRawType(tag, "blockMarkers", Tag.TAG_LIST);
        if (tag.get("markers") instanceof ListTag markers
                && !markers.isEmpty()
                && markers.getElementType() != Tag.TAG_STRING) {
            throw new IllegalArgumentException("Cannot preserve malformed raw structuregen.markers list");
        }
        if (tag.get("blockMarkers") instanceof ListTag markers
                && !markers.isEmpty()
                && markers.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Cannot preserve malformed raw structuregen.blockMarkers list");
        }
    }

    private void requireRawType(CompoundTag tag, String key, int expectedType) {
        if (tag.contains(key) && !tag.contains(key, expectedType)) {
            throw new IllegalArgumentException("Cannot preserve malformed raw structuregen." + key + " tag");
        }
    }

    private RawBlockMarkers readRawBlockMarkers(CompoundTag structureGen) {
        Map<StructurePosition, CompoundTag> byPosition = new LinkedHashMap<>();
        List<CompoundTag> unpositioned = new ArrayList<>();
        if (!(structureGen.get("blockMarkers") instanceof ListTag markers)) {
            return new RawBlockMarkers(byPosition, unpositioned);
        }
        for (int index = 0; index < markers.size(); index++) {
            CompoundTag marker = markers.getCompound(index).copy();
            StructurePosition position = markerPosition(marker);
            if (position == null || byPosition.putIfAbsent(position, marker) != null) {
                unpositioned.add(marker);
            }
        }
        return new RawBlockMarkers(byPosition, unpositioned);
    }

    private StructurePosition markerPosition(CompoundTag marker) {
        if (!(marker.get("pos") instanceof ListTag position)
                || position.size() != 3
                || position.getElementType() != Tag.TAG_INT) {
            return null;
        }
        return new StructurePosition(position.getInt(0), position.getInt(1), position.getInt(2));
    }

    private void removeStandardFields(CompoundTag root) {
        root.remove("size");
        root.remove("blocks");
        root.remove("palette");
        root.remove("palettes");
        root.remove("entities");
        root.remove("DataVersion");
    }

    private record PaletteLayout(
            List<List<StructureBlockState>> palettes,
            Map<String, Integer> primaryIndexByKey,
            boolean imported
    ) {
    }

    private record RawBlockMarkers(
            Map<StructurePosition, CompoundTag> byPosition,
            List<CompoundTag> unpositioned
    ) {
    }
}
