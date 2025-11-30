package com.thunder.wildernessodysseyapi.globalchat.server;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatPacket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.net.InetSocketAddress;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight relay server for the global chat network. It is intentionally small
 * so it can be launched as an external JVM process alongside a Minecraft server.
 */
public class GlobalChatRelayServer {

    private static final Moshi MOSHI = new Moshi.Builder().build();
    private static final JsonAdapter<GlobalChatPacket> ADAPTER = MOSHI.adapter(GlobalChatPacket.class);
    private static final int MAX_MESSAGES_PER_MINUTE = 20;

    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<Socket, ClientState> clients = new ConcurrentHashMap<>();
    private final Map<String, BanEntry> bansByName = new ConcurrentHashMap<>();
    private final Map<String, BanEntry> bansByIp = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final String moderationToken;

    public GlobalChatRelayServer(int port, String moderationToken) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.moderationToken = moderationToken;
    }

    public void start() {
        System.out.println("[GlobalChatRelayServer] Listening on " + serverSocket.getLocalPort());
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                clients.put(socket, new ClientState(socket));
                executor.submit(() -> handleClient(socket));
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("[GlobalChatRelayServer] Accept loop error: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while ((line = reader.readLine()) != null) {
                GlobalChatPacket packet = ADAPTER.fromJson(line);
                if (packet == null) {
                    continue;
                }
                dispatch(packet, socket, writer);
            }
        } catch (IOException e) {
            System.err.println("[GlobalChatRelayServer] Client connection failed: " + e.getMessage());
        } finally {
            clients.remove(socket);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void dispatch(GlobalChatPacket packet, Socket socket, PrintWriter writer) {
        ClientState state = clients.get(socket);
        if (state == null) {
            return;
        }
        switch (packet.type) {
            case CHAT -> handleChat(packet, state);
            case STATUS_REQUEST -> sendStatus(writer, state, packet);
            case MOD_ACTION -> handleModeration(packet, state);
            default -> {
            }
        }
    }

    private void sendStatus(PrintWriter writer, ClientState state, GlobalChatPacket request) {
        GlobalChatPacket status = new GlobalChatPacket();
        status.type = GlobalChatPacket.Type.STATUS_RESPONSE;
        status.serverId = state.serverId;
        status.sender = "relay";
        status.timestamp = System.currentTimeMillis();
        status.pingMillis = System.currentTimeMillis() - request.timestamp;
        writer.println(Objects.requireNonNull(ADAPTER.toJson(status)));
    }

    private void handleChat(GlobalChatPacket packet, ClientState state) {
        cleanupExpiredBans();
        if (state.isMuted()) {
            return;
        }
        if (isBanned(packet.sender, state.remoteAddress)) {
            sendSystemMessage(state, "You are banned from the global chat network.");
            return;
        }
        if (!state.tryConsumeQuota()) {
            sendSystemMessage(state, "Rate limit exceeded; please slow down.");
            return;
        }
        broadcast(packet, state);
    }

    private void broadcast(GlobalChatPacket packet, ClientState origin) {
        packet.serverId = origin.serverId;
        packet.timestamp = System.currentTimeMillis();
        String json = ADAPTER.toJson(packet);
        for (Map.Entry<Socket, ClientState> entry : clients.entrySet()) {
            try {
                PrintWriter writer = entry.getValue().writer;
                writer.println(json);
            } catch (Exception ignored) {
            }
        }
    }

    private void handleModeration(GlobalChatPacket packet, ClientState state) {
        if (packet.moderationToken == null || !packet.moderationToken.equals(moderationToken)) {
            sendSystemMessage(state, "Moderation token invalid; action rejected.");
            return;
        }

        switch (packet.moderationAction == null ? "" : packet.moderationAction.toLowerCase()) {
            case "mute" -> {
                clients.values().stream()
                        .filter(client -> packet.target != null && packet.target.equals(client.serverId))
                        .forEach(ClientState::mute);
                broadcastSystem("Server " + packet.target + " muted by moderator.");
            }
            case "unmute" -> {
                clients.values().stream()
                        .filter(client -> packet.target != null && packet.target.equals(client.serverId))
                        .forEach(ClientState::unmute);
                broadcastSystem("Server " + packet.target + " unmuted by moderator.");
            }
            case "ban" -> {
                bansByName.put(packet.target, BanEntry.create(packet.durationSeconds, packet.reason));
                broadcastSystem("User " + packet.target + " banned from global chat." + formatDuration(packet.durationSeconds));
            }
            case "unban" -> {
                bansByName.remove(packet.target);
                bansByIp.remove(packet.target);
                sendSystemMessage(state, "Removed ban for target " + packet.target);
            }
            case "timeout" -> {
                bansByName.put(packet.target, BanEntry.create(packet.durationSeconds, packet.reason));
                sendSystemMessage(state, "Timed out " + packet.target + formatDuration(packet.durationSeconds));
            }
            case "list" -> sendConnectionList(state, packet.includeIp);
            case "ipban" -> {
                if (packet.ip != null) {
                    bansByIp.put(packet.ip, BanEntry.create(packet.durationSeconds, packet.reason));
                    sendSystemMessage(state, "IP banned: " + packet.ip + formatDuration(packet.durationSeconds));
                }
            }
            case "ipunban" -> {
                if (packet.ip != null) {
                    bansByIp.remove(packet.ip);
                    sendSystemMessage(state, "IP ban cleared: " + packet.ip);
                }
            }
            case "role" -> {
                if (packet.target != null && packet.role != null) {
                    clients.values().stream()
                            .filter(client -> packet.target.equals(client.serverId))
                            .findFirst()
                            .ifPresent(client -> client.role = packet.role);
                    sendSystemMessage(state, "Updated role for " + packet.target + " to " + packet.role);
                }
            }
            default -> sendSystemMessage(state, "Unknown moderation action: " + packet.moderationAction);
        }
    }

    private void sendConnectionList(ClientState state, boolean includeIp) {
        StringBuilder builder = new StringBuilder("Connected clients: ");
        clients.values().forEach(client -> {
            builder.append("[")
                    .append(client.serverId)
                    .append(" role=")
                    .append(client.role)
                    .append(includeIp ? " ip=" + client.remoteAddress : "")
                    .append("] ");
        });
        sendSystemMessage(state, builder.toString());
    }

    private boolean isBanned(String sender, String remoteAddress) {
        BanEntry nameBan = bansByName.get(sender);
        BanEntry ipBan = bansByIp.get(remoteAddress);
        return (nameBan != null && nameBan.isActive()) || (ipBan != null && ipBan.isActive());
    }

    private void cleanupExpiredBans() {
        bansByName.entrySet().removeIf(entry -> !entry.getValue().isActive());
        bansByIp.entrySet().removeIf(entry -> !entry.getValue().isActive());
    }

    private String formatDuration(long durationSeconds) {
        if (durationSeconds <= 0) {
            return " (permanent)";
        }
        return " for " + Duration.of(durationSeconds, ChronoUnit.SECONDS).toMinutes() + "m";
    }

    private void broadcastSystem(String message) {
        GlobalChatPacket system = new GlobalChatPacket();
        system.type = GlobalChatPacket.Type.SYSTEM;
        system.sender = "relay";
        system.message = message;
        system.timestamp = System.currentTimeMillis();
        String json = ADAPTER.toJson(system);
        clients.values().forEach(client -> client.writer.println(json));
    }

    private void sendSystemMessage(ClientState state, String message) {
        GlobalChatPacket system = new GlobalChatPacket();
        system.type = GlobalChatPacket.Type.SYSTEM;
        system.sender = "relay";
        system.message = message;
        system.timestamp = System.currentTimeMillis();
        state.writer.println(ADAPTER.toJson(system));
    }

    public void stop() {
        running.set(false);
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        executor.shutdownNow();
    }

    private static class BanEntry {
        private final Instant expiresAt;
        private final String reason;

        private BanEntry(Instant expiresAt, String reason) {
            this.expiresAt = expiresAt;
            this.reason = reason;
        }

        static BanEntry create(long durationSeconds, String reason) {
            Instant expiry = durationSeconds > 0 ? Instant.now().plusSeconds(durationSeconds) : null;
            return new BanEntry(expiry, reason);
        }

        boolean isActive() {
            return expiresAt == null || Instant.now().isBefore(expiresAt);
        }

        Optional<String> reason() {
            return Optional.ofNullable(reason);
        }
    }

    private static class ClientState {
        private final Socket socket;
        private final PrintWriter writer;
        private final String serverId = UUID.randomUUID().toString();
        private Instant lastReset = Instant.now();
        private int messagesSent = 0;
        private boolean muted = false;
        private final String remoteAddress;
        private String role = "user";

        ClientState(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.remoteAddress = socket.getRemoteSocketAddress() instanceof InetSocketAddress inet
                    ? inet.getAddress().getHostAddress()
                    : "unknown";
        }

        boolean tryConsumeQuota() {
            Instant now = Instant.now();
            if (Duration.between(lastReset, now).getSeconds() >= 60) {
                messagesSent = 0;
                lastReset = now;
            }
            if (messagesSent >= MAX_MESSAGES_PER_MINUTE) {
                return false;
            }
            messagesSent++;
            return true;
        }

        boolean isMuted() {
            return muted;
        }

        void mute() {
            muted = true;
        }

        void unmute() {
            muted = false;
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 39876;
        String token = System.getProperty("wilderness.globalchat.token", "changeme");
        GlobalChatRelayServer server = new GlobalChatRelayServer(port, token);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
