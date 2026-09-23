package com.thunder.aether.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thunder.aether.server.api.GenerateRequest;
import com.thunder.aether.server.api.JsonHttp;
import com.thunder.aether.server.config.ServerConfig;
import com.thunder.aether.server.model.PromptCatalog;
import com.thunder.aether.server.ollama.OllamaAdapter;
import com.thunder.aether.server.api.RequestLimits;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone public gateway. HTTP handlers release their worker after queueing;
 * generation workers own accepted exchanges, keeping health responsive during inference.
 */
public final class AetherServer implements AutoCloseable {
    private static final System.Logger LOG=System.getLogger("Aether");
    private final ServerConfig config;
    private final HttpServer http;
    private final OllamaAdapter ollama;
    private final PromptCatalog prompts;
    private final RequestLimits limits;
    private final ThreadPoolExecutor handlers;
    private final ThreadPoolExecutor generations;
    private final ScheduledThreadPoolExecutor timers=new ScheduledThreadPoolExecutor(2);
    private final Set<HttpExchange> exchanges=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile Map<String,Object> dependency;

    public AetherServer(ServerConfig config) throws Exception {
        this.config=config;
        prompts=new PromptCatalog(config.promptsFile());
        ollama=new OllamaAdapter(config,prompts);
        limits=new RequestLimits(config.requestsPerMinute());
        dependency=Map.of("ollama","UNKNOWN","ready",false,"model",config.model());
        handlers=new ThreadPoolExecutor(config.httpWorkers(),config.httpWorkers(),0,TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.AbortPolicy());
        BlockingQueue<Runnable> queue=config.queueSize()==0 ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(config.queueSize());
        generations=new ThreadPoolExecutor(config.concurrency(),config.concurrency(),0,TimeUnit.SECONDS,
                queue,new ThreadPoolExecutor.AbortPolicy());
        timers.setRemoveOnCancelPolicy(true);
        http=HttpServer.create(new InetSocketAddress(config.bind(),config.port()),64);
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
            if (path.equals("/health")) {
                if (!exchange.getRequestMethod().equals("GET")) { error(exchange,405,"","METHOD_NOT_ALLOWED"); return; }
                Map<String,Object> state=new HashMap<>(dependency); state.put("status","UP"); state.put("activeGenerations",generations.getActiveCount()); state.put("queuedGenerations",generations.getQueue().size());
                JsonHttp.send(exchange,200,state); return;
            }
            if (!path.equals("/ready") && !path.equals("/v1/aether/generate")) { error(exchange,404,"","NOT_FOUND"); return; }
            if (path.equals("/ready")) {
                if (!exchange.getRequestMethod().equals("GET")) { error(exchange,405,"","METHOD_NOT_ALLOWED"); return; }
                Map<String,Object> state=new HashMap<>(dependency);
                boolean ready=Boolean.TRUE.equals(state.get("ready"));
                state.put("status",ready ? "UP" : "DOWN");
                JsonHttp.send(exchange,ready ? 200 : 503,state); return;
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
            if (length!=null && Long.parseLong(length)>config.maxRequestBytes()) { error(exchange,413,"","REQUEST_TOO_LARGE"); return; }
            byte[] body=exchange.getRequestBody().readNBytes(config.maxRequestBytes()+1);
            if (body.length>config.maxRequestBytes()) { error(exchange,413,"","REQUEST_TOO_LARGE"); return; }
            GenerateRequest request=GenerateRequest.parse(JsonHttp.object(body),prompts.speakers());
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
            if (!handedOff) { exchanges.remove(exchange); exchange.close(); }
        }
    }

    private void generate(HttpExchange exchange,GenerateRequest request,long started,long deadline,ScheduledFuture<?> timeout) {
        String resultCode="OK";
        String responseText="";
        try {
            if (closed.get()) { error(exchange,503,request.requestId(),"SHUTTING_DOWN"); return; }
            if (System.nanoTime()>=deadline) { error(exchange,408,request.requestId(),"TIMEOUT"); return; }
            OllamaAdapter.Dialogue reply=ollama.generate(request,deadline);
            responseText=reply.response();
            Map<String,Object> response=new HashMap<>();
            response.put("requestId",request.requestId()); response.put("success",true);
            response.put("speaker",reply.speaker()); response.put("response",reply.response());
            response.put("speech",reply.speech()); response.put("emotion",reply.emotion());
            response.put("radioEffect",reply.radioEffect()); response.put("model",config.model());
            response.put("processingTimeMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            JsonHttp.send(exchange,200,response);
        } catch (OllamaAdapter.Failure failure) {
            resultCode=failure.code();
            error(exchange,resultCode.equals("TIMEOUT") ? 408 : 503,request.requestId(),resultCode);
        } catch (Exception failure) {
            resultCode="MODEL_ERROR"; error(exchange,503,request.requestId(),resultCode);
        } finally {
            timeout.cancel(false); exchanges.remove(exchange); exchange.close();
            if (config.logRequests()) {
                LOG.log(System.Logger.Level.INFO,"request={0} agent={1} result={2} latencyMs={3}",
                        request.requestId(),limits.safeLog(request.speaker()),resultCode,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            }
            if (config.logPlayerMessages()) { LOG.log(System.Logger.Level.INFO,"message={0}",limits.safeLog(request.message())); }
            if (config.logResponses()) { LOG.log(System.Logger.Level.INFO,"response={0}",limits.safeLog(responseText)); }
        }
    }

    private static void error(HttpExchange exchange,int status,String id,String code) {
        JsonHttp.send(exchange,status,Map.of("requestId",id,"success",false,"error",code));
    }

    /** Closes accepted exchanges, queues and HTTP work; never starts or stops Ollama. */
    @Override public void close() {
        if (!closed.compareAndSet(false,true)) { return; }
        http.stop(0); exchanges.forEach(HttpExchange::close); exchanges.clear();
        generations.shutdownNow(); handlers.shutdownNow(); timers.shutdownNow(); ollama.close();
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
            LOG.log(System.Logger.Level.INFO,"Public Aether gateway listening on port {0}", server.port());
        } catch (Exception failure) {
            // Configuration/library diagnostics may include secrets or paths; keep startup logs categorical.
            LOG.log(System.Logger.Level.ERROR,"Aether gateway could not start: invalid configuration or unavailable listener.");
            System.exit(1);
        }
    }
}
