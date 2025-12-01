package com.thunder.wildernessodysseyapi.globalchat;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle of the global chat client running inside the modded server JVM.
 */
public class GlobalChatManager {

    private static final Moshi MOSHI = new Moshi.Builder().build();
    private static final JsonAdapter<GlobalChatPacket> ADAPTER = MOSHI.adapter(GlobalChatPacket.class);

    private static final GlobalChatManager INSTANCE = new GlobalChatManager();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
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
        if (settings.enabled() && !settings.host().isEmpty() && settings.port() > 0) {
            connect();
        }
    }

    public void shutdown() {
        connected.set(false);
        closeSocket();
        executor.shutdownNow();
    }

    public void connect() {
        if (settings == null || settings.host().isEmpty() || settings.port() <= 0) {
            return;
        }
        executor.submit(this::runConnectionLoop);
    }

    private void runConnectionLoop() {
        closeSocket();
        try {
            socket = new Socket(settings.host(), settings.port());
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            sendHandshake();
            connected.set(true);
            lastConnectedAt = Instant.now();
            sendSystemToPlayers("Connected to global chat relay at " + settings.host() + ":" + settings.port());
            listenLoop();
        } catch (IOException e) {
            connected.set(false);
            lastDisconnectedAt = Instant.now();
            settings.recordDowntime("Connection failed: " + e.getMessage());
            settings.save(settingsFile);
        }
    }

    private void listenLoop() {
        try {
            String line;
            while (connected.get() && (line = reader.readLine()) != null) {
                GlobalChatPacket packet = ADAPTER.fromJson(line);
                if (packet == null) {
                    continue;
                }
                handlePacket(packet);
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
        if (server == null) {
            return;
        }
        if (packet.type == GlobalChatPacket.Type.CHAT || packet.type == GlobalChatPacket.Type.SYSTEM) {
            Component chat = Component.literal("[Global] " + packet.sender + ": " + packet.message);
            broadcastToOptedIn(chat);
        }
    }

    public void sendChat(String sender, String message) {
        if (!connected.get()) {
            return;
        }
        GlobalChatPacket packet = new GlobalChatPacket();
        packet.type = GlobalChatPacket.Type.CHAT;
        packet.sender = sender;
        packet.message = message;
        packet.timestamp = System.currentTimeMillis();
        writer.println(ADAPTER.toJson(packet));
    }

    public void requestStatus() {
        if (!connected.get()) {
            return;
        }
        GlobalChatPacket packet = new GlobalChatPacket();
        packet.type = GlobalChatPacket.Type.STATUS_REQUEST;
        packet.timestamp = System.currentTimeMillis();
        writer.println(ADAPTER.toJson(packet));
    }

    public void bind(String host, int port) {
        settings.setHost(host);
        settings.setPort(port);
        settings.setEnabled(true);
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
        writer.println(ADAPTER.toJson(packet));
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
        writer.println(ADAPTER.toJson(hello));
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
