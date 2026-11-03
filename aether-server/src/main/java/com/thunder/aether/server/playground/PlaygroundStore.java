package com.thunder.aether.server.playground;

import com.google.gson.*;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.bootstrap.DataDirectory;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Single-owner durable jobs. FULL SQLite commits precede every acknowledgement or worker claim. */
public final class PlaygroundStore implements AutoCloseable {
    public static final class Conflict extends RuntimeException {}
    public static final class Full extends RuntimeException {}
    private final DataDirectory directory;
    private final Connection db;
    private final String serverId;

    public PlaygroundStore(Path path, String serverId) throws Exception {
        DataDirectory.rejectLinks(path);
        if(path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createDirectories(path,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
            var permissions=Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS);
            for(var permission:permissions) {
                if(!permission.name().startsWith("OWNER_"))throw new java.io.IOException("PRIVATE_STATE_DIRECTORY_REQUIRED");
            }
        }
        directory=DataDirectory.open(path); this.serverId=serverId;
        Connection connection=null;
        try {
            Path file=directory.path().resolve("playground.sqlite");
            if (Files.isSymbolicLink(file)) throw new java.io.IOException("INVALID_STORE_PATH");
            connection=DriverManager.getConnection("jdbc:sqlite:"+file); db=connection;
            sql("PRAGMA journal_mode=DELETE"); sql("PRAGMA synchronous=FULL"); sql("PRAGMA secure_delete=ON");
            sql("PRAGMA busy_timeout=3000"); sql("PRAGMA max_page_count=65536");
            sql("CREATE TABLE IF NOT EXISTS identity(server TEXT NOT NULL)");
            try(var s=db.createStatement(); var rows=s.executeQuery("SELECT server FROM identity")) {
                if(rows.next()) { if(!serverId.equals(rows.getString(1))) throw new java.io.IOException("STORE_SERVER_MISMATCH"); }
                else { update("INSERT INTO identity VALUES(?)",serverId); }
            }
            sql("CREATE TABLE IF NOT EXISTS jobs(request_id TEXT PRIMARY KEY, fingerprint TEXT, job TEXT, dataset TEXT, actor TEXT, accepted INTEGER NOT NULL, state TEXT NOT NULL)");
            sql("CREATE TABLE IF NOT EXISTS operations(id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, command TEXT, result TEXT NOT NULL)");
            sql("CREATE TABLE IF NOT EXISTS job_runtime(request_id TEXT PRIMARY KEY, identity TEXT NOT NULL)");
            // A previously claimed process may have reached its side effects. Never launch it again.
            for(JsonObject job: jobsIn("running","cancel-requested")) {
                transition(job,job.get("state").getAsString().equals("cancel-requested")?"cancelled":"failed",null);
            }
        } catch(Exception e) { if(connection!=null)connection.close(); directory.close(); throw e; }
    }

    public Path path() { return directory.path(); }
    public synchronized JsonObject find(String id) throws SQLException {
        String text=value("SELECT job FROM jobs WHERE request_id=?",id); return text==null?null:parse(text);
    }
    public synchronized String dataset(String id) throws SQLException { return value("SELECT dataset FROM jobs WHERE request_id=?",id); }
    public synchronized boolean preCancelled(String id) throws SQLException { return "tombstone".equals(value("SELECT state FROM jobs WHERE request_id=?",id)); }
    public synchronized JsonObject replay(PlaygroundContract.Training input) throws SQLException {
        String id=input.envelope().get("requestId").getAsString();
        String fingerprint=value("SELECT fingerprint FROM jobs WHERE request_id=?",id);
        if(fingerprint!=null && !fingerprint.equals(input.fingerprint())) throw new Conflict();
        return fingerprint==null?null:find(id);
    }

    public synchronized JsonObject submit(PlaygroundContract.Training input, int queueLimit) throws Exception {
        return submit(input,queueLimit,"");
    }
    public synchronized String runtimeIdentity(String id) throws SQLException { return value("SELECT identity FROM job_runtime WHERE request_id=?",id); }

