package com.thunder.wildernessodysseyapi.globalchat;

/**
 * Simple line-delimited message exchanged between clients and the relay server.
 */
public class GlobalChatPacket {
    public enum Type {
        HELLO,
        CHAT,
        STATUS_REQUEST,
        STATUS_RESPONSE,
        MOD_ACTION,
        ADMIN,
        SYSTEM
    }

    public Type type;
    public String sender;
    public String message;
    public String serverId;
    public long timestamp;
    public String clientType;
    public String clusterToken;
    public String moderationAction;
    public String target;
    public String moderationToken;
    public long durationSeconds;
    public String reason;
    public boolean includeIp;
    public String ip;
    public String role;
    public boolean muted;
    public long pingMillis;
}
