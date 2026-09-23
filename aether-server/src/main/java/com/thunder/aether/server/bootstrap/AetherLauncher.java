package com.thunder.aether.server.bootstrap;

import com.thunder.aether.server.AetherServer;
import com.thunder.aether.server.config.ServerConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

/** Executable distribution entry point: extract the bundled payload, own Ollama, then serve Aether. */
public final class AetherLauncher {
    private static final System.Logger LOG = System.getLogger("AetherLauncher");
    private AetherLauncher() {}

    /** Runs managed setup by default, with an explicit compatibility path for external Ollama. */
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Aether server: java -jar Aether-AI-Server-<platform>.jar [--data-dir directory] [--setup-only]");
            System.out.println("Gateway-only mode: --external [--config file]. Existing --config file usage is also supported.");
            System.out.println("Bundled mode extracts Ollama and the model beside the application data; no system installation.");
            return;
        }
        if (args.length > 0 && args[0].equals("--external")) {
            AetherServer.main(Arrays.copyOfRange(args, 1, args.length)); return;
        }
        // Preserve the previous gateway-only CLI for operators with an external Ollama installation.
        if (args.length == 2 && args[0].equals("--config")) { AetherServer.main(args); return; }
        try {
            Path data = Path.of("aether");
            boolean setupOnly = false;
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--setup-only")) { setupOnly = true; }
                else if (args[i].equals("--data-dir") && i + 1 < args.length) { data = Path.of(args[++i]); }
                else { throw new IOException("Use --help for supported launcher options."); }
            }
            run(data, setupOnly);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            if (Thread.currentThread().isInterrupted()) { return; }
            // Only our own categorical IO failures are printable. Library/config exceptions may contain secrets.
            String message = failure instanceof IOException ? failure.getMessage() : "Setup failed; check bundle compatibility, settings and host resources.";
            LOG.log(System.Logger.Level.ERROR, message);
            System.exit(1);
        }
    }

    private static void run(Path directory, boolean setupOnly) throws Exception {
        BundleManifest manifest = BundleManifest.read(AetherLauncher.class.getResourceAsStream("/bundle/manifest.json"));
        manifest.requireCurrentPlatform();
        AtomicReference<ManagedOllama> runtime = new AtomicReference<>();
        AtomicReference<AetherServer> gateway = new AtomicReference<>();
        Thread main = Thread.currentThread();
        Runnable stop = () -> {
            AetherServer server = gateway.getAndSet(null); if (server != null) { server.close(); }
            ManagedOllama child = runtime.getAndSet(null); if (child != null) { child.close(); }
        };
        Thread hook = new Thread(() -> {
            main.interrupt();
            stop.run();
            // An in-progress native launch still owns its child until it returns; let its cleanup finish.
            try { main.join(10_000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            stop.run();
        }, "aether-bundle-shutdown");
        try (DataDirectory data = DataDirectory.open(directory)) {
            Runtime.getRuntime().addShutdownHook(hook);
                Thread.ofPlatform().daemon().name("aether-console").start(() -> {
                    Scanner console = new Scanner(System.in);
                    while (console.hasNextLine()) {
                        if (console.nextLine().trim().equalsIgnoreCase("stop")) { main.interrupt(); break; }
                    }
                });
            try {
                LOG.log(System.Logger.Level.INFO, "Checking and extracting bundled runtime/model. First setup can take several minutes.");
                BundleInstaller.install(data.path(), manifest,
                        name -> AetherLauncher.class.getResourceAsStream("/bundle/files/" + name));
                Path configFile = SetupConfig.ensure(data.path(), manifest, System.getenv());
                LOG.log(System.Logger.Level.INFO, "Public Aether settings are in {0}; no access key is required", configFile);
                if (setupOnly) { LOG.log(System.Logger.Level.INFO, "Extraction complete. Start without --setup-only to serve Aether."); return; }
                ServerConfig config = ServerConfig.load(configFile, System.getenv());
                LOG.log(System.Logger.Level.INFO, "Starting bundled Ollama and registering Aether.");
                runtime.set(ManagedOllama.launch(config, manifest, data.path()));
                if (Thread.currentThread().isInterrupted()) { throw new InterruptedException(); }
                AetherServer.configureHttpLimits(config);
                AetherServer server = new AetherServer(config);
                gateway.set(server); server.start(); server.refreshHealth();
                LOG.log(System.Logger.Level.INFO, "Aether gateway ready on port {0}. Type stop or stop the hosting process to shut down.", server.port());

                while (!Thread.currentThread().isInterrupted()) {
                    ManagedOllama child = runtime.get();
                    if (child == null || !child.isAlive()) { throw new IOException("Owned Ollama process stopped. Restart the Aether launcher after checking its startup log."); }
                    Thread.sleep(1000);
                }
            } finally {
                stop.run();
                try { Runtime.getRuntime().removeShutdownHook(hook); }
                catch (IllegalStateException shutdownInProgress) { /* JVM already owns hook completion. */ }
            }
        }
    }
}