    public synchronized JsonObject submit(PlaygroundContract.Training input, int queueLimit,String runtimeIdentity) throws Exception {
        return transaction(()->{
            JsonObject previous=replay(input); if(previous!=null)return previous;
            JsonObject r=input.envelope(); String id=r.get("requestId").getAsString(); PlaygroundContract.fresh(r);
            boolean cancelled="tombstone".equals(value("SELECT state FROM jobs WHERE request_id=?",id));
            if(!cancelled) {
                capacity();
                if(count("SELECT COUNT(*) FROM jobs WHERE state IN ('queued','running','cancel-requested')")>=queueLimit)throw new Full();
                String actor=PlaygroundContract.hash(r.get("actor").getAsString());
                if(count("SELECT COUNT(*) FROM jobs WHERE accepted>?",Instant.now().minusSeconds(86400).toEpochMilli())>=100
                        || count("SELECT COUNT(*) FROM jobs WHERE actor=? AND accepted>?",actor,Instant.now().minusSeconds(86400).toEpochMilli())>=10)throw new Full();
            }
            JsonObject job=new JsonObject(); job.addProperty("schemaVersion","1.0"); job.addProperty("serverId",serverId);
            job.addProperty("requestId",id); job.addProperty("jobId","job-"+UUID.randomUUID());
            for(String key:List.of("baseModelId","presetId"))job.add(key,r.get(key));
            job.add("datasetHash",r.getAsJsonObject("dataset").get("sha256")); job.addProperty("state",cancelled?"cancelled":"queued");
            String now=Instant.now().toString(); job.addProperty("createdAt",now); job.addProperty("updatedAt",now);
            job.add("progress",JsonNull.INSTANCE); job.add("candidateModelId",JsonNull.INSTANCE);
            update("INSERT INTO jobs VALUES(?,?,?,?,?,?,?) ON CONFLICT(request_id) DO UPDATE SET fingerprint=excluded.fingerprint,job=excluded.job,dataset=excluded.dataset,actor=excluded.actor,accepted=excluded.accepted,state=excluded.state",
                    id,input.fingerprint(),job.toString(),cancelled?null:input.dataset(),PlaygroundContract.hash(r.get("actor").getAsString()),Instant.now().toEpochMilli(),cancelled?"cancelled":"queued");
            update("INSERT INTO job_runtime VALUES(?,?)",id,runtimeIdentity);
            return job;
        });
    }

    public synchronized JsonObject cancel(String id) throws Exception {
        return transaction(()->{
            JsonObject job=find(id);
            if(job==null) {
                if(value("SELECT state FROM jobs WHERE request_id=?",id)==null) {
                    capacity(); update("INSERT INTO jobs VALUES(?,NULL,NULL,NULL,NULL,?,'tombstone')",id,Instant.now().toEpochMilli());
                }
                JsonObject receipt=new JsonObject(); receipt.addProperty("schemaVersion","1.0"); receipt.addProperty("serverId",serverId);
                receipt.addProperty("requestId",id); receipt.addProperty("cancelledBeforeAcceptance",true); return receipt;
            }
            String state=job.get("state").getAsString();
            if(state.equals("queued"))return transition(job,"cancelled",null);
            if(state.equals("running"))return transition(job,"cancel-requested",null);
            return job;
        });
    }

    public synchronized JsonObject claim() throws Exception {
        return transaction(()->{
            String text=value("SELECT job FROM jobs WHERE state='queued' ORDER BY accepted LIMIT 1");
            return text==null?null:transition(parse(text),"running",null);
        });
    }
    public synchronized boolean cancelling(String id) throws SQLException { return "cancel-requested".equals(value("SELECT state FROM jobs WHERE request_id=?",id)); }

    /** Caller validates and fsyncs artifact bytes before passing a candidate identifier. */
    synchronized JsonObject finish(String id, String candidate) throws Exception {
        return transaction(()->{
            JsonObject job=find(id); String state=job.get("state").getAsString();
            if(!Set.of("running","cancel-requested").contains(state))return job;
            return transition(job,state.equals("cancel-requested")?"cancelled":candidate==null?"failed":"completed",state.equals("cancel-requested")?null:candidate);
        });
    }

