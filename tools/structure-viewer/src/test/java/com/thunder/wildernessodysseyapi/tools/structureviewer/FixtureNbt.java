package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.NbtValue;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

final class FixtureNbt {
    static NbtValue tag(int type, Object value) { return new NbtValue(type, value); }
    static NbtValue compound(Map<String, NbtValue> value) { return tag(10, value); }
    static NbtValue ints(int... values) { return tag(9, Arrays.stream(values).mapToObj(v -> tag(3, v)).toList()); }
    static NbtValue block(int x, int y, int z, int state) {
        return compound(Map.of("pos", ints(x,y,z), "state", tag(3,state),
                "nbt", compound(Map.of("id",tag(8,"wildernessodysseyapi:test_block"),"power",tag(1,(byte)1)))));
    }
    static NbtValue structure() {
        var map = new LinkedHashMap<String, NbtValue>();
        // Blocks before palette exercises the streaming adapter's ordering independence.
        map.put("blocks", tag(9, List.of(block(0,0,0,0), block(1,0,0,1))));
        map.put("size", ints(2,2,2));
        map.put("palette", tag(9,List.of(compound(Map.of("Name", tag(8,"minecraft:stone"))),
                compound(Map.of("Name",tag(8,"wildernessodysseyapi:unknown_door"),"Properties",
                        compound(Map.of("facing",tag(8,"north"),"powered",tag(8,"false"))))))));
        map.put("entities", tag(9,List.of(compound(Map.of("nbt",compound(Map.of("id",tag(8,"minecraft:armor_stand"))))))));
        map.put("structuregen", compound(Map.of("markers", tag(9,List.of(tag(8,"mission:start"))))));
        map.put("DataVersion", tag(3,3955));
        return compound(map);
    }
    static Path write(Path path, NbtValue root, boolean compressed) throws IOException {
        try (OutputStream bytes = Files.newOutputStream(path);
             DataOutputStream out = new DataOutputStream(compressed ? new GZIPOutputStream(bytes) : bytes)) {
            out.writeByte(10); out.writeUTF(""); writeValue(out,root);
        }
        return path;
    }
    static void writeValue(DataOutputStream out, NbtValue tag) throws IOException {
        switch (tag.type()) {
            case 1 -> out.writeByte(((Number)tag.value()).byteValue());
            case 2 -> out.writeShort(((Number)tag.value()).shortValue());
            case 3 -> out.writeInt(((Number)tag.value()).intValue());
            case 4 -> out.writeLong(((Number)tag.value()).longValue());
            case 5 -> out.writeFloat(((Number)tag.value()).floatValue());
            case 6 -> out.writeDouble(((Number)tag.value()).doubleValue());
            case 8 -> out.writeUTF(tag.value().toString());
            case 7,9,11,12 -> {
                if (tag.type() == 9) out.writeByte(tag.list().isEmpty() ? 0 : tag.list().getFirst().type());
                out.writeInt(tag.list().size());
                for (var value: tag.list()) writeValue(out,value);
            }
            case 10 -> {
                for (var entry:tag.compound().entrySet()) {
                    out.writeByte(entry.getValue().type()); out.writeUTF(entry.getKey()); writeValue(out,entry.getValue());
                }
                out.writeByte(0);
            }
            default -> throw new IOException("Unsupported fixture tag");
        }
    }
}

