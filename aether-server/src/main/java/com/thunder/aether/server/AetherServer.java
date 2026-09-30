package com.thunder.aether.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thunder.aether.server.api.GenerateRequest;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.aether.server.model.PromptCatalog;
import com.thunder.aether.server.ollama.OllamaAdapter;
import com.thunder.aether.server.api.RequestLimits;
import com.thunder.aether.server.security.ServiceAuthenticator;
import com.thunder.aether.server.security.ServiceAuthenticator.Scope;
import com.thunder.aether.server.security.AdmissionJournal;
import com.thunder.aether.server.security.RequestAudit;
import java.time.Instant;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone protected gateway. HTTP handlers release their worker after queueing;
 * generation workers own accepted exchanges, keeping health responsive during inference.
 */
public final class AetherServer implements AutoCloseable {
    private static final System.Logger LOG=System.getLogger("Aether");
    private final ServerConfig config;
    private final HttpServer http;
    private final OllamaAdapter ollama;
    private final PromptCatalog prompts;
    private final RequestLimits limits;
    private final ServiceAuthenticator authentication;
    private final AdmissionJournal admission;
    private final RequestAudit audit;
    private final Map<HttpExchange, Scope> callers = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor handlers;
    private final ThreadPoolExecutor generations;
    private final ScheduledThreadPoolExecutor timers=new ScheduledThreadPoolExecutor(2);
    private final Set<HttpExchange> exchanges=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile Map<String,Object> dependency;

    public AetherServer(ServerConfig config) throws Exception {
        this(config, new ServiceAuthenticator(config.security()));
    }

    AetherServer(ServerConfig config, ServiceAuthenticator authenticator) throws Exception {
        this.config=config;
        prompts=new PromptCatalog(config.promptsFile());
        ollama=new OllamaAdapter(config,prompts);
        limits=new RequestLimits(config.requestsPerMinute());
        authentication=authenticator;
        try { admission = new AdmissionJournal(config.security().stateDirectory()); }
        catch (Exception failure) { authentication.close(); ollama.close(); throw failure; }
        try { audit = new RequestAudit(config.security().stateDirectory()); }
        catch (Exception failure) { admission.close(); authentication.close(); ollama.close(); throw failure; }
        dependency=Map.of("ollama","UNKNOWN","ready",false,"model",config.model());
        handlers=new ThreadPoolExecutor(config.httpWorkers(),config.httpWorkers(),0,TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.AbortPolicy());
        BlockingQueue<Runnable> queue=config.queueSize()==0 ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(config.queueSize());
        generations=new ThreadPoolExecutor(config.concurrency(),config.concurrency(),0,TimeUnit.SECONDS,
                queue,new ThreadPoolExecutor.AbortPolicy());
        timers.setRemoveOnCancelPolicy(true);
        try { http=HttpServer.create(new InetSocketAddress(config.bind(),config.port()),64); }
        catch (Exception failure) {
            audit.close(); admission.close(); authentication.close(); ollama.close();
            handlers.shutdownNow(); generations.shutdownNow(); timers.shutdownNow(); throw failure;
        }
        http.setExecutor(handlers);
        http.createContext("/",this::handle);
    }

    /** Starts listening independently of Minecraft; model checks are bounded background work. */
    public void start() {
        http.start();
        timers.scheduleWithFixedDelay(this::refreshHealth,0,10,TimeUnit.SECONDS);
    }

    public int port() { return http.getAddress().getPort(); }

    /** Observes the configured model without taking any generation queue slot. */
    public void refreshHealth() {
        if (!closed.get()) { dependency=ollama.health(); }
    }

