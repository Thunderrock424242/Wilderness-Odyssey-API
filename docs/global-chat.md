# Global chat setup and operations

The global chat system lets multiple Wilderness Odyssey servers share messages through a lightweight relay process. This guide covers how to anchor the relay to a specific dedicated server, connect other servers, and use moderation/opt-in controls.

## Components
- **Relay server**: External JVM process that accepts TCP connections, relays chat, enforces rate limits/bans, and handles admin actions.
- **Embedded client**: Runs inside each Minecraft server JVM after you bind it to a relay host and port. It keeps a downtime history, tracks ping, and fans out messages only to opted-in players.
- **Commands**: Registered under `/globalchat` for binding, status checks, opt-in, messaging, relay lifecycle, and moderation.

## Anchoring the relay to a dedicated host
1. Pick the Minecraft server that should host the central relay. On that machine, launch the relay directly, pinned to the hard-coded endpoint the clients expect (`198.51.100.77:39876`):
   ```bash
   java -Dwilderness.globalchat.token=<moderationToken> \
        -Dwilderness.globalchat.clustertoken=<clusterSecret> \
        -cp <path-to-mod-jar-or-classpath> \
        com.thunder.wildernessodysseyapi.globalchat.server.GlobalChatRelayServer <port>
   ```
   - Replace `<port>` with the TCP port you want the relay to listen on (defaults to `39876` if omitted) and match the hard-coded client port.
   - The `wilderness.globalchat.token` system property must match the moderation token you configure on each participating server.
   - (Optional but recommended) Set `wilderness.globalchat.clustertoken` to a shared secret that every server must present during the handshake. Connections without the matching token are dropped before the relay will process messages.
2. On every other server, **keep relay autostart disabled** (the default) so the `startserver` command is blocked and the relay stays anchored to your chosen host.
3. If you explicitly want a server to be allowed to spawn the relay sidecar, run `/globalchat allowautostart true` as an operator on that server.

## Connecting a server to the relay
1. As an operator, anchor the server to the shared relay endpoint:
   ```
   /globalchat anchorrelay
   ```
   This pins the client to `198.51.100.77:39876`, persists to `config/wildernessodysseyapi/global-chat.json`, and immediately attempts a connection.
   If you need to override the endpoint for testing, `/globalchat bind <host> <port>` is still available but production clusters should stick to the anchored IP/port.
2. Verify connectivity and latency:
   ```
   /globalchat status
   ```
   The status call reports connected/disconnected, last ping, and downtime history for troubleshooting.

## Player opt-in and messaging
- Players must opt in before sending or receiving global chat:
  ```
  /globalchat optin true
  ```
- Send a message once opted in:
  ```
  /globalchat send <message>
  ```
- Use `/globalchat status` to see your opt-in flag along with relay connectivity.

## Moderation and roles
Moderation commands require a valid token (set via `/globalchat moderationtoken <token>`) and operator permission level 2 unless noted.

- Bans and timeouts:
  - `/globalchat mod ban <playerName> <durationSeconds> [reason]`
  - `/globalchat mod timeout <playerName> <durationSeconds> [reason]`
  - `/globalchat mod unban <playerName>`
- IP-specific controls (permission level 3):
  - `/globalchat mod ipban <ip> <durationSeconds> [reason]`
  - `/globalchat mod ipunban <ip>`
- Connection visibility:
  - `/globalchat mod list` (omit IPs) or `/globalchat mod list withip` (includes IPs; permission level 3)
- Roles:
- `/globalchat mod role <serverId> <role>` to tag a connected server with a custom role label. Role assignments are only accepted from connections originating on the relay host itself, keeping admin registration centralized on the main server.

## Cluster secret for trusted membership
- Use `/globalchat clustertoken <token>` on every server to persist the shared secret to `config/wildernessodysseyapi/global-chat.json`.
- Restart or run `/globalchat startserver` on the relay host after updating the token so the sidecar process receives the new value via JVM args.
- During the handshake, the relay rejects clients whose `HELLO` packets lack the matching `clusterToken`, ensuring only authorized servers and whitelisted external IPs can participate.

### Connection trust and whitelisting
- The relay only accepts Minecraft clients by default. Each client performs a handshake declaring itself as a Minecraft node and is rejected if it skips this step.
- If you need to let a non-Minecraft tool (for example, an external bot) talk in global chat, start the relay with a comma-separated whitelist of trusted IPs:
  ```bash
  java -Dwilderness.globalchat.token=<moderationToken> \
       -Dwilderness.globalchat.whitelist=192.0.2.10,198.51.100.8 \
       -cp <path-to-mod-jar-or-classpath> \
       com.thunder.wildernessodysseyapi.globalchat.server.GlobalChatRelayServer <port>
  ```
  Non-Minecraft clients must connect from a whitelisted IP and identify as `external` during the handshake or they will be dropped.

## Persisted settings
`config/wildernessodysseyapi/global-chat.json` stores:
- `host` / `port`: Relay endpoint for the client to dial.
- `enabled`: Whether the client should auto-connect on server start after a bind.
- `allowServerAutostart`: Whether `/globalchat startserver` is allowed on this server (keep `false` to pin the relay to your dedicated host).
- `moderationToken`: Shared secret required for moderation actions.
- `downtimeHistory`: Rolling log of the last 10 connection failures.
