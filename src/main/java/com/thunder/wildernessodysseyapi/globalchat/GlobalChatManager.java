package com.thunder.wildernessodysseyapi.globalchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of the global chat client running inside the modded server JVM.
 */
public class GlobalChatManager {

    // Serialize nulls to maintain parity with the former Moshi behavior used by the relay.
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static final GlobalChatManager INSTANCE = new GlobalChatManager();
    private final AtomicReference<ExecutorService> executor = new AtomicReference<>(Executors.newSingleThreadExecutor());
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private MinecraftServer server;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Instant lastConnectedAt;
    private Instant lastDisconnectedAt;
    private Duration lastPing = Duration.ofMillis(-1);
    private GlobalChatSettings settings;
    private Path settingsFile;

    private GlobalChatManager() {
    }

    public static GlobalChatManager getInstance() {
        return INSTANCE;
    }

    public void initialize(MinecraftServer server, Path configDir) {
        this.server = server;
        this.settingsFile = configDir.resolve("wildernessodysseyapi/global-chat.json");
        this.settings = GlobalChatSettings.load(settingsFile);
        this.settings.save(settingsFile);
        if (settings.enabled() && !settings.host().isEmpty() && settings.port() > 0) {
            connect();
        }
    }

    public void shutdown() {
        connected.set(false);
        closeSocket();
        executor.get().shutdownNow();
    }

    public void connect() {
        if (settings == null || settings.host().isEmpty() || settings.port() <= 0) {
            return;
        }
        getExecutor().submit(this::runConnectionLoop);
    }

    private ExecutorService getExecutor() {
        ExecutorService current = executor.get();
        if (current.isShutdown() || current.isTerminated()) {
            ExecutorService replacement = Executors.newSingleThreadExecutor();
            if (executor.compareAndSet(current, replacement)) {
                current = replacement;
            } else {
                current = executor.get();
            }
        }
        return current;
    }

