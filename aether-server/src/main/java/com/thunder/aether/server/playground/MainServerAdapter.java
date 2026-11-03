package com.thunder.aether.server.playground;

import com.google.gson.*;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.aether.server.security.AdmissionJournal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.thunder.aether.server.playground.PlaygroundContract.*;

/** Pinned main-client adapter. Only native admission pause is a supported mutation. */
public final class MainServerAdapter {
    private final ServerConfig config;
    private final PlaygroundStore store;
    private final AdmissionJournal admission;
    private final String bootId=UUID.randomUUID().toString();
    private final AtomicLong sequence=new AtomicLong();

    public MainServerAdapter(ServerConfig config,PlaygroundStore store,AdmissionJournal admission) throws Exception {
        this.config=config; this.store=store; this.admission=admission;
        if(store!=null)for(JsonObject command:store.pendingOperations())complete(command);
    }

    public JsonObject health(Map<String,Object> dependency, Instant checkedAt, Long latencyMs) {
        JsonObject observation=new JsonObject(); observation.addProperty("schema_version","1.0");
        observation.addProperty("server_id",config.security().minecraftServerId()); observation.addProperty("boot_id",bootId);
        observation.addProperty("sequence",sequence.incrementAndGet()); observation.addProperty("observed_at",Instant.now().toString());
        JsonObject minecraft=new JsonObject(); minecraft.addProperty("status","unknown");
        for(String key:List.of("checked_at","players_online","players_max","tps","mspt","minecraft_version","modpack_version"))minecraft.add(key,JsonNull.INSTANCE);
        observation.add("minecraft",minecraft);
        JsonObject aether=new JsonObject(); boolean fresh=checkedAt!=null && checkedAt.isAfter(Instant.now().minusSeconds(30));
        boolean reachable="UP".equals(dependency.get("ollama"));
        boolean available=fresh && config.activation().permits(config.model()) && admission.allowsInference() && Boolean.TRUE.equals(dependency.get("ready"));
        aether.addProperty("status",!fresh?"unknown":available?"online":reachable?"degraded":"offline");
        aether.add("checked_at",fresh?new JsonPrimitive(checkedAt.toString()):JsonNull.INSTANCE);
        aether.add("ollama_reachable",fresh?new JsonPrimitive(reachable):JsonNull.INSTANCE);
        aether.add("inference_available",fresh?new JsonPrimitive(available):JsonNull.INSTANCE);
        aether.add("latency_ms",fresh && latencyMs!=null?new JsonPrimitive(latencyMs):JsonNull.INSTANCE);
        aether.addProperty("requests_paused",!admission.allowsInference()); observation.add("aether",aether); return observation;
    }

    public JsonObject capabilities(Set<String> installed) throws java.io.IOException {
        if(config.contextTokens()==0)throw new java.io.IOException("NATIVE_CONTEXT_SETTING_REQUIRED");
        JsonObject result=new JsonObject(); result.addProperty("schema_version","1.0"); result.addProperty("server_id",config.security().minecraftServerId());
        JsonArray actions=new JsonArray(); if(store!=null && config.security().serviceWriteEnabled())actions.add("ai.pause"); result.add("actions",actions);
        String revision=admission.snapshot().get("revision").toString(); result.addProperty("ai_requests_revision",revision);
        JsonObject models=new JsonObject(); models.addProperty("schemaVersion","1.0"); models.addProperty("revision","fixed-1");
        // The older admin model contract permits id characters only; stable opaque aliases map approved runtime IDs.
        boolean activeApproved=config.playground().models().stream().anyMatch(m->m.id().equals(config.model()));
        models.add("activeModelId",activeApproved && config.activation().permits(config.model())?new JsonPrimitive(alias(config.model())):JsonNull.INSTANCE);
        JsonArray approved=new JsonArray();
        for(var model:config.playground().models()) {
            JsonObject m=new JsonObject(); m.addProperty("id",alias(model.id())); m.addProperty("name",model.label()); m.addProperty("approved",true);
            m.addProperty("readiness",installed.contains(model.id())?"ready":"unavailable");
            m.addProperty("detail",installed.contains(model.id())?"Approved runtime model is installed.":"Approved runtime model is unavailable."); approved.add(m);
        }
        models.add("models",approved);
        // This is the actual fixed native inference profile; no settings-write action is advertised.
        models.add("settings",JsonHttp.JSON.toJsonTree(Map.of("temperature",0.65,"numPredict",config.maxOutputTokens(),"numCtx",config.contextTokens())));
        models.add("permittedSettings",JsonHttp.JSON.toJsonTree(Map.of("temperature",Map.of("min",0.65,"max",0.65),
                "numPredict",Map.of("min",config.maxOutputTokens(),"max",config.maxOutputTokens()),"numCtx",Map.of("min",config.contextTokens(),"max",config.contextTokens()))));
        result.add("models",models); return result;
    }
    private static String alias(String id) { return "model-"+hash(id).substring(0,24); }

