# Global chat quickstart (beginner friendly)

This guide walks through the minimum steps to host the central relay on one dedicated server and let other Minecraft servers join the shared global chat. It focuses on safe defaults, plain commands, and the order of operations.

## What you get
- A **relay server** (central process) that forwards chat, enforces rate limits, and respects bans.
- An **embedded client** inside each Minecraft server that connects to the relay and only lets opted-in players participate.
- Admin tools for banning, timeouts, IP bans (relay host only), and seeing who is connected.

## Before you start
- Pick **one** Minecraft dedicated server to host the relay. Keep it running so everyone has a stable anchor point.
- Choose two secrets:
  - `moderationToken` – shared with admins so ban/timeout commands are accepted.
  - `clusterToken` – shared with every participating server so only trusted servers (or whitelisted IPs) can join.

## Step 1: Start the relay on the host server
1. Copy the mod jar to the host machine.
2. On that machine, start the relay once with your secrets (replace the values) on the hard-coded endpoint the clients expect (`198.51.100.77:39876`):
   ```bash
   java -Dwilderness.globalchat.token=YOUR_MODERATION_TOKEN \
        -Dwilderness.globalchat.clustertoken=YOUR_CLUSTER_TOKEN \
        -cp <path-to-mod-jar-or-classpath> \
        com.thunder.wildernessodysseyapi.globalchat.server.GlobalChatRelayServer 39876
   ```
   - `39876` is the default port; keep it in sync with the built-in client default.
   - Only this host should run the relay. Other servers will connect to it at `198.51.100.77:39876`.
3. (Optional) Allow the relay to auto-start alongside this server if you prefer the built-in sidecar:
   ```
   /globalchat allowautostart true
   /globalchat clustertoken YOUR_CLUSTER_TOKEN
   /globalchat moderationtoken YOUR_MODERATION_TOKEN
   /globalchat startserver
   ```

## Step 2: Anchor each Minecraft server to the relay
Run these commands in-game or from the server console on **every** participating server (including the host):
```
/globalchat clustertoken YOUR_CLUSTER_TOKEN
/globalchat moderationtoken YOUR_MODERATION_TOKEN
/globalchat anchorrelay
```
- `anchorrelay` pins the client to `198.51.100.77:39876`, saves the endpoint to `config/wildernessodysseyapi/global-chat.json`, and immediately connects.
- Keep `/globalchat startserver` **disabled** on all non-host servers to avoid duplicate relays.
- Use `/globalchat bind <relay-hostname-or-ip> <port>` only for short-term testing if you need a non-default endpoint.

## Step 3: Players opt in and chat
- Players must opt in once per player:
  ```
  /globalchat optin true
  ```
- Send a message:
  ```
  /globalchat send Hello everyone!
  ```
- Check your connection, ping, and downtime history:
  ```
  /globalchat status
  ```

## Step 4: Admin basics
- Ban or timeout (requires moderation token and operator permission):
  ```
  /globalchat mod ban <player> <seconds> [reason]
  /globalchat mod timeout <player> <seconds> [reason]
  /globalchat mod unban <player>
  ```
- IP bans/unbans (relay host only; permission level 3):
  ```
  /globalchat mod ipban <ip> <seconds> [reason]
  /globalchat mod ipunban <ip>
  ```
- See who is connected:
  ```
  /globalchat mod list       # names only
  /globalchat mod list withip  # includes IPs (permission level 3)
  ```

## Allowing non-Minecraft chat tools (optional)
The relay only accepts Minecraft clients by default. To let an external bot or tool talk in global chat, start the relay with a whitelist of trusted IPs:
```bash
java -Dwilderness.globalchat.token=YOUR_MODERATION_TOKEN \
     -Dwilderness.globalchat.clustertoken=YOUR_CLUSTER_TOKEN \
     -Dwilderness.globalchat.whitelist=192.0.2.10,198.51.100.8 \
     -cp <path-to-mod-jar-or-classpath> \
     com.thunder.wildernessodysseyapi.globalchat.server.GlobalChatRelayServer 39876
```
- External clients must come from a whitelisted IP **and** identify as `external` during the handshake or they will be dropped.

## Troubleshooting checklist
- Run `/globalchat status` to see connected/disconnected, ping, and downtime history.
- If a server cannot join, confirm the `clusterToken` matches the relay and the relay port is reachable.
- If moderation commands fail, ensure `/globalchat moderationtoken` is set and matches the relay’s `wilderness.globalchat.token`.
- Keep the relay on one host; disable `/globalchat startserver` elsewhere to prevent conflicts.
