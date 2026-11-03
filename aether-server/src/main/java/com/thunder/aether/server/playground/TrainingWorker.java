package com.thunder.aether.server.playground;

import com.google.gson.*;
import com.thunder.aether.server.api.JsonHttp;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.thunder.aether.server.playground.PlaygroundContract.require;

/** One controlled offline trainer per gateway, with durable claims and no restart retries. */
public final class TrainingWorker implements AutoCloseable {
    private static final System.Logger LOG=System.getLogger("AetherTraining");
    private final PlaygroundStore store;
    private final PlaygroundConfig config;
    private final Path script;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"aether-training"));
    private volatile Set<String> ready=Set.of();
    private volatile Process process;
    private volatile boolean closed;
    private long checked;

    public TrainingWorker(PlaygroundStore store, PlaygroundConfig config) throws IOException {
        this.store=store; this.config=config;
        script=store.path().resolve("trainer.py");
        if(Files.isSymbolicLink(script))throw new IOException("INVALID_TRAINER_PATH");
        try(var in=TrainingWorker.class.getResourceAsStream("/training/trainer.py")) {
            if(in==null)throw new IOException("TRAINER_MISSING");
            Files.write(script,in.readAllBytes(),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS);
        }
    }
    public void start() { worker.scheduleWithFixedDelay(this::tick,0,1,TimeUnit.SECONDS); }
    public boolean ready(String model) { return ready.contains(model); }
    public boolean anyReady() { return !ready.isEmpty(); }

    private void tick() {
        if(closed)return;
        try {
            if(System.nanoTime()-checked>TimeUnit.SECONDS.toNanos(60) || checked==0) {
                Set<String> available=new HashSet<>();
                if(config.trainingEnabled() && !config.presets().isEmpty() && localCapacity()) {
                    for(var model:config.models()) {
                        if(model.trainingDirectory()!=null && !model.trainingSha256().isEmpty() && execute(configuration(model),"--check",null,120))available.add(model.id());
                    }
                }
                ready=Set.copyOf(available); checked=System.nanoTime();
                retain();
            }
            JsonObject job=store.claim();
            if(job!=null)run(job);
        } catch(Exception failure) {
            ready=Set.of(); LOG.log(System.Logger.Level.WARNING,"Training worker unavailable; private diagnostics suppressed.");
        }
    }
    private void run(JsonObject job) throws Exception {
        String id=job.get("requestId").getAsString(); String candidate=null;
        Path work=store.path().resolve("work-"+id), output=store.path().resolve("candidate-"+id);
        try {
            if(closed || !ready(job.get("baseModelId").getAsString()) || !localCapacity())throw new IOException("TRAINING_UNAVAILABLE");
            require(config.presets().contains(job.get("presetId").getAsString()));
            require(identity(job.get("baseModelId").getAsString()).equals(store.runtimeIdentity(id)));
            String dataset=store.dataset(id); require(dataset!=null);
            Files.createDirectory(work);
            Path snapshot=work.resolve("dataset.json");
            Files.writeString(snapshot,dataset,StandardOpenOption.CREATE_NEW);
            JsonObject c=configuration(config.model(job.get("baseModelId").getAsString()));
            c.addProperty("datasetPath",snapshot.toString()); c.addProperty("outputDirectory",output.toString());
            for(String key:List.of("baseModelId","presetId","datasetHash"))c.add(key,job.get(key));
            if(execute(c,"--train",id,config.timeoutSeconds()) && !closed && !store.cancelling(id)) {
                JsonObject pinned=job.deepCopy();pinned.addProperty("approvedBaseSha256",config.model(job.get("baseModelId").getAsString()).trainingSha256());
                CandidateArtifacts.verify(output,pinned,(long)config.artifactMiB()*1048576);
                candidate="candidate-"+id;
            }
        } catch(Exception failure) {
            LOG.log(System.Logger.Level.WARNING,"Training request={0} result=failed",id);
        } finally {
            if(closed) { try { store.cancel(id); } catch(Exception ignored) { /* Restart recovery fails the claimed job. */ } }
            JsonObject terminal=store.finish(id,candidate);
            removeTree(work);
            if(!terminal.get("state").getAsString().equals("completed"))removeTree(output);
        }
    }
    private JsonObject configuration(PlaygroundConfig.Model model) {
        JsonObject c=new JsonObject(); c.addProperty("baseModelId",model.id());
        c.addProperty("modelDirectory",model.trainingDirectory()==null?"":model.trainingDirectory().toString());
        c.addProperty("approvedBaseSha256",model.trainingSha256());
        c.addProperty("device",config.device()); c.addProperty("cpuThreads",config.cpuThreads());
        c.addProperty("memoryMiB",config.memoryMiB()); c.addProperty("minFreeGpuMiB",config.minFreeGpuMiB());
        c.addProperty("artifactMiB",config.artifactMiB()); c.addProperty("timeoutSeconds",config.timeoutSeconds()); return c;
    }
    public String identity(String model) throws IOException {
        JsonObject bound=configuration(config.model(model));
        bound.addProperty("trainerSha256",PlaygroundContract.hash(Files.readAllBytes(script)));
        bound.addProperty("presetRevision","lora-v1");
        return PlaygroundContract.fingerprint(bound);
    }
    private boolean execute(JsonObject c,String mode,String requestId,int seconds) throws Exception {
        if(closed || config.python()==null || !Files.isRegularFile(config.python()))return false;
        Path input=store.path().resolve("worker-input.json");
        Files.writeString(input,c.toString(),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS);
        ProcessBuilder builder=new ProcessBuilder(config.python().toString(),"-I",script.toString(),mode,input.toString());
        // The child receives no API credentials, hub tokens, prompts or unrelated service environment.
        String systemRoot=System.getenv("SystemRoot"); builder.environment().clear();
        if(systemRoot!=null)builder.environment().put("SystemRoot",systemRoot);
        builder.environment().put("PATH",config.python().getParent().toString());
        builder.environment().put("HOME",store.path().toString());
        builder.environment().put("TMPDIR",store.path().toString());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD); builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        try {
            process=builder.start();
            while(!process.waitFor(250,TimeUnit.MILLISECONDS)) {
                if(closed || System.nanoTime()>deadline || !storageCapacity()
                        || requestId!=null && store.cancelling(requestId)) { stopProcess(); return false; }
            }
            return process.exitValue()==0;
        } finally { stopProcess(); process=null; Files.deleteIfExists(input); }
    }
    private boolean localCapacity() throws IOException {
        if(!System.getProperty("os.name").equals("Linux") || config.python()==null)return false;
        var os=java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        return os instanceof com.sun.management.OperatingSystemMXBean metrics
                && metrics.getFreeMemorySize()>=(long)config.minFreeMemoryMiB()*1048576 && storageCapacity();
    }
    private boolean storageCapacity() throws IOException {
        long size=0;
        try(var paths=Files.walk(store.path())) {
            for(Path p:paths.toList()) {
                if(Files.isSymbolicLink(p))throw new IOException("LINKED_STATE");
                if(Files.isRegularFile(p))size+=Files.size(p);
            }
        }
        return size<(long)config.storageMiB()*1048576
                && Files.getFileStore(store.path()).getUsableSpace()>(long)config.artifactMiB()*1048576;
    }
    private void retain() throws Exception {
        Instant cutoff=Instant.now().minusSeconds(config.retentionDays()*86400L);
        for(var job:store.jobsIn("completed","failed","cancelled")) {
            if(Instant.parse(job.get("updatedAt").getAsString()).isBefore(cutoff)) {
                removeTree(store.path().resolve("candidate-"+job.get("requestId").getAsString()));
            }
        }
        // Remove work/candidates left by a crashed or cancelled trainer without touching live work.
        try(var paths=Files.list(store.path())) {
            for(Path p:paths.toList()) {
                String name=p.getFileName().toString();
                if(name.startsWith("work-"))removeTree(p);
                else if(name.startsWith("candidate-")) {
                    String id=name.substring(10); JsonObject job=store.find(id);
                    if(job==null || !job.get("state").getAsString().equals("completed"))removeTree(p);
                }
            }
        }
        store.erasePrivateBefore(cutoff);
    }
    private synchronized void stopProcess() {
        Process active=process;
        if(active!=null && active.isAlive()) {
            active.descendants().forEach(ProcessHandle::destroyForcibly); active.destroyForcibly();
            try { active.waitFor(5,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
    static void removeTree(Path root) throws IOException {
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return;
        if(Files.isSymbolicLink(root))throw new IOException("LINKED_STATE");
        try(var paths=Files.walk(root)) {
            for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) { if(Files.isSymbolicLink(p))throw new IOException("LINKED_STATE"); Files.deleteIfExists(p); }
        }
    }
    @Override public void close() {
        closed=true; stopProcess(); worker.shutdownNow();
        try { if(!worker.awaitTermination(10,TimeUnit.SECONDS))throw new IllegalStateException("TRAINER_STOP_TIMEOUT"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
