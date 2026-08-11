package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.thunder.wildernessodysseyapi.structuregen.StructureGenConstants;
import com.thunder.wildernessodysseyapi.structuregen.content.ContentManifestStatus;
import com.thunder.wildernessodysseyapi.structuregen.content.ResolvedMaterial;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Exports a canonical structure model as readable Blueprint Format v1 JSON.
 *
 * <p>The file-writing path streams blocks directly through Gson's {@link JsonWriter}; this avoids
 * constructing a second multi-million-element JSON tree while exporting large reference fixtures.
 * Imported palette indices and unknown-entry SNBT are emitted as extension fields so inspection
 * exports remain loss-aware even when Blueprint v1 cannot author those details directly.</p>
 */
public final class BlueprintExporter {

    /** Returns a JSON tree for tests and callers working with reasonably sized structures. */
    public JsonObject toJson(StructureModel model) {
        return JsonParser.parseString(export(model)).getAsJsonObject();
    }

    /** Returns pretty Blueprint v1 JSON in memory; prefer {@link #write} for large structures. */
    public String export(StructureModel model) {
        Objects.requireNonNull(model, "model");
        StringWriter output = new StringWriter();
        try (JsonWriter json = prettyWriter(output)) {
            writeModel(json, model);
        } catch (IOException exception) {
            // StringWriter does not perform I/O; preserve the checked JsonWriter contract as an assertion failure.
            throw new IllegalStateException("Unable to render blueprint JSON", exception);
        }
        return output + System.lineSeparator();
    }

    /**
     * Streams pretty Blueprint v1 JSON to the supplied serialization path.
     *
     * <p>The caller owns collision checks and atomic promotion; this method only serializes to the
     * path it receives, which is normally a temporary file inside the safe output directory.</p>
     */
    public void write(StructureModel model, Path serializationPath) throws IOException {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(serializationPath, "serializationPath");
        try (Writer output = Files.newBufferedWriter(serializationPath, StandardCharsets.UTF_8);
             JsonWriter json = prettyWriter(output)) {
            writeModel(json, model);
        }
    }

    private JsonWriter prettyWriter(Writer output) {
        JsonWriter json = new JsonWriter(output);
        json.setIndent("  ");
        json.setSerializeNulls(false);
        return json;
    }

    private void writeModel(JsonWriter json, StructureModel model) throws IOException {
        json.beginObject();
        json.name("formatVersion").value(StructureGenConstants.BLUEPRINT_FORMAT_VERSION);
        json.name("name").value(model.name());
        if (model.dataVersion() >= 0) {
            json.name("dataVersion").value(model.dataVersion());
        }
        json.name("size");
        writePosition(json, model.size().x(), model.size().y(), model.size().z());

        if (!model.metadata().isEmpty()) {
            json.name("metadata");
            writeStringMap(json, model.metadata());
        }
        if (!model.markers().isEmpty()) {
            json.name("markers");
            writeStringArray(json, model.markers());
        }
        if (model.contentManifest().provenanceStatus() == ContentManifestStatus.VERIFIED) {
            json.name("contentPolicy").beginObject();
            json.name("allowInstalledModBlocks").value(model.contentManifest().allowInstalledModBlocks());
            if (!model.contentManifest().preferredDecorativeMods().isEmpty()) {
                json.name("preferredDecorativeMods");
                writeStringArray(json, model.contentManifest().preferredDecorativeMods());
            }
            if (!model.contentManifest().requiredMods().isEmpty()) {
                json.name("requiredMods");
                writeStringArray(json, model.contentManifest().requiredMods());
            }
            if (!model.contentManifest().enabledFunctionalSystems().isEmpty()) {
                json.name("enabledFunctionalSystems");
                writeStringArray(json, model.contentManifest().enabledFunctionalSystems());
            }
            json.endObject();
        }

        json.name("blocks").beginArray();
        for (StructureBlock block : model.blocks()) {
            writeBlock(json, model, block);
        }
        json.endArray();

        if (!model.entities().isEmpty()) {
            json.name("entities").beginArray();
            for (StructureEntity entity : model.entities()) {
                writeEntity(json, entity);
            }
            json.endArray();
        }

        // Alternate imported palettes are extensions to authored v1, but retaining them makes exports inspectable.
        if (!model.sourcePalettes().isEmpty()) {
            json.name("sourcePalettes").beginArray();
            for (List<StructureBlockState> palette : model.sourcePalettes()) {
                json.beginArray();
                for (StructureBlockState state : palette) {
                    writePaletteState(json, state);
                }
                json.endArray();
            }
            json.endArray();
        }

        if (model.rawRootSnbt() != null) {
            json.name("rawRootSnbt").value(model.rawRootSnbt());
        }
        if (!model.unsupportedFields().isEmpty()) {
            json.name("unsupportedFields");
            writeStringArray(json, new ArrayList<>(new TreeSet<>(model.unsupportedFields())));
        }
        json.endObject();
    }

