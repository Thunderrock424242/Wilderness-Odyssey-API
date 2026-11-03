package com.thunder.aether.server.playground;

import com.google.gson.*;
import com.thunder.aether.server.api.JsonHttp;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Strict peer v1 validation pinned to bot 60a9620. No model/runtime side effects. */
public final class PlaygroundContract {
    public static final String ID = "[A-Za-z0-9_-]{1,96}";
    public static final String MODEL = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}";
    private PlaygroundContract() {}

    public record Training(JsonObject envelope, String fingerprint, String dataset, JsonArray items) {}

    public static JsonObject object(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            unicode(text);
            JsonObject result = JsonHttp.object(text.getBytes(StandardCharsets.UTF_8));
            checkStrings(result);
            return result;
        } catch (CharacterCodingException invalid) { throw new IllegalArgumentException("INVALID_UTF8"); }
    }

    public static Training training(JsonObject r, String serverId) {
        keys(r,"schemaVersion","serverId","requestId","actor","baseModelId","presetId","dataset","reason","createdAt","expiresAt","acknowledgeTraining");
        envelope(r,serverId); model(r,"baseModelId"); match(r,"presetId",ID);
        String reason = text(r,"reason",1,2000).strip(); require(reason.length()>=10 && reason.length()<=1000);
        date(r,"createdAt"); require(bool(r,"acknowledgeTraining"));
        JsonObject d = r.getAsJsonObject("dataset"); keys(d,"sha256","json");
        String hash = match(d,"sha256","[a-f0-9]{64}"), data = text(d,"json",0,262144);
        require(data.getBytes(StandardCharsets.UTF_8).length<=262144);
        require(hash(data).equals(hash)); // Literal bytes are checked BEFORE parsing, never reserialized.
        JsonObject wrapper = object(("{\"items\":"+data+"}").getBytes(StandardCharsets.UTF_8));
        keys(wrapper,"items");
        require(wrapper.get("items").isJsonArray()); JsonArray items = wrapper.getAsJsonArray("items");
        require(items.size()>=1 && items.size()<=100); Set<String> identities = new HashSet<>();
        for (JsonElement entry : items) {
            JsonObject item = entry.getAsJsonObject(); keys(item,"id","revision","prompt","target");
            require(identities.add(match(item,"id",ID))); text(item,"revision",1,120);
            text(item,"prompt",1,4000); text(item,"target",1,12000);
        }
        return new Training(r.deepCopy(),fingerprint(r),data,items.deepCopy());
    }

    public static JsonObject sandbox(JsonObject r, String serverId, int maxTokens) {
        keys(r,"modelId","messages","maxOutputTokens","temperature","schemaVersion","serverId","requestId","actor","expiresAt");
        envelope(r,serverId); fresh(r); model(r,"modelId"); integer(r,"maxOutputTokens",1,Math.min(1024,maxTokens));
        number(r,"temperature",0,2); require(r.get("messages").isJsonArray());
        JsonArray messages = r.getAsJsonArray("messages"); require(messages.size()>=1 && messages.size()<=12);
        int chars = 0;
        for (JsonElement e : messages) {
            JsonObject message = e.getAsJsonObject(); keys(message,"role","content");
            require(Set.of("system","user","assistant").contains(text(message,"role",1,9)));
            String content = text(message,"content",1,16000).strip(); require(!content.isEmpty() && content.length()<=4000);
            chars+=content.length(); message.addProperty("content",content);
        }
        require(chars<=16000); return r;
    }

    private static void envelope(JsonObject r, String serverId) {
        require(text(r,"schemaVersion",3,3).equals("1.0"));
        require(match(r,"serverId",ID).equals(serverId)); uuid(text(r,"requestId",36,36));
        match(r,"actor",ID); date(r,"expiresAt");
    }

    /** Freshness is checked only for new acceptance, never for reconciliation/replay. */
    public static void fresh(JsonObject r) {
        Instant now = Instant.now(), expiry = date(r,"expiresAt");
        require(expiry.isAfter(now) && !expiry.isAfter(now.plusSeconds(300)));
        if (r.has("createdAt")) { require(!date(r,"createdAt").isAfter(now.plusSeconds(30)) && date(r,"createdAt").isBefore(expiry)); }
    }
    public static String uuid(String id) {
        require(id.matches("(?i)[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
                || id.equals("00000000-0000-0000-0000-000000000000") || id.equalsIgnoreCase("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        return id;
    }
    public static Instant date(JsonObject r, String key) {
        String value = text(r,key,20,50); require(value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?Z"));
        try { return Instant.parse(value); } catch(java.time.DateTimeException invalid) { throw new IllegalArgumentException("INVALID_TIMESTAMP"); }
    }
    public static void keys(JsonObject r, String... names) { require(r!=null && r.keySet().equals(Set.of(names))); }
    public static String text(JsonObject r, String key, int min, int max) {
        require(r.has(key) && r.get(key).isJsonPrimitive() && r.getAsJsonPrimitive(key).isString());
        String value = r.get(key).getAsString(); unicode(value); require(value.length()>=min && value.length()<=max); return value;
    }
    public static String match(JsonObject r, String key, String pattern) {
        String s=text(r,key,1,256); require(s.matches(pattern)); return s;
    }
    public static String model(JsonObject r, String key) {
        String s=match(r,key,MODEL); require(!s.contains("://") && !s.contains("..")); return s;
    }
    public static boolean bool(JsonObject r, String key) {
        require(r.has(key) && r.get(key).isJsonPrimitive() && r.getAsJsonPrimitive(key).isBoolean()); return r.get(key).getAsBoolean();
    }
    public static double number(JsonObject r, String key, double min, double max) {
        require(r.has(key) && r.get(key).isJsonPrimitive() && r.getAsJsonPrimitive(key).isNumber());
        double n=r.get(key).getAsDouble(); require(Double.isFinite(n) && n>=min && n<=max); return n;
    }
    public static long integer(JsonObject r, String key, long min, long max) {
        double n=number(r,key,min,max); require(n==Math.rint(n)); return (long)n;
    }
    public static String hash(String text) { return hash(text.getBytes(StandardCharsets.UTF_8)); }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String fingerprint(JsonObject r) { return hash(canonical(r).toString()); }
    private static JsonElement canonical(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject out = new JsonObject(); e.getAsJsonObject().keySet().stream().sorted().forEach(k -> out.add(k,canonical(e.getAsJsonObject().get(k)))); return out;
        }
        if (e.isJsonArray()) { JsonArray out=new JsonArray(); e.getAsJsonArray().forEach(x -> out.add(canonical(x))); return out; }
        return e;
    }
    private static void unicode(String s) {
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if (Character.isHighSurrogate(c)) { require(i+1<s.length() && Character.isLowSurrogate(s.charAt(++i))); }
            else { require(!Character.isLowSurrogate(c)); }
        }
    }
    private static void checkStrings(JsonElement e) {
        if (e.isJsonObject()) { e.getAsJsonObject().entrySet().forEach(x -> { unicode(x.getKey()); checkStrings(x.getValue()); }); }
        else if (e.isJsonArray()) { e.getAsJsonArray().forEach(PlaygroundContract::checkStrings); }
        else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) { unicode(e.getAsString()); }
    }
    public static void require(boolean value) { if (!value) { throw new IllegalArgumentException("INVALID_PEER_INPUT"); } }
}