    private void runConnectionLoop() {
        closeSocket();
        int attempt = 0;
        while (settings.enabled() && !Thread.currentThread().isInterrupted()) {
            try {
                attempt++;
                socket = new Socket();
                socket.connect(new InetSocketAddress(settings.host(), settings.port()), 4000);
                socket.setSoTimeout(4000);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                sendHandshake();
                connected.set(true);
                lastConnectedAt = Instant.now();
                sendSystemToPlayers("Connected to global chat relay at " + settings.host() + ":" + settings.port());
                listenLoop();
                attempt = 0;
            } catch (IOException e) {
                connected.set(false);
                lastDisconnectedAt = Instant.now();
                settings.recordDowntime("Connection failed: " + e.getMessage());
                settings.save(settingsFile);
                long backoffSeconds = Math.min(60, (long) Math.pow(2, Math.min(attempt, 6)));
                if (server != null && attempt == 1) {
                    sendSystemToPlayers("Global chat relay unavailable, retrying...");
                }
                try {
                    TimeUnit.SECONDS.sleep(backoffSeconds);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void listenLoop() {
        try {
            while (connected.get()) {
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    GlobalChatPacket packet = GSON.fromJson(line, GlobalChatPacket.class);
                    if (packet == null) {
                        continue;
                    }
                    handlePacket(packet);
                } catch (SocketTimeoutException e) {
                    // Allow loop to re-check connection health and continue.
                }
            }
        } catch (IOException e) {
            settings.recordDowntime("Read loop failed: " + e.getMessage());
            settings.save(settingsFile);
        } finally {
            connected.set(false);
            lastDisconnectedAt = Instant.now();
            closeSocket();
        }
    }

    private void handlePacket(GlobalChatPacket packet) {
        if (packet.type == GlobalChatPacket.Type.STATUS_RESPONSE) {
            lastPing = Duration.ofMillis(packet.pingMillis);
            return;
        }
        if (packet.type == GlobalChatPacket.Type.ANALYTICS) {
            // Analytics packets are intended for external tools; ignore them in-game.
            return;
        }
        if (server == null) {
            return;
        }
        if (packet.type == GlobalChatPacket.Type.ADMIN) {
            Component admin = Component.literal("[GlobalChat Admin] " + packet.message);
            server.sendSystemMessage(admin);
            server.getPlayerList().getPlayers().stream()
                    .filter(p -> p.hasPermissions(2))
                    .forEach(p -> p.sendSystemMessage(admin));
            return;
        }
        if (packet.type == GlobalChatPacket.Type.CHAT || packet.type == GlobalChatPacket.Type.SYSTEM) {
            String channel = packet.channel == null || packet.channel.isBlank() ? "global" : packet.channel;
            String source = packet.source == null || packet.source.isBlank() ? "minecraft" : packet.source;
            if ("discord".equalsIgnoreCase(source)) {
                Component chat = Component.literal("[").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("discord").withStyle(ChatFormatting.BLUE))
                        .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(packet.sender).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(": " + packet.message).withStyle(ChatFormatting.WHITE));
                broadcastToOptedIn(chat);
                return;
            }
            Component chat = Component.literal("[" + channel + "] " + packet.sender + ": " + packet.message);
            broadcastToOptedIn(chat);
        }
    }

    public void sendChat(String sender, String channel, String message) {
        if (!connected.get()) {
            return;
        }
        GlobalChatPacket packet = new GlobalChatPacket();
        packet.type = GlobalChatPacket.Type.CHAT;
        packet.sender = sender;
        packet.message = message;
        packet.channel = (channel == null || channel.isBlank()) ? "global" : channel.trim().toLowerCase();
        packet.source = "minecraft";
        packet.timestamp = System.currentTimeMillis();
        writer.println(GSON.toJson(packet));
    }

    public void sendAnalyticsSnapshot(AnalyticsSnapshot snapshot, AnalyticsSyncView syncView) {
        if (!connected.get()) {
            return;
        }
        GlobalChatPacket packet = new GlobalChatPacket();
        packet.type = GlobalChatPacket.Type.ANALYTICS;
        packet.sender = server != null ? server.getMotd() : "minecraft";
        packet.timestamp = System.currentTimeMillis();
        packet.analytics = snapshot;
        packet.analyticsSync = syncView;
        writer.println(GSON.toJson(packet));
    }

    public void requestStatus() {
        if (!connected.get()) {
            return;
        }
        GlobalChatPacket packet = new GlobalChatPacket();
        packet.type = GlobalChatPacket.Type.STATUS_REQUEST;
        packet.timestamp = System.currentTimeMillis();
        writer.println(GSON.toJson(packet));
    }

    public void bind(String host, int port) {
        settings.setHost(host);
        settings.setPort(port);
        settings.setEnabled(true);
        settings.save(settingsFile);
        connect();
    }

    public void anchorToDefaultRelay() {
        settings.anchorToDefaultRelay();
        settings.save(settingsFile);
        connect();
    }

    public void setModerationToken(String token) {
        if (settings == null) {
            return;
        }
        settings.setModerationToken(token);
        settings.save(settingsFile);
    }

    public void setClusterToken(String token) {
        if (settings == null) {
            return;
        }
        settings.setClusterToken(token);
        settings.save(settingsFile);
    }

    public void setAllowServerAutostart(boolean enabled) {
        if (settings == null) {
            return;
        }
        settings.setAllowServerAutostart(enabled);
        settings.save(settingsFile);
    }

    public void sendModerationAction(String action, String target, long durationSeconds, boolean includeIp,
                                     String role, String ip, String reason) {
        if (!connected.get() || settings == null || settings.moderationToken().isEmpty()) {
            return;
        }
        GlobalChatPacket packet = new GlobalChatPacket();
        packet.type = GlobalChatPacket.Type.MOD_ACTION;
        packet.moderationAction = action;
        packet.target = target;
        packet.durationSeconds = durationSeconds;
        packet.includeIp = includeIp;
        packet.role = role;
        packet.ip = ip;
        packet.reason = reason;
        packet.moderationToken = settings.moderationToken();
        packet.timestamp = System.currentTimeMillis();
        writer.println(GSON.toJson(packet));
    }

    private void sendHandshake() {
        if (writer == null) {
            return;
        }
        GlobalChatPacket hello = new GlobalChatPacket();
        hello.type = GlobalChatPacket.Type.HELLO;
        hello.clientType = "minecraft";
        hello.clusterToken = settings != null ? settings.clusterToken() : "";
        hello.sender = server != null ? server.getMotd() : "minecraft";
        hello.timestamp = System.currentTimeMillis();
        writer.println(GSON.toJson(hello));
    }

    public boolean isConnected() {
        return connected.get();
    }

    public Optional<Duration> lastPing() {
        return lastPing.isNegative() ? Optional.empty() : Optional.of(lastPing);
    }

    public Optional<Instant> lastConnectedAt() {
        return Optional.ofNullable(lastConnectedAt);
    }

    public Optional<Instant> lastDisconnectedAt() {
        return Optional.ofNullable(lastDisconnectedAt);
    }

    public GlobalChatSettings getSettings() {
        return settings;
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public void sendSystemToPlayers(String message) {
        if (server != null) {
            Component chat = Component.literal("[GlobalChat] " + message);
            broadcastToOptedIn(chat);
        }
    }

    private void broadcastToOptedIn(Component message) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (GlobalChatOptIn.isOptedIn(player)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
