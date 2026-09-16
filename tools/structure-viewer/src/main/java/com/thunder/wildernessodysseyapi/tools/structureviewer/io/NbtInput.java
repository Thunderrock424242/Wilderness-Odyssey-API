package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.NbtValue;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Bounded Java-edition NBT decoder; the adapter streams root block entries to limit peak memory. */
final class NbtInput implements AutoCloseable {
    private final DataInputStream input;
    private long values;

    NbtInput(InputStream source) {
        input = new DataInputStream(new FilterInputStream(source) {
            private long remaining = 256L * 1024 * 1024;
            private void count(long count) throws IOException {
                remaining -= Math.max(count, 0);
                if (remaining < 0) throw new IOException("NBT exceeds the 256 MiB decoded input limit.");
            }
            @Override public int read() throws IOException { int v = super.read(); count(v < 0 ? 0 : 1); return v; }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                int n = in.read(b, off, len); count(n); return n;
            }
        });
    }

    int type() throws IOException { return input.readUnsignedByte(); }
    String name() throws IOException { return input.readUTF(); }
    int length() throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 4000000) throw new IOException("Invalid or excessive NBT collection length: " + length);
        return length;
    }

    NbtValue value(int type, int depth) throws IOException {
        if (depth > 64 || ++values > 40000000) throw new IOException("NBT exceeds the nesting or tag-count limit.");
        Object value = switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3 -> input.readInt();
            case 4 -> input.readLong();
            case 5 -> input.readFloat();
            case 6 -> input.readDouble();
            case 8 -> name();
            case 7, 9, 11, 12 -> {
                int element = type == 9 ? type() : type == 7 ? 1 : type == 11 ? 3 : 4;
                int count = length();
                if (element == 0 && count > 0) throw new IOException("Nonempty NBT list has END element type.");
                List<NbtValue> entries = new ArrayList<>(Math.min(count, 4096));
                for (int i = 0; i < count; i++) entries.add(value(element, depth + 1));
                yield entries;
            }
            case 10 -> {
                var entries = new LinkedHashMap<String, NbtValue>();
                int child;
                while ((child = type()) != 0) {
                    String key = name();
                    if (entries.containsKey(key)) throw new IOException("Duplicate NBT compound key: " + key);
                    entries.put(key, value(child, depth + 1));
                }
                yield entries;
            }
            default -> throw new IOException("Unknown NBT tag type: " + type);
        };
        return new NbtValue(type, value);
    }

    @Override public void close() throws IOException { input.close(); }
}

