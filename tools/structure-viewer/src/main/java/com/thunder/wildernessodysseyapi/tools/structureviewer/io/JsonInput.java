package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Strict bounded JSON input shared by structure and asset readers; duplicate keys are rejected. */
public final class JsonInput {
    private JsonInput() {}
    /** Reads a JSON object from a bounded local file. */
    public static JsonObject read(Path file) throws IOException {
        if(Files.size(file)>64L*1024*1024) throw new IOException("JSON exceeds 64 MiB file limit");
        return read(Files.readAllBytes(file));
    }
    /** Reads an object and rejects excessive nesting, duplicate keys, and trailing input. */
    public static JsonObject read(byte[] bytes) throws IOException {
        try(var reader=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            long[] count={0};
            JsonElement result=value(reader,0,count);
            if(!result.isJsonObject() || reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("Expected one JSON object");
            return result.getAsJsonObject();
        } catch(IllegalStateException | NumberFormatException e) {throw new IOException("Malformed JSON: "+e.getMessage(),e);}
    }
    private static JsonElement value(JsonReader r,int depth,long[] count) throws IOException {
        if(depth>64 || ++count[0]>8_000_000)throw new IOException("JSON nesting/value limit exceeded");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object=new JsonObject();r.beginObject();
                while(r.hasNext()){String name=r.nextName();if(object.has(name))throw new IOException("Duplicate JSON key: "+name);
                    object.add(name,value(r,depth+1,count));}
                r.endObject();yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array=new JsonArray();r.beginArray();
                while(r.hasNext())array.add(value(r,depth+1,count));
                r.endArray();yield array;
            }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> {r.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new IOException("Unexpected JSON token: "+r.peek());
        };
    }
}
