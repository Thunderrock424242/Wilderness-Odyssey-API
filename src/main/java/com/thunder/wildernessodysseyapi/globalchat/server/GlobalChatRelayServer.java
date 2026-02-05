package com.thunder.wildernessodysseyapi.globalchat.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thunder.wildernessodysseyapi.globalchat.AnalyticsSnapshot;
import com.thunder.wildernessodysseyapi.globalchat.AnalyticsSyncView;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatPacket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.net.InetSocketAddress;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

/**
 * Lightweight relay server for the global chat network. It is intentionally small
 * so it can be launched as an external JVM process alongside a Minecraft server.
 */
public class GlobalChatRelayServer {

    // Serialize nulls to mirror the previous Moshi-based payloads and keep the wire format stable.
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int MAX_MESSAGES_PER_MINUTE = 20;
    private static final int MAX_MESSAGES_PER_IP_PER_MINUTE = 90;

    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<Socket, ClientState> clients = new ConcurrentHashMap<>();
    private final Map<String, BanEntry> bansByName = new ConcurrentHashMap<>();
    private final Map<String, BanEntry> bansByIp = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QuotaWindow> ipQuotas = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final String moderationToken;
    private final String clusterToken;
    private final Set<String> externalWhitelist = new HashSet<>();
    private final Path analyticsDir = Path.of("analytics");

    public GlobalChatRelayServer(int port, String moderationToken, String clusterToken) throws IOException {
        if (moderationToken == null || moderationToken.isBlank() || "changeme".equals(moderationToken)) {
            throw new IllegalArgumentException("A non-default moderation token is required");
        }
        this.serverSocket = new ServerSocket(port);
        this.moderationToken = moderationToken;
        this.clusterToken = clusterToken == null ? "" : clusterToken;
        loadWhitelist();
        createDirectoriesSecure(analyticsDir);
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
            socket.setSoTimeout(30_000);
            String line;
            while ((line = reader.readLine()) != null) {
                GlobalChatPacket packet;
                try {
                    packet = GSON.fromJson(line, GlobalChatPacket.class);
                } catch (Exception malformedPacketError) {
                    ClientState state = clients.get(socket);
                    if (state != null) {
                        sendSystemMessage(state, "Malformed packet; closing connection.");
                    }
                    closeQuietly(socket);
                    return;
                }
                if (packet == null) {
                    continue;
                }
                dispatch(packet, socket, writer);
            }
        } catch (SocketTimeoutException idleTimeout) {
            // Idle client timed out waiting for a packet; close connection quietly.
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
        if (!state.authenticated && packet.type != GlobalChatPacket.Type.HELLO) {
            sendSystemMessage(state, "Handshake required before communication.");
            closeQuietly(socket);
            return;
        }
        switch (packet.type) {
            case HELLO -> handleHello(packet, socket, state);
            case CHAT -> handleChat(packet, state);
            case STATUS_REQUEST -> sendStatus(writer, state, packet);
            case MOD_ACTION -> handleModeration(packet, state);
            case ANALYTICS -> handleAnalytics(packet, state);
            default -> {
            }
        }
    }

    private void handleHello(GlobalChatPacket packet, Socket socket, ClientState state) {
        if (clusterToken != null && !clusterToken.isEmpty()) {
            String provided = packet.clusterToken == null ? "" : packet.clusterToken;
            if (!clusterToken.equals(provided)) {
                sendSystemMessage(state, "Cluster token mismatch; connection rejected.");
                closeQuietly(socket);
                return;
            }
        }
        String type = packet.clientType == null ? "" : packet.clientType.toLowerCase();
        if ("minecraft".equals(type)) {
            state.authenticated = true;
            state.role = "server";
            if (packet.sender != null && !packet.sender.isBlank()) {
                state.overrideServerId(packet.sender.trim());
            }
            return;
        }
        if ("external".equals(type)) {
            if (externalWhitelist.contains(state.remoteAddress)) {
                state.authenticated = true;
                state.role = "external";
                return;
            }
            sendSystemMessage(state, "External client is not on the trusted IP whitelist.");
            closeQuietly(socket);
            return;
        }
        sendSystemMessage(state, "Unknown client type; connection rejected.");
        closeQuietly(socket);
    }

    private void sendStatus(PrintWriter writer, ClientState state, GlobalChatPacket request) {
        GlobalChatPacket status = new GlobalChatPacket();
        status.type = GlobalChatPacket.Type.STATUS_RESPONSE;
        status.serverId = state.serverId;
        status.sender = "relay";
        status.timestamp = System.currentTimeMillis();
        status.pingMillis = System.currentTimeMillis() - request.timestamp;
        writer.println(Objects.requireNonNull(GSON.toJson(status)));
    }

