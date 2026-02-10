package com.thunder.wildernessodysseyapi.globalchat.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatPacket;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Minimal Discord bridge for selected global chat channels.
 */
public class DiscordBridgeService {

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String botToken;
    private final long pollIntervalSeconds;
    private final Consumer<GlobalChatPacket> inboundConsumer;
    private final Map<String, ChannelBridge> channels = new HashMap<>();
    private final Map<String, String> lastSeenMessageIds = new HashMap<>();
    private ScheduledExecutorService scheduler;
    private String botUserId;

    public DiscordBridgeService(String botToken,
                                long pollIntervalSeconds,
                                Map<String, ChannelBridge> channels,
                                Consumer<GlobalChatPacket> inboundConsumer) {
        this.botToken = botToken == null ? "" : botToken.trim();
        this.pollIntervalSeconds = Math.max(2, pollIntervalSeconds);
        this.inboundConsumer = inboundConsumer;
        this.channels.putAll(channels);
    }

    public static DiscordBridgeService fromSystemProperties(Consumer<GlobalChatPacket> inboundConsumer) {
        String botToken = System.getProperty("wilderness.globalchat.discord.botToken", "");
        long interval = Long.parseLong(System.getProperty("wilderness.globalchat.discord.pollSeconds", "4"));
        Map<String, ChannelBridge> channels = new HashMap<>();
        for (String channel : List.of("help", "staff")) {
            String webhook = System.getProperty("wilderness.globalchat.discord.channels." + channel + ".webhook", "").trim();
            String channelId = System.getProperty("wilderness.globalchat.discord.channels." + channel + ".channelId", "").trim();
            if (!webhook.isEmpty() || !channelId.isEmpty()) {
                channels.put(channel, new ChannelBridge(channel, webhook, channelId));
            }
        }
        return new DiscordBridgeService(botToken, interval, channels, inboundConsumer);
    }

    public boolean isEnabled() {
        return !channels.isEmpty() && (!botToken.isBlank() || channels.values().stream().anyMatch(c -> !c.webhookUrl.isBlank()));
    }

    public void start() {
        if (!isEnabled()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        if (!botToken.isBlank()) {
            botUserId = fetchBotUserId();
            scheduler.scheduleWithFixedDelay(this::pollDiscord, 2, pollIntervalSeconds, TimeUnit.SECONDS);
        }
        System.out.println("[GlobalChatRelayServer] Discord bridge enabled for channels " + channels.keySet());
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public void relayMinecraftToDiscord(GlobalChatPacket packet) {
        if (packet == null || packet.message == null) {
            return;
        }
        String channel = normalizeChannel(packet.channel);
        ChannelBridge bridge = channels.get(channel);
        if (bridge == null || bridge.webhookUrl.isBlank()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("username", packet.sender == null || packet.sender.isBlank() ? "minecraft" : packet.sender);
        payload.addProperty("content", packet.message);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bridge.webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException ignored) {
        }
    }

    private void pollDiscord() {
        for (ChannelBridge bridge : channels.values()) {
            if (bridge.channelId.isBlank()) {
                continue;
            }
            try {
                List<DiscordMessage> messages = fetchRecentMessages(bridge.channelId, 15);
                messages.sort(Comparator.comparing(m -> m.id));
                String lastSeen = lastSeenMessageIds.get(bridge.channel);
                for (DiscordMessage msg : messages) {
                    if (lastSeen != null && compareSnowflake(msg.id, lastSeen) <= 0) {
                        continue;
                    }
                    if (msg.authorBot && Objects.equals(msg.authorId, botUserId)) {
                        continue;
                    }
                    GlobalChatPacket packet = new GlobalChatPacket();
                    packet.type = GlobalChatPacket.Type.CHAT;
                    packet.channel = bridge.channel;
                    packet.source = "discord";
                    packet.sender = msg.authorName;
                    packet.message = msg.content;
                    packet.timestamp = System.currentTimeMillis();
                    packet.messageId = "discord:" + msg.id;
                    inboundConsumer.accept(packet);
                    lastSeenMessageIds.put(bridge.channel, msg.id);
                }
                if (!messages.isEmpty() && !lastSeenMessageIds.containsKey(bridge.channel)) {
                    lastSeenMessageIds.put(bridge.channel, messages.get(messages.size() - 1).id);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private List<DiscordMessage> fetchRecentMessages(String channelId, int limit) throws IOException, InterruptedException {
        String uri = "https://discord.com/api/v10/channels/" + URLEncoder.encode(channelId, StandardCharsets.UTF_8)
                + "/messages?limit=" + Math.max(1, Math.min(limit, 50));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", "Bot " + botToken)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return List.of();
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!(parsed instanceof JsonArray arr)) {
            return List.of();
        }
        List<DiscordMessage> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!(el instanceof JsonObject obj)) {
                continue;
            }
            String id = getString(obj, "id");
            String content = getString(obj, "content");
            JsonObject author = obj.getAsJsonObject("author");
            String authorName = author == null ? "discord" : getString(author, "global_name");
            if (authorName == null || authorName.isBlank()) {
                authorName = author == null ? "discord" : getString(author, "username");
            }
            String authorId = author == null ? "" : getString(author, "id");
            boolean authorBot = author != null && author.has("bot") && author.get("bot").getAsBoolean();
            if (id == null || id.isBlank() || content == null || content.isBlank()) {
                continue;
            }
            out.add(new DiscordMessage(id, content, authorName == null ? "discord" : authorName, authorId, authorBot));
        }
        return out;
    }

    private String fetchBotUserId() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/users/@me"))
                    .header("Authorization", "Bot " + botToken)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            return getString(obj, "id");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "global";
        }
        return channel.toLowerCase(Locale.ROOT);
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private static int compareSnowflake(String a, String b) {
        if (a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return a.compareTo(b);
    }

    public static class ChannelBridge {
        private final String channel;
        private final String webhookUrl;
        private final String channelId;

        public ChannelBridge(String channel, String webhookUrl, String channelId) {
            this.channel = channel;
            this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
            this.channelId = channelId == null ? "" : channelId;
        }
    }

    private record DiscordMessage(String id, String content, String authorName, String authorId, boolean authorBot) {
    }
}