    private JsonObject transition(JsonObject job, String state, String candidate) throws SQLException {
        job.addProperty("state",state); job.addProperty("updatedAt",Instant.now().toString());
        job.add("candidateModelId",candidate==null?JsonNull.INSTANCE:new JsonPrimitive(candidate));
        job.add("progress",state.equals("completed")?new JsonPrimitive(1):JsonNull.INSTANCE);
        update("UPDATE jobs SET job=?,state=? WHERE request_id=?",job.toString(),state,job.get("requestId").getAsString()); return job;
    }

    /** Erases private snapshots/actors after terminal retention; replay fingerprints remain bounded. */
    public synchronized void erasePrivateBefore(Instant cutoff) throws SQLException {
        update("UPDATE jobs SET dataset=NULL, actor=NULL WHERE accepted<? AND state IN ('completed','failed','cancelled')",cutoff.toEpochMilli());
        update("UPDATE operations SET command=NULL WHERE json_extract(result,'$.state') IN ('succeeded','failed','cancelled') AND json_extract(result,'$.updatedAt')<?",cutoff.toString());
    }
    public synchronized List<JsonObject> jobsIn(String... states) throws SQLException {
        List<JsonObject> out=new ArrayList<>();
        try(var q=db.prepareStatement("SELECT job FROM jobs WHERE state IN ("+String.join(",",Collections.nCopies(states.length,"?"))+")")) {
            for(int i=0;i<states.length;i++)q.setString(i+1,states[i]);
            try(var rows=q.executeQuery()) { while(rows.next())out.add(parse(rows.getString(1))); }
        }
        return out;
    }
    public synchronized JsonObject operation(String id) throws SQLException {
        String text=value("SELECT result FROM operations WHERE id=?",id); return text==null?null:parse(text);
    }
    public synchronized JsonObject acceptOperation(JsonObject command, JsonObject result) throws Exception {
        return transaction(()->{
            String id=command.get("operation_id").getAsString();
            String old=value("SELECT fingerprint FROM operations WHERE id=?",id);
            if(old!=null) { if(!old.equals(PlaygroundContract.fingerprint(command)))throw new Conflict(); return operation(id); }
            if(count("SELECT COUNT(*) FROM operations")>=50000)throw new Full();
            update("INSERT INTO operations VALUES(?,?,?,?)",id,PlaygroundContract.fingerprint(command),command.toString(),result.toString()); return result;
        });
    }
    public synchronized void saveOperation(JsonObject result) throws SQLException { update("UPDATE operations SET result=? WHERE id=?",result.toString(),result.get("id").getAsString()); }
    public synchronized List<JsonObject> pendingOperations() throws SQLException {
        List<JsonObject> out=new ArrayList<>();
        try(var s=db.createStatement(); var rows=s.executeQuery("SELECT command FROM operations WHERE json_extract(result,'$.state')='running'")) {
            while(rows.next())out.add(parse(rows.getString(1)));
        } return out;
    }
    public synchronized boolean operationMatches(JsonObject command) throws SQLException {
        String old=value("SELECT fingerprint FROM operations WHERE id=?",command.get("operation_id").getAsString());
        if(old==null)return false; if(!old.equals(PlaygroundContract.fingerprint(command)))throw new Conflict(); return true;
    }
    private void capacity() throws SQLException { if(count("SELECT COUNT(*) FROM jobs")>=50000)throw new Full(); }
    private void sql(String text) throws SQLException { try(var s=db.createStatement()) { s.execute(text); } }
    private void update(String text,Object... args) throws SQLException { try(var s=prepare(text,args)) { s.executeUpdate(); } }
    private String value(String text,Object... args) throws SQLException { try(var s=prepare(text,args);var r=s.executeQuery()) { return r.next()?r.getString(1):null; } }
    private long count(String text,Object... args) throws SQLException { return Long.parseLong(value(text,args)); }
    private PreparedStatement prepare(String text,Object... args) throws SQLException {
        var s=db.prepareStatement(text); for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]); return s;
    }
    private interface Work<T> { T run() throws Exception; }
    private <T> T transaction(Work<T> work) throws Exception {
        db.setAutoCommit(false);
        try { T result=work.run(); db.commit(); return result; }
        catch(Exception e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
    }
    private static JsonObject parse(String text) { return JsonHttp.object(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    @Override public synchronized void close() throws Exception { try { db.close(); } finally { directory.close(); } }
}
