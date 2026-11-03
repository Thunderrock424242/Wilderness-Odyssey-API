package com.thunder.aether.server.playground;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.api.RequestLimits;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.aether.server.ollama.OllamaAdapter;
import com.thunder.aether.server.security.AdmissionJournal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import static com.thunder.aether.server.playground.PlaygroundContract.*;

/** Fixed peer route family; all authentication is performed by the owning gateway first. */
public final class PlaygroundService implements AutoCloseable {
    public static final String PREFIX="/v1/admin/playground";
    public interface Sender { void send(HttpExchange exchange,int status,Object body); }
    private final ServerConfig config;
    private final PlaygroundStore store;
    private final TrainingWorker trainer;
    private final MainServerAdapter main;
    private final OllamaAdapter ollama;
    private final AdmissionJournal admission;
    private final RequestLimits requests=new RequestLimits(30);
    private final Semaphore sandbox=new Semaphore(1);
    private volatile Set<String> installed=Set.of();

    public PlaygroundService(ServerConfig config,OllamaAdapter ollama,AdmissionJournal admission) throws Exception {
        this.config=config;this.ollama=ollama;this.admission=admission;
        boolean enabled=config.playground().sandboxEnabled() || config.playground().trainingEnabled() || config.security().serviceWriteEnabled();
        if(enabled)require(config.security().minecraftServerId().matches(ID));
        store=enabled?new PlaygroundStore(config.playground().stateDirectory(),config.security().minecraftServerId()):null;
        TrainingWorker worker=null;
        try {
            worker=store==null?null:new TrainingWorker(store,config.playground()); trainer=worker;
            main=new MainServerAdapter(config,store,admission);
        } catch(Exception failure) { if(worker!=null)worker.close(); if(store!=null)store.close(); throw failure; }
    }
    public void start() { if(trainer!=null)trainer.start(); }
    public void refreshModels() {
        installed=!config.playground().models().isEmpty()?ollama.installedModels(config.playground().models().stream()
                .map(PlaygroundConfig.Model::id).collect(java.util.stream.Collectors.toSet())):Set.of();
    }
    public JsonObject capabilities() {
        JsonObject c=new JsonObject(); c.addProperty("schemaVersion","1.0"); c.addProperty("serverId",config.security().minecraftServerId());
        JsonArray models=new JsonArray();for(var m:config.playground().models())models.add(JsonHttp.JSON.toJsonTree(Map.of("id",m.id(),"label",m.label())));c.add("models",models);
        JsonArray presets=new JsonArray();for(String id:config.playground().presets()) {
            var p=PlaygroundConfig.PRESETS.get(id);presets.add(JsonHttp.JSON.toJsonTree(Map.of("id",p.id(),"label",p.label(),"maxExamples",p.maxExamples())));
        }c.add("presets",presets);
        boolean admin=config.security().administrationEnabled();
        c.add("features",JsonHttp.JSON.toJsonTree(Map.of("sandbox",admin && config.playground().sandboxEnabled() && !installed.isEmpty(),
                "training",admin && trainer!=null && trainer.anyReady(),"cancellation",admin && store!=null,"admission",admin)));
        c.add("limits",JsonHttp.JSON.toJsonTree(Map.of("maxMessages",12,"maxInputCharacters",16000,"maxOutputTokens",config.maxOutputTokens())));
        c.addProperty("revision",fingerprint(c).substring(0,32));return c;
    }
    public void handle(HttpExchange e, Sender sender, Map<String,Object> dependency,Instant checkedAt,Long latency) {
        try {
            String path=e.getRequestURI().getPath(), method=e.getRequestMethod();
            if(path.equals("/v1/service/health") && method.equals("GET")) { sender.send(e,200,main.health(dependency,checkedAt,latency));return; }
            if(path.equals("/v1/service/capabilities") && method.equals("GET")) { sender.send(e,200,main.capabilities(installed));return; }
            if(path.startsWith("/v1/service/operations/") && method.equals("GET")) {
                String id=path.substring("/v1/service/operations/".length());require(id.matches(ID));
                var op=main.operation(id); if(op==null)failure(e,sender,404,"NOT_FOUND");else sender.send(e,200,op);return;
            }
            if(path.equals("/v1/service/operations") && method.equals("POST")) {
                if(!config.security().serviceWriteEnabled()) { failure(e,sender,403,"SERVICE_WRITE_DISABLED");return; }
                sender.send(e,200,main.submit(body(e,8192)));return;
            }
            if(path.equals(PREFIX+"/capabilities") && method.equals("GET")) { sender.send(e,200,capabilities());return; }
            if(!config.security().administrationEnabled()) { failure(e,sender,403,"ADMINISTRATION_DISABLED");return; }
            if(path.equals(PREFIX+"/sandbox") && method.equals("POST")) { infer(e,sender);return; }
            if(path.equals(PREFIX+"/training/jobs") && method.equals("POST")) {
                var input=training(body(e,524287),config.security().minecraftServerId());
                if(store==null) { failure(e,sender,503,"TRAINING_UNAVAILABLE");return; }
                var replay=store.replay(input); if(replay!=null) { sender.send(e,200,replay);return; }
                String model=input.envelope().get("baseModelId").getAsString(), preset=input.envelope().get("presetId").getAsString();
                config.playground().model(model);require(config.playground().presets().contains(preset));
                require(input.items().size()<=PlaygroundConfig.PRESETS.get(preset).maxExamples());fresh(input.envelope());
                if(!store.preCancelled(input.envelope().get("requestId").getAsString()) && (trainer==null || !trainer.ready(model))) { failure(e,sender,503,"TRAINING_UNAVAILABLE");return; }
                sender.send(e,200,store.submit(input,config.playground().queueLimit(),trainer.identity(model)));return;
            }
            String base=PREFIX+"/training/jobs/by-request/";
            if(path.startsWith(base)) {
                String rest=path.substring(base.length());boolean cancel=rest.endsWith("/cancel");
                String id=cancel?rest.substring(0,rest.length()-7):rest;uuid(id);
                if(store==null) { failure(e,sender,503,"JOBS_UNAVAILABLE");return; }
                if(cancel && method.equals("POST")) {
                    JsonObject b=body(e,1024);keys(b,"requestId");require(text(b,"requestId",36,36).equals(id));sender.send(e,200,store.cancel(id));return;
                }
                if(!cancel && method.equals("GET")) {
                    JsonObject job=store.find(id);if(job==null)failure(e,sender,404,"NOT_FOUND");else sender.send(e,200,job);return;
                }
            }
            failure(e,sender,404,"NOT_FOUND");
        } catch(PlaygroundStore.Conflict | AdmissionJournal.Conflict conflict) { failure(e,sender,409,"IDENTITY_OR_REVISION_CONFLICT"); }
        catch(PlaygroundStore.Full full) { failure(e,sender,429,"QUOTA_EXCEEDED"); }
        catch(IllegalArgumentException | IllegalStateException | ClassCastException invalid) { failure(e,sender,422,"REJECTED_INPUT"); }
        catch(Exception unavailable) { failure(e,sender,503,"DEPENDENCY_UNAVAILABLE"); }
    }
    private void infer(HttpExchange e,Sender sender) throws Exception {
        JsonObject r=sandbox(body(e,65536),config.security().minecraftServerId(),config.maxOutputTokens());
        String model=r.get("modelId").getAsString();config.playground().model(model);
        if(!config.playground().sandboxEnabled() || !installed.contains(model) || !admission.allowsInference()) { failure(e,sender,503,"SANDBOX_UNAVAILABLE");return; }
        if(!requests.allow(System.nanoTime()) || !sandbox.tryAcquire()) { failure(e,sender,429,"SANDBOX_BUSY");return; }
        long start=System.nanoTime();
        try {
            long window=Math.min(20000,Math.max(1,java.time.Duration.between(Instant.now(),date(r,"expiresAt")).toMillis()));
            var answer=ollama.sandbox(model,r.getAsJsonArray("messages"),r.get("maxOutputTokens").getAsInt(),r.get("temperature").getAsDouble(),start+TimeUnit.MILLISECONDS.toNanos(window));
            JsonObject out=new JsonObject();out.addProperty("schemaVersion","1.0");out.add("serverId",r.get("serverId"));out.add("requestId",r.get("requestId"));
            out.addProperty("modelId",model);out.addProperty("answer",answer.answer());out.addProperty("finishReason",answer.finishReason());
            out.addProperty("durationMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));sender.send(e,200,out);
        } finally { sandbox.release(); }
    }
    private static JsonObject body(HttpExchange e,int limit) throws Exception {
        String type=e.getRequestHeaders().getFirst("Content-Type");require(type!=null && type.split(";",2)[0].trim().equalsIgnoreCase("application/json"));
        byte[] bytes=e.getRequestBody().readNBytes(limit+1);require(bytes.length<=limit);return object(bytes);
    }
    private static void failure(HttpExchange e,Sender sender,int code,String category) { sender.send(e,code,Map.of("error",category)); }
    @Override public void close() throws Exception { try { if(trainer!=null)trainer.close(); } finally { if(store!=null)store.close(); } }
}
