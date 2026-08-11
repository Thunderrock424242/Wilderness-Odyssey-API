package com.thunder.wildernessodysseyapi.structuregen.model;

import com.thunder.wildernessodysseyapi.structuregen.content.StructureContentManifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical structure representation shared by blueprint, NBT, inspection, and comparison code.
 *
 * <p>SNBT strings retain exact NBT numeric and array types without coupling the model to compressed
 * binary serialization. Imported palette and raw-entry data allow the NBT side to preserve fields
 * that Blueprint v1 does not otherwise understand.</p>
 */
public final class StructureModel {

    private final String name;
    private final StructureSize size;
    private final List<StructureBlock> blocks;
    private final List<StructureEntity> entities;
    private final int dataVersion;
    private final Map<String, String> metadata;
    private final List<String> markers;
    private final List<List<StructureBlockState>> sourcePalettes;
    private final String rawRootSnbt;
    private final List<String> unsupportedFields;
    private final StructureContentManifest contentManifest;

    /** Creates a fully described canonical structure model. */
    public StructureModel(
            String name,
            StructureSize size,
            List<StructureBlock> blocks,
            List<StructureEntity> entities,
            int dataVersion,
            Map<String, String> metadata,
            List<String> markers,
            List<List<StructureBlockState>> sourcePalettes,
            String rawRootSnbt,
            List<String> unsupportedFields
    ) {
        this(
                name, size, blocks, entities, dataVersion, metadata, markers, sourcePalettes,
                rawRootSnbt, unsupportedFields, StructureContentManifest.defaults()
        );
    }

    /** Creates a canonical structure model with mod-aware content provenance. */
    public StructureModel(
            String name,
            StructureSize size,
            List<StructureBlock> blocks,
            List<StructureEntity> entities,
            int dataVersion,
            Map<String, String> metadata,
            List<String> markers,
            List<List<StructureBlockState>> sourcePalettes,
            String rawRootSnbt,
            List<String> unsupportedFields,
            StructureContentManifest contentManifest
    ) {
        this.name = name;
        this.size = size;
        this.blocks = List.copyOf(blocks);
        this.entities = List.copyOf(entities);
        this.dataVersion = dataVersion;
        this.metadata = Collections.unmodifiableMap(new TreeMap<>(metadata));
        this.markers = List.copyOf(markers);
        List<List<StructureBlockState>> palettes = new ArrayList<>(sourcePalettes.size());
        sourcePalettes.forEach(palette -> palettes.add(List.copyOf(palette)));
        this.sourcePalettes = List.copyOf(palettes);
        this.rawRootSnbt = rawRootSnbt;
        this.unsupportedFields = List.copyOf(unsupportedFields);
        this.contentManifest = contentManifest == null
                ? StructureContentManifest.defaults()
                : contentManifest;
    }

    public String name() {
        return name;
    }

    public StructureSize size() {
        return size;
    }

    public List<StructureBlock> blocks() {
        return blocks;
    }

    public List<StructureEntity> entities() {
        return entities;
    }

    public int dataVersion() {
        return dataVersion;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public List<String> markers() {
        return markers;
    }

    public List<List<StructureBlockState>> sourcePalettes() {
        return sourcePalettes;
    }

    public String rawRootSnbt() {
        return rawRootSnbt;
    }

    public List<String> unsupportedFields() {
        return unsupportedFields;
    }

    /** Returns the policy and semantic-material choices used to author this concrete model. */
    public StructureContentManifest contentManifest() {
        return contentManifest;
    }
}