    private void loadWhitelist() {
        String raw = System.getProperty("wilderness.globalchat.whitelist", "");
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                externalWhitelist.add(trimmed);
            }
        }
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
        if (!tryConsumeIpQuota(state.remoteAddress)) {
            sendSystemMessage(state, "IP rate limit exceeded; temporarily throttled.");
            return;
        }
        broadcast(packet, state);
    }

    private boolean tryConsumeIpQuota(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank() || "unknown".equals(remoteAddress)) {
            return true;
        }
        QuotaWindow quota = ipQuotas.computeIfAbsent(remoteAddress, ignored -> new QuotaWindow());
        return quota.tryConsume(MAX_MESSAGES_PER_IP_PER_MINUTE);
    }

    private void broadcast(GlobalChatPacket packet, ClientState origin) {
        packet.serverId = origin.serverId;
        packet.timestamp = System.currentTimeMillis();
        String json = GSON.toJson(packet);
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
            sendAdminMessage(state, "Moderation token invalid; action rejected.");
            return;
        }

        switch (packet.moderationAction == null ? "" : packet.moderationAction.toLowerCase()) {
            case "mute" -> {
                clients.values().stream()
                        .filter(client -> packet.target != null && packet.target.equals(client.serverId))
                        .forEach(ClientState::mute);
                sendAdminMessage(state, "Server " + packet.target + " muted by moderator.");
            }
            case "unmute" -> {
                clients.values().stream()
                        .filter(client -> packet.target != null && packet.target.equals(client.serverId))
                        .forEach(ClientState::unmute);
                sendAdminMessage(state, "Server " + packet.target + " unmuted by moderator.");
            }
            case "ban" -> {
                bansByName.put(packet.target, BanEntry.create(packet.durationSeconds, packet.reason));
                sendAdminMessage(state, "User " + packet.target + " banned from global chat." + formatDuration(packet.durationSeconds));
            }
            case "unban" -> {
                bansByName.remove(packet.target);
                bansByIp.remove(packet.target);
                sendAdminMessage(state, "Removed ban for target " + packet.target);
            }
            case "timeout" -> {
                bansByName.put(packet.target, BanEntry.create(packet.durationSeconds, packet.reason));
                sendAdminMessage(state, "Timed out " + packet.target + formatDuration(packet.durationSeconds));
            }
            case "list" -> sendConnectionList(state, packet.includeIp);
            case "ipban" -> {
                if (packet.ip != null) {
                    bansByIp.put(packet.ip, BanEntry.create(packet.durationSeconds, packet.reason));
                    sendAdminMessage(state, "IP banned: " + packet.ip + formatDuration(packet.durationSeconds));
                }
            }
            case "ipunban" -> {
                if (packet.ip != null) {
                    bansByIp.remove(packet.ip);
                    sendAdminMessage(state, "IP ban cleared: " + packet.ip);
                }
            }
            case "role" -> {
                if (!state.fromRelayHost) {
                    sendAdminMessage(state, "Role assignments can only be issued from the relay host.");
                    return;
                }
                if (packet.target != null && packet.role != null) {
                    clients.values().stream()
                            .filter(client -> packet.target.equals(client.serverId))
                            .findFirst()
                            .ifPresent(client -> client.role = packet.role);
                    sendAdminMessage(state, "Updated role for " + packet.target + " to " + packet.role);
                }
            }
            default -> sendAdminMessage(state, "Unknown moderation action: " + packet.moderationAction);
        }
    }

    private void handleAnalytics(GlobalChatPacket packet, ClientState state) {
        if (!"server".equals(state.role)) {
            sendSystemMessage(state, "Analytics packets must originate from a server client.");
            return;
        }
        persistAnalytics(packet, state);
    }

    private void persistAnalytics(GlobalChatPacket packet, ClientState state) {
        if (packet.analytics == null || packet.analytics.players == null) {
            return;
        }
        packet.analytics.players.forEach(player -> {
            PlayerAnalyticsRecord record = new PlayerAnalyticsRecord();
            record.serverId = state.serverId;
            record.timestampMillis = packet.analytics.timestampMillis;
            record.player = player;
            record.playerCount = packet.analytics.playerCount;
            record.maxPlayers = packet.analytics.maxPlayers;
            record.usedMemoryMb = packet.analytics.usedMemoryMb;
            record.totalMemoryMb = packet.analytics.totalMemoryMb;
            record.peakMemoryMb = packet.analytics.peakMemoryMb;
            record.recommendedMemoryMb = packet.analytics.recommendedMemoryMb;
            record.worstTickMillis = packet.analytics.worstTickMillis;
            record.cpuLoad = packet.analytics.cpuLoad;
            record.overloaded = packet.analytics.overloaded;
            record.overloadedReason = packet.analytics.overloadedReason;
            if (packet.analyticsSync != null) {
                record.status = packet.analyticsSync.status;
                record.joinedPlayerIds = packet.analyticsSync.joinedPlayerIds;
                record.leftPlayerIds = packet.analyticsSync.leftPlayerIds;
            }
            try {
                Path serverDir = analyticsDir.resolve(state.serverId);
                createDirectoriesSecure(serverDir);
                Path file = serverDir.resolve(player.uuid + ".json");
                Files.writeString(file, GSON.toJson(record));
            } catch (IOException e) {
                System.err.println("[GlobalChatRelayServer] Failed to persist analytics for player " + player.uuid
                        + ": " + e.getMessage());
            }
        });
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
        sendAdminMessage(state, builder.toString());
    }

    private boolean isBanned(String sender, String remoteAddress) {
        BanEntry nameBan = bansByName.get(sender);
        BanEntry ipBan = bansByIp.get(remoteAddress);
        return (nameBan != null && nameBan.isActive()) || (ipBan != null && ipBan.isActive());
    }

    private void cleanupExpiredBans() {
        bansByName.entrySet().removeIf(entry -> !entry.getValue().isActive());
        bansByIp.entrySet().removeIf(entry -> !entry.getValue().isActive());
        ipQuotas.entrySet().removeIf(entry -> entry.getValue().isIdle());
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
        String json = GSON.toJson(system);
        clients.values().forEach(client -> client.writer.println(json));
    }

    private void sendSystemMessage(ClientState state, String message) {
        GlobalChatPacket system = new GlobalChatPacket();
        system.type = GlobalChatPacket.Type.SYSTEM;
        system.sender = "relay";
        system.message = message;
        system.timestamp = System.currentTimeMillis();
        state.writer.println(GSON.toJson(system));
    }

    private void sendAdminMessage(ClientState state, String message) {
        GlobalChatPacket admin = new GlobalChatPacket();
        admin.type = GlobalChatPacket.Type.ADMIN;
        admin.sender = "relay";
        admin.message = message;
        admin.timestamp = System.currentTimeMillis();
        state.writer.println(GSON.toJson(admin));
    }

    public void stop() {
        running.set(false);
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        executor.shutdownNow();
    }

    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
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

    private static class PlayerAnalyticsRecord {
        private long timestampMillis;
        private String serverId;
        private AnalyticsSnapshot.PlayerStats player;
        private int playerCount;
        private int maxPlayers;
        private long usedMemoryMb;
        private long totalMemoryMb;
        private long peakMemoryMb;
        private int recommendedMemoryMb;
        private long worstTickMillis;
        private double cpuLoad;
        private boolean overloaded;
        private String overloadedReason;
        private AnalyticsSyncView.HealthStatus status;
        private List<String> joinedPlayerIds;
        private List<String> leftPlayerIds;
    }

    private static class ClientState {
        private final Socket socket;
        private final PrintWriter writer;
        private String serverId = UUID.randomUUID().toString();
        private Instant lastReset = Instant.now();
        private int messagesSent = 0;
        private boolean muted = false;
        private final String remoteAddress;
        private String role = "user";
        private boolean authenticated = false;
        private final boolean fromRelayHost;

        ClientState(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.remoteAddress = socket.getRemoteSocketAddress() instanceof InetSocketAddress inet
                    ? inet.getAddress().getHostAddress()
                    : "unknown";
            this.fromRelayHost = socket.getInetAddress().isLoopbackAddress();
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

        void overrideServerId(String providedId) {
            if (providedId != null && !providedId.isBlank()) {
                this.serverId = providedId;
            }
        }
    }

    private static class QuotaWindow {
        private Instant windowStart = Instant.now();
        private int consumed = 0;

        synchronized boolean tryConsume(int limitPerMinute) {
            Instant now = Instant.now();
            if (Duration.between(windowStart, now).getSeconds() >= 60) {
                consumed = 0;
                windowStart = now;
            }
            if (consumed >= limitPerMinute) {
                return false;
            }
            consumed++;
            return true;
        }

        synchronized boolean isIdle() {
            return Duration.between(windowStart, Instant.now()).toMinutes() >= 5;
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 39876;
        String token = System.getProperty("wilderness.globalchat.token", "changeme");
        String clusterToken = System.getProperty("wilderness.globalchat.clustertoken", "");
        GlobalChatRelayServer server = new GlobalChatRelayServer(port, token, clusterToken);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    private void createDirectoriesSecure(Path dir) throws IOException {
        Files.createDirectories(dir);
        try {
            Files.setPosixFilePermissions(dir, EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems (e.g., Windows) will ignore permission tightening.
        }
    }
}