    private void writeBlock(JsonWriter json, StructureModel model, StructureBlock block) throws IOException {
        json.beginObject();
        json.name("pos");
        writePosition(json, block.position());
        json.name("block").value(block.state().blockId());
        if (!block.state().properties().isEmpty()) {
            json.name("properties");
            writeStringMap(json, block.state().properties());
        }
        if (hasVerifiedDecorativeSelection(model, block)) {
            json.name("usageIntent").value("decorative");
        }
        if (block.blockEntitySnbt() != null) {
            json.name("blockEntitySnbt").value(block.blockEntitySnbt());
        }
        if (!block.markers().isEmpty()) {
            json.name("markers");
            writeStringArray(json, block.markers());
        }
        if (block.rawEntrySnbt() != null) {
            json.name("rawEntrySnbt").value(block.rawEntrySnbt());
        }
        if (block.sourcePaletteIndex() >= 0) {
            json.name("sourcePaletteIndex").value(block.sourcePaletteIndex());
        }
        json.endObject();
    }

    // A concrete imported block has no per-position author intent. Only an exact state selected
    // by a recorded decorative role in a structure with no enabled functional systems is safe to
    // classify. Otherwise the same state could be functional elsewhere, so re-import must ask the
    // author instead of inventing decorative intent.
    private boolean hasVerifiedDecorativeSelection(StructureModel model, StructureBlock block) {
        if (model.contentManifest().provenanceStatus() != ContentManifestStatus.VERIFIED
                || !model.contentManifest().enabledFunctionalSystems().isEmpty()) {
            return false;
        }
        List<ResolvedMaterial> matching = model.contentManifest().resolvedMaterials().stream()
                .filter(material -> material.selectedBlock().equals(block.state().blockId()))
                .filter(material -> material.properties().equals(block.state().properties()))
                .toList();
        return !matching.isEmpty()
                && matching.stream().allMatch(material -> "decorative".equals(material.intent()));
    }

    private void writeEntity(JsonWriter json, StructureEntity entity) throws IOException {
        json.beginObject();
        json.name("pos").beginArray();
        for (double value : entity.position()) {
            json.value(value);
        }
        json.endArray();
        json.name("blockPos");
        writePosition(json, entity.blockPosition());
        json.name("nbtSnbt").value(entity.entityNbtSnbt());
        if (entity.rawEntrySnbt() != null) {
            json.name("rawEntrySnbt").value(entity.rawEntrySnbt());
        }
        json.endObject();
    }

    private void writePaletteState(JsonWriter json, StructureBlockState state) throws IOException {
        json.beginObject();
        json.name("block").value(state.blockId());
        if (!state.properties().isEmpty()) {
            json.name("properties");
            writeStringMap(json, state.properties());
        }
        if (state.rawPaletteEntrySnbt() != null) {
            json.name("rawPaletteEntrySnbt").value(state.rawPaletteEntrySnbt());
        }
        json.endObject();
    }

    private void writeStringMap(JsonWriter json, Map<String, String> values) throws IOException {
        json.beginObject();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            json.name(entry.getKey()).value(entry.getValue());
        }
        json.endObject();
    }

    private void writeStringArray(JsonWriter json, List<String> values) throws IOException {
        json.beginArray();
        for (String value : values) {
            json.value(value);
        }
        json.endArray();
    }

    private void writePosition(JsonWriter json, StructurePosition position) throws IOException {
        writePosition(json, position.x(), position.y(), position.z());
    }

    private void writePosition(JsonWriter json, int x, int y, int z) throws IOException {
        json.beginArray();
        json.value(x);
        json.value(y);
        json.value(z);
        json.endArray();
    }

}