    public synchronized JsonObject submit(JsonObject command) throws Exception {
        require(store!=null && config.security().serviceWriteEnabled()); validate(command);
        if(store.operationMatches(command))return store.operation(command.get("operation_id").getAsString());
        Instant now=Instant.now(), expiry=Instant.parse(command.get("expires_at").getAsString());
        require(expiry.isAfter(now) && !expiry.isAfter(now.plusSeconds(300)));
        require(!Instant.parse(command.get("created_at").getAsString()).isAfter(now.plusSeconds(30)));
        if(!command.get("expected_revision").getAsString().equals(admission.snapshot().get("revision").toString()))throw new PlaygroundStore.Conflict();
        JsonObject result=new JsonObject(); result.add("id",command.get("operation_id")); result.add("kind",command.get("action"));
        result.addProperty("state","running"); result.addProperty("summary","Accepted native admission request.");
        result.add("requestedAt",command.get("created_at")); result.addProperty("updatedAt",now.toString());
        store.acceptOperation(command,result); // Durable identity before native journal side effect.
        return complete(command);
    }
    private JsonObject complete(JsonObject command) throws Exception {
        JsonObject nativeCommand=new JsonObject();
        nativeCommand.addProperty("requestId",UUID.nameUUIDFromBytes(("main-service:"+command.get("operation_id").getAsString()).getBytes(StandardCharsets.UTF_8)).toString());
        nativeCommand.addProperty("expectedRevision",Long.parseLong(command.get("expected_revision").getAsString()));
        nativeCommand.add("expiresAt",command.get("expires_at")); nativeCommand.add("paused",command.getAsJsonObject("parameters").get("paused"));
        nativeCommand.addProperty("actor","service-"+hash(command.get("actor").getAsString()).substring(0,32));
        JsonObject result=store.operation(command.get("operation_id").getAsString());
        try {
            if(admission.reconcile(nativeCommand)==null)admission.apply(nativeCommand);
            result.addProperty("state","succeeded"); result.addProperty("summary","Native admission change committed.");
        } catch(AdmissionJournal.Capacity full) {
            result.addProperty("state","failed");result.addProperty("summary","Native admission capacity is exhausted.");
        } catch(AdmissionJournal.Conflict conflict) {
            result.addProperty("state","failed");result.addProperty("summary","Admission revision changed.");
        } catch(IllegalArgumentException expired) {
            result.addProperty("state","cancelled");result.addProperty("summary","Request expired before native acceptance.");
        } catch(java.io.IOException unavailable) {
            // Leave running when durability is ambiguous; restart reconciliation checks the native journal.
            return result;
        }
        result.addProperty("updatedAt",Instant.now().toString()); store.saveOperation(result); return result;
    }
    public synchronized JsonObject operation(String id) throws Exception {
        if(store==null)return null;
        JsonObject result=store.operation(id);
        if(result!=null && result.get("state").getAsString().equals("running")) {
            for(var command:store.pendingOperations())if(command.get("operation_id").getAsString().equals(id))return complete(command);
        }
        return result;
    }
    private void validate(JsonObject c) {
        keys(c,"operation_id","server_id","action","parameters","expected_revision","created_at","expires_at","actor","reason");
        match(c,"operation_id",ID); require(text(c,"server_id",1,100).equals(config.security().minecraftServerId()));
        require(text(c,"action",1,80).equals("ai.pause")); keys(c.getAsJsonObject("parameters"),"paused"); bool(c.getAsJsonObject("parameters"),"paused");
        match(c,"expected_revision","(?:0|[1-9][0-9]{0,9})"); require(Long.parseLong(c.get("expected_revision").getAsString())<=9999999999L);
        String actor=text(c,"actor",1,200); require(!actor.isBlank());
        String reason=text(c,"reason",1,2000).strip(); require(reason.length()>=10 && reason.length()<=1000);
        try {
            Instant created=Instant.parse(text(c,"created_at",20,50)), expiry=Instant.parse(text(c,"expires_at",20,50)); require(created.isBefore(expiry));
        } catch(java.time.DateTimeException invalid) { throw new IllegalArgumentException("INVALID_TIMESTAMP"); }
    }
}
