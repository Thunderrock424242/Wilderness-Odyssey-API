# Global chat setup and operations

The global chat system lets multiple Wilderness Odyssey servers share messages through a lightweight relay process. This guide covers how to anchor the relay to a specific dedicated server, connect other servers, and use moderation/opt-in controls.

## Components
- **Relay server**: External JVM process that accepts TCP connections, relays chat, enforces rate limits/bans, and handles admin actions.
- **Embedded client**: Runs inside each Minecraft server JVM after you bind it to a relay host and port. It keeps a downtime history, tracks ping, and fans out messages only to opted-in players.
- **Commands**: Registered under `/globalchat` for binding, status checks, opt-in, messaging, relay lifecycle, and moderation.

## Anchoring the relay to a dedicated host
1. Pick the Minecraft server that should host the central relay. On that machine, launch the relay directly:
   ```bash
   java -Dwilderness.globalchat.token=<moderationToken> \
        -cp <path-to-mod-jar-or-classpath> \
        com.thunder.wildernessodysseyapi.globalchat.server.GlobalChatRelayServer <port>
   ```
   - Replace `<port>` with the TCP port you want the relay to listen on (defaults to `39876` if omitted).
   - The `wilderness.globalchat.token` system property must match the moderation token you configure on each participating server.
2. On every other server, **keep relay autostart disabled** (the default) so the `startserver` command is blocked and the relay stays anchored to your chosen host.
3. If you explicitly want a server to be allowed to spawn the relay sidecar, run `/globalchat allowautostart true` as an operator on that server.

## Connecting a server to the relay
1. As an operator, bind the server to the relay host and port:
   ```
   /globalchat bind <host> <port>
   ```
   This persists to `config/wildernessodysseyapi/global-chat.json` and immediately attempts a connection.
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
  - `/globalchat mod role <serverId> <role>` to tag a connected server with a custom role label.

## Persisted settings
`config/wildernessodysseyapi/global-chat.json` stores:
- `host` / `port`: Relay endpoint for the client to dial.
- `enabled`: Whether the client should auto-connect on server start after a bind.
- `allowServerAutostart`: Whether `/globalchat startserver` is allowed on this server (keep `false` to pin the relay to your dedicated host).
- `moderationToken`: Shared secret required for moderation actions.
- `downtimeHistory`: Rolling log of the last 10 connection failures.
