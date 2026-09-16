package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.NbtValue;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Imports raw or gzip Java structure templates independently of Minecraft. Unlike the existing
 * registry/compiler reader, this adapter tolerates invalid entries so the rest can be previewed.
 * StructureGen metadata is retained verbatim rather than interpreted as another mission authority.
 */
public final class NbtStructureReader implements StructureReader {
    @Override
    public StructureData read(Path path) throws IOException {
        if (Files.size(path) > 64L * 1024 * 1024) throw new IOException("Structure exceeds the 64 MiB file limit.");
        var root = new LinkedHashMap<String, NbtValue>();
        List<Block> blocks = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        try (var stream = new BufferedInputStream(Files.newInputStream(path))) {
            stream.mark(2);
            int a = stream.read(), b = stream.read();
            stream.reset();
            try (var input = new NbtInput(a == 0x1f && b == 0x8b ? new GZIPInputStream(stream) : stream)) {
                if (input.type() != 10) throw new IOException("Structure root must be an NBT compound.");
                input.name();
                int type;
                boolean sawBlocks = false;
                while ((type = input.type()) != 0) {
                    String key = input.name();
                    if (key.equals("blocks")) {
                        if (sawBlocks) throw new IOException("Duplicate root blocks list.");
                        sawBlocks = true;
                        if (type != 9) throw new IOException("Structure blocks must be an NBT list.");
                        int element = input.type(), count = input.length();
                        if (element != 10 && count > 0) throw new IOException("Block entries must be compounds.");
                        for (int i = 0; i < count; i++) {
                            if (Thread.currentThread().isInterrupted()) throw new IOException("Loading cancelled.");
                            readBlock(input.value(element, 1), i, blocks, diagnostics);
                        }
                    } else {
                        NbtValue value = input.value(type, 1);
                        if (root.putIfAbsent(key, value) != null) warn(diagnostics, "Duplicate root metadata: " + key + " (first value retained).");
                    }
                }
                if (!sawBlocks) throw new IOException("Structure has no blocks list.");
            }
        }
        Position size = position(root.remove("size"));
        if (size == null || size.x() < 0 || size.y() < 0 || size.z() < 0)
            throw new IOException("Structure size must contain three nonnegative integers.");
        List<List<BlockState>> palettes = new ArrayList<>();
        NbtValue palette = root.remove("palette"), multiple = root.remove("palettes");
        if (palette != null) palettes.add(readPalette(palette, diagnostics));
        if (multiple != null) {
            if (palette != null) warn(diagnostics, "Both palette and palettes are present; palette is used for preview.");
            for (NbtValue entry : multiple.list()) palettes.add(readPalette(entry, diagnostics));
        }
        if (palettes.isEmpty()) {
            palettes.add(List.of());
            warn(diagnostics, "Missing palette; blocks use missing-state placeholders.");
        }
        if (palettes.size() > 1) warn(diagnostics, "Multiple palettes retained; preview uses the first palette.");
        for (Block block : blocks) {
            Position p = block.position();
            if (p.x() < 0 || p.y() < 0 || p.z() < 0 || p.x() >= size.x() || p.y() >= size.y() || p.z() >= size.z())
                warn(diagnostics, "Block outside declared dimensions at " + p);
            if (block.paletteIndex() < 0 || block.paletteIndex() >= palettes.getFirst().size())
                warn(diagnostics, "Invalid palette reference " + block.paletteIndex() + " at " + p);
        }
        NbtValue entities = root.remove("entities");
        String filename = path.getFileName().toString();
        return new StructureData(filename.replaceFirst("(?i)\\.nbt$", ""), path.toAbsolutePath().normalize(), size,
                blocks, palettes, entities == null ? List.of() : entities.list(), root, diagnostics);
    }

    private static void readBlock(NbtValue value, int index, List<Block> blocks, List<String> diagnostics) {
        var fields = new LinkedHashMap<>(value.compound());
        Position p = position(fields.remove("pos"));
        NbtValue state = fields.remove("state");
        if (p == null) { warn(diagnostics, "Skipped block " + index + ": malformed position."); return; }
        int paletteIndex = state != null && state.type() == 3 ? ((Number) state.value()).intValue() : -1;
        NbtValue nbt = fields.remove("nbt");
        if (nbt != null && nbt.type() != 10) warn(diagnostics, "Malformed block-entity NBT at " + p + "; raw tag retained.");
        blocks.add(new Block(p, paletteIndex, nbt, fields));
    }

    private static List<BlockState> readPalette(NbtValue value, List<String> diagnostics) {
        List<BlockState> states = new ArrayList<>();
        for (NbtValue item : value.list()) {
            NbtValue name = item.compound().get("Name");
            if (name == null || name.type() != 8) warn(diagnostics, "Missing block ID in palette entry " + states.size());
            String id = name != null && name.type() == 8 ? name.value().toString() : BlockState.MISSING.id();
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) warn(diagnostics, "Malformed block ID: " + id);
            var properties = new LinkedHashMap<String, String>();
            NbtValue props = item.compound().get("Properties");
            if (props != null) {
                if (props.type() != 10) warn(diagnostics, "Malformed state properties for " + id);
                props.compound().forEach((key, property) -> {
                    properties.put(key, property.value().toString());
                    if (property.type() != 8) warn(diagnostics, "Non-string property " + id + "[" + key + "]");
                });
            }
            states.add(new BlockState(id, properties));
        }
        return states;
    }

    private static Position position(NbtValue value) {
        if (value == null || value.list().size() != 3 || value.list().stream().anyMatch(v -> v.type() != 3)) return null;
        return new Position((int) value.list().get(0).value(), (int) value.list().get(1).value(), (int) value.list().get(2).value());
    }

    private static void warn(List<String> diagnostics, String message) {
        if (diagnostics.size() < 200) diagnostics.add(message);
        else if (diagnostics.size() == 200) diagnostics.add("Further import warnings omitted.");
    }
}

