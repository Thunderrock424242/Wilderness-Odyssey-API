package com.thunder.aether.server.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;

/** Validated, immutable game data; unknown fields never become model instructions. */
public record GenerateRequest(String requestId, String serverId, String world, String playerId,
        String playerName, String speaker, String message, Context context, List<History> history) {
    public record Context(String dimension, String biome, boolean surface, String activationInterface,
                          List<String> tags, String playerMemory) {}
    public record History(String role, String speaker, String text) {}

    public static GenerateRequest parse(JsonObject json, List<String> speakers) {
        String id = text(json,"requestId",36,true), player = text(json,"playerId",36,true);
        if (!UUID.fromString(id).toString().equalsIgnoreCase(id)
                || !UUID.fromString(player).toString().equalsIgnoreCase(player)) { invalid(); }
        String server = text(json,"serverId",64,true);
        if (!server.matches("[A-Za-z0-9._-]+")) { invalid(); }
        String world = text(json,"world",128,true);
        String speaker = text(json,"speaker",64,false);
        String agent = text(json,"agent",64,false);
        if (speaker.isBlank()) { speaker = agent; }
        if (!speaker.isBlank()) {
            String selected = speaker;
            speaker = speakers.stream().filter(s -> s.equalsIgnoreCase(selected)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("INVALID_AGENT"));
        }
        JsonObject ctx = json.has("context") ? json.getAsJsonObject("context") : new JsonObject();
        String dimension = text(ctx,"dimension",128,false);
        if (dimension.isBlank()) { dimension = world; }
        if (!world.equals(dimension)) { invalid(); }
        boolean surface = false;
        if (ctx.has("surface")) {
            if (!ctx.get("surface").isJsonPrimitive() || !ctx.get("surface").getAsJsonPrimitive().isBoolean()) { invalid(); }
            surface = ctx.get("surface").getAsBoolean();
        }
        java.util.ArrayList<String> tags = new java.util.ArrayList<>();
        JsonArray rawTags = ctx.has("tags") ? ctx.getAsJsonArray("tags") : new JsonArray();
        if (rawTags.size() > 128) { invalid(); }
        for (JsonElement tag : rawTags) {
            if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()
                    || tag.getAsString().length() > 256) { invalid(); }
            tags.add(tag.getAsString());
        }
        Context context = new Context(dimension,text(ctx,"biome",128,false),surface,
                text(ctx,"activationInterface",64,false),List.copyOf(tags),text(ctx,"playerMemory",4096,false));
        java.util.ArrayList<History> history = new java.util.ArrayList<>();
        JsonArray rawHistory = json.has("history") ? json.getAsJsonArray("history") : new JsonArray();
        if (rawHistory.size() > 20) { invalid(); }
        for (JsonElement entry : rawHistory) {
            JsonObject item = entry.getAsJsonObject();
            String role = text(item,"role",16,true);
            if (!role.equals("user") && !role.equals("assistant")) { invalid(); }
            history.add(new History(role,text(item,"speaker",64,false),text(item,"text",4000,true)));
        }
        return new GenerateRequest(id,server,world,player,text(json,"playerName",64,true),speaker,
                text(json,"message",4000,true),context,List.copyOf(history));
    }

    private static String text(JsonObject value,String key,int max,boolean required) {
        if (!value.has(key)) { if (required) { invalid(); } return ""; }
        JsonElement element = value.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) { invalid(); }
        String text = element.getAsString();
        if (text.length() > max || text.indexOf(0) >= 0 || required && text.isBlank()) { invalid(); }
        return text;
    }
    private static void invalid() { throw new IllegalArgumentException("INVALID_REQUEST"); }
}
