package com.thunder.aether.server.api;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;

/** Stateless dialogue envelope; never fabricates Minecraft player/world state. */
public record ChatRequest(String schemaVersion, String requestId, String serverId, String surface,
        String principalId, String scopeId, String message) {
    public static ChatRequest parse(JsonObject json) {
        if (!json.keySet().equals(Set.of("schemaVersion","requestId","serverId","surface","principalId","scopeId","message"))) invalid();
        String version=text(json,"schemaVersion",3), id=text(json,"requestId",36);
        String server=text(json,"serverId",64), surface=text(json,"surface",9);
        String principal=text(json,"principalId",64), scope=text(json,"scopeId",64);
        if (!version.equals("1.0") || !UUID.fromString(id).toString().equalsIgnoreCase(id)
                || !server.matches("[A-Za-z0-9._-]{1,64}") || !Set.of("discord","minecraft").contains(surface)
                || !principal.matches("[a-f0-9]{64}") || !scope.matches("[a-f0-9]{64}")) invalid();
        return new ChatRequest(version,id,server,surface,principal,scope,text(json,"message",2000));
    }
    private static String text(JsonObject json,String key,int maximum) {
        var item=json.get(key);
        if (item==null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) invalid();
        String value=item.getAsString();
        if (value.isBlank() || value.length()>maximum || value.indexOf(0)>=0) invalid();
        return value;
    }
    private static void invalid() { throw new IllegalArgumentException("INVALID_REQUEST"); }
}