    private void handle(HttpExchange exchange) {
        exchanges.add(exchange);
        ScheduledFuture<?> watchdog=timers.schedule(exchange::close,config.requestTimeoutSeconds(),TimeUnit.SECONDS);
        boolean handedOff=false;
        try {
            String path=exchange.getRequestURI().getPath();
            if (exchange.getRequestURI().getRawQuery()!=null) { error(exchange,400,"","INVALID_REQUEST"); return; }
            Scope required = switch (path) {
                case "/health", "/ready", "/v1/admin/admission" -> path.equals("/v1/admin/admission")
                        && exchange.getRequestMethod().equals("POST") ? Scope.ADMINISTRATION : Scope.MONITORING;
                case "/v1/aether/generate", "/v1/aether/status" -> Scope.INFERENCE;
                default -> null;
            };
            if (required == null) { error(exchange,404,"","NOT_FOUND"); return; }
            Scope caller = authentication.authenticate(exchange.getRequestHeaders(), Instant.now());
            if (caller == null) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                error(exchange,401,"","UNAUTHORIZED"); return;
            }
            callers.put(exchange, caller);
            if (caller != required) { error(exchange,403,"","FORBIDDEN"); return; }
            if (path.equals("/v1/admin/admission")) { administer(exchange); return; }
            if (path.equals("/v1/aether/status")) {
                if (!exchange.getRequestMethod().equals("GET")) { error(exchange,405,"","METHOD_NOT_ALLOWED"); return; }
                send(exchange,200,Map.of("available",config.activation().permits(config.model())
                        && admission.allowsInference() && Boolean.TRUE.equals(dependency.get("ready")),
                        "status","UP", "error", !config.activation().permits(config.model()) ? "ACTIVATION_REQUIRED"
                                : !admission.allowsInference() ? "INFERENCE_PAUSED" : "")); return;
            }
            if (path.equals("/health")) {
                if (!exchange.getRequestMethod().equals("GET")) { error(exchange,405,"","METHOD_NOT_ALLOWED"); return; }
                Map<String,Object> state=new HashMap<>(dependency); state.put("status","UP"); state.put("activeGenerations",generations.getActiveCount()); state.put("queuedGenerations",generations.getQueue().size());
                state.putAll(admission.snapshot());
                send(exchange,200,state); return;
            }
            if (!path.equals("/ready") && !path.equals("/v1/aether/generate")) { error(exchange,404,"","NOT_FOUND"); return; }
            if (path.equals("/ready")) {
                if (!exchange.getRequestMethod().equals("GET")) { error(exchange,405,"","METHOD_NOT_ALLOWED"); return; }
                Map<String,Object> state=new HashMap<>(dependency);
                boolean ready=config.activation().permits(config.model()) && admission.allowsInference()
                        && Boolean.TRUE.equals(state.get("ready"));
                state.put("ready", ready);
                state.put("status",ready ? "UP" : "DOWN");
                send(exchange,ready ? 200 : 503,state); return;
            }
            if (!exchange.getRequestMethod().equals("POST")) { error(exchange,405,"","METHOD_NOT_ALLOWED"); return; }
            if (!limits.allow(System.nanoTime())) {
                exchange.getResponseHeaders().set("Retry-After","60"); error(exchange,429,"","RATE_LIMITED"); return;
            }
            String contentType=exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType==null || !contentType.split(";",2)[0].trim().equalsIgnoreCase("application/json")) {
                error(exchange,415,"","UNSUPPORTED_MEDIA_TYPE"); return;
            }
            String length=exchange.getRequestHeaders().getFirst("Content-Length");
            if (length != null) {
                long declared = Long.parseLong(length);
                if (declared > config.maxRequestBytes()) {
                    // A small known-size overshoot must finish transmitting before
                    // the response is closed. Otherwise the client can receive a
                    // TCP reset instead of the intended HTTP 413 response.
                    // Never drain unbounded attacker-controlled request bodies.
                    if (declared <= (long) config.maxRequestBytes() + 8192L) {
                        byte[] scratch = new byte[4096];
                        long remaining = declared;
                        while (remaining > 0) {
                            int n = exchange.getRequestBody().read(scratch, 0,
                                    (int) Math.min(scratch.length, remaining));
                            if (n < 0) break;
                            remaining -= n;
                        }
                    }
                    error(exchange, 413, "", "REQUEST_TOO_LARGE");
                    return;
                }
            }
            byte[] body=exchange.getRequestBody().readNBytes(config.maxRequestBytes()+1);
            if (body.length>config.maxRequestBytes()) { error(exchange,413,"","REQUEST_TOO_LARGE"); return; }
            GenerateRequest request=GenerateRequest.parse(JsonHttp.object(body),prompts.speakers());
            if (!request.serverId().equals(config.security().minecraftServerId())) {
                error(exchange,403,request.requestId(),"SERVER_ID_MISMATCH"); return;
            }
            if (!config.activation().permits(config.model())) {
                error(exchange,503,request.requestId(),"ACTIVATION_REQUIRED"); return;
            }
            if (!admission.allowsInference()) { error(exchange,503,request.requestId(),"INFERENCE_PAUSED"); return; }
            long received=System.nanoTime();
            // Queue waiting and both Ollama calls consume one total service deadline.
            long deadline=received+TimeUnit.SECONDS.toNanos(config.timeoutSeconds());
            ScheduledFuture<?> generationTimeout=timers.schedule(exchange::close,config.timeoutSeconds()+1L,TimeUnit.SECONDS);
            try {
                generations.execute(() -> generate(exchange,request,received,deadline,generationTimeout));
                handedOff=true;
            } catch (RejectedExecutionException overload) {
                generationTimeout.cancel(false);
                exchange.getResponseHeaders().set("Retry-After","1"); error(exchange,503,request.requestId(),"OVERLOADED");
            }
        } catch (Exception invalid) {
            error(exchange,400,"","INVALID_REQUEST");
        } finally {
            watchdog.cancel(false);
            if (!handedOff) { exchanges.remove(exchange); callers.remove(exchange); exchange.close(); }
        }
    }

    private void generate(HttpExchange exchange,GenerateRequest request,long started,long deadline,ScheduledFuture<?> timeout) {
        String resultCode="OK";
        try {
            if (closed.get()) { error(exchange,503,request.requestId(),"SHUTTING_DOWN"); return; }
            if (!admission.allowsInference()) { error(exchange,503,request.requestId(),"INFERENCE_PAUSED"); return; }
            if (System.nanoTime()>=deadline) { error(exchange,408,request.requestId(),"TIMEOUT"); return; }
            OllamaAdapter.Dialogue reply=ollama.generate(request,deadline);
            Map<String,Object> response=new HashMap<>();
            response.put("requestId",request.requestId()); response.put("success",true);
            response.put("speaker",reply.speaker()); response.put("response",reply.response());
            response.put("speech",reply.speech()); response.put("emotion",reply.emotion());
            response.put("radioEffect",reply.radioEffect()); response.put("model",config.model());
            response.put("processingTimeMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            send(exchange,200,response);
        } catch (OllamaAdapter.Failure failure) {
            resultCode=failure.code();
            error(exchange,resultCode.equals("TIMEOUT") ? 408 : 503,request.requestId(),resultCode);
        } catch (Exception failure) {
            resultCode="MODEL_ERROR"; error(exchange,503,request.requestId(),resultCode);
        } finally {
            timeout.cancel(false); exchanges.remove(exchange); callers.remove(exchange); exchange.close();
            if (config.logRequests()) {
                LOG.log(System.Logger.Level.INFO,"request={0} agent={1} result={2} latencyMs={3}",
                        request.requestId(),limits.safeLog(request.speaker()),resultCode,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            }
        }
    }

    private void administer(HttpExchange exchange) {
        if (exchange.getRequestMethod().equals("GET")) { send(exchange,200,admission.snapshot()); return; }
        if (!exchange.getRequestMethod().equals("POST")) { error(exchange,405,"","METHOD_NOT_ALLOWED"); return; }
        if (!config.security().administrationEnabled()) { error(exchange,403,"","ADMINISTRATION_DISABLED"); return; }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.split(";",2)[0].trim().equalsIgnoreCase("application/json")) {
            error(exchange,415,"","UNSUPPORTED_MEDIA_TYPE"); return;
        }
        try {
            byte[] body = exchange.getRequestBody().readNBytes(4097);
            if (body.length > 4096) { error(exchange,413,"","REQUEST_TOO_LARGE"); return; }
            send(exchange,200,admission.apply(JsonHttp.object(body)));
        } catch (AdmissionJournal.Conflict conflict) { error(exchange,409,"","REVISION_OR_REPLAY_CONFLICT"); }
        catch (IllegalArgumentException invalid) { error(exchange,400,"","INVALID_COMMAND"); }
        catch (Exception unavailable) { error(exchange,503,"","ADMINISTRATION_UNAVAILABLE"); }
    }

    private void send(HttpExchange exchange, int status, Object body) {
        String path = exchange.getRequestURI().getPath();
        String operation = Set.of("/health", "/ready", "/v1/aether/status", "/v1/aether/generate", "/v1/admin/admission")
                .contains(path) ? path : "UNKNOWN";
        Scope caller = callers.get(exchange);
        if (!audit.record(operation, caller == null ? "UNAUTHENTICATED" : caller.name(), status)) {
            JsonHttp.send(exchange,503,Map.of("success",false,"error","AUDIT_UNAVAILABLE")); return;
        }
        JsonHttp.send(exchange,status,body);
    }

    private void error(HttpExchange exchange,int status,String id,String code) {
        send(exchange,status,Map.of("requestId",id,"success",false,"error",code));
    }

    /** Closes accepted exchanges, queues and HTTP work; never starts or stops Ollama. */
    @Override public void close() {
        if (!closed.compareAndSet(false,true)) { return; }
        http.stop(0); exchanges.forEach(HttpExchange::close); exchanges.clear();
        generations.shutdownNow(); handlers.shutdownNow(); timers.shutdownNow(); ollama.close(); authentication.close();
        callers.clear();
        try { try { audit.close(); } finally { admission.close(); } }
        catch (java.io.IOException failure) { LOG.log(System.Logger.Level.ERROR,"Gateway state could not close cleanly."); }
    }

    /** Installs process-wide HTTP limits before the standalone listener is first created. */
    public static void configureHttpLimits(ServerConfig config) {
            // JDK 21 reads these once, before the first HttpServer is created. Set them only
            // in the standalone process, never in an embedding Minecraft/test JVM.
            System.setProperty("jdk.httpserver.maxConnections", "128");
            System.setProperty("sun.net.httpserver.maxIdleConnections", "32");
            System.setProperty("sun.net.httpserver.maxReqHeaders", "32");
            System.setProperty("sun.net.httpserver.maxReqHeaderSize", "16384");
            // OpenJDK 21 converts these properties from seconds internally.
            System.setProperty("sun.net.httpserver.maxReqTime", Integer.toString(config.requestTimeoutSeconds()));
            System.setProperty("sun.net.httpserver.maxRspTime", Integer.toString(config.timeoutSeconds() + 2));
    }

    /** Entry point for java -jar Aether-AI-Server.jar [--config file]. */
    public static void main(String[] args) {
        try {
            if (args.length!=0 && (args.length!=2 || !args[0].equals("--config"))) {
                throw new IllegalArgumentException("INVALID_ARGUMENTS");
            }
            ServerConfig config=ServerConfig.load(args.length==0 ? null : Path.of(args[1]),System.getenv());
            configureHttpLimits(config);
            AetherServer server=new AetherServer(config);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close,"aether-shutdown"));
            server.start();
            LOG.log(System.Logger.Level.INFO,"Protected Aether gateway listening on port {0}", server.port());
        } catch (Exception failure) {
            // Configuration/library diagnostics may include secrets or paths; keep startup logs categorical.
            LOG.log(System.Logger.Level.ERROR,"Aether gateway could not start: invalid configuration or unavailable listener.");
            System.exit(1);
        }
    }
}
