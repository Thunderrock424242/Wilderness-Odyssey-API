# Dedicated multiplayer playtesting

Wilderness Odyssey uses the existing server command registrations, asynchronous
HTTP transport and persistent telemetry queue. The logical server owns player
snapshots and completion messages; the client does not submit reports.

## Configuration and secrets

Playtesting service categories live in
`config/wildernessodysseyapi/wildernessodysseyapi-common.toml` on the server:
`[verificationRelay]`, `[feedback]`, `[telemetry]`, `[playerTelemetry]` and
`[eventTelemetry]`. COMMON config is local to the installation and is not sent
to connecting clients. Gameplay settings remain in the SERVER config.

This distinction matters: NeoForge synchronizes SERVER configuration to joining
clients. Do not put webhook credentials in a synchronized config or distribute
your production common config with a client pack. See the
[NeoForge configuration contract](https://docs.neoforged.net/docs/1.21.1/misc/config/).
Keep configs, retry spools and their backups private.

Existing playtesting categories in the old unified server file are migrated
before NeoForge registers configs. Consult [configuration.md](configuration.md)
for migration behavior. If an older server has already used secret webhook
URLs in synchronized SERVER config, rotate those webhook credentials before
the next public playtest.

The following example deliberately leaves endpoints empty. Set private URLs
locally; the empty example does not enable network delivery.

```toml
[verificationRelay]
enableServerVerificationRelay = true
discordVerificationWebhookUrl = ""
requestTimeoutSeconds = 10
cooldownSeconds = 30

[feedback]
enabled = true
webhookUrl = ""
requestTimeoutSeconds = 10
maxMessageLength = 500
cooldownSeconds = 30

[telemetry]
enabled = true
queueMaxSize = 512
queueFlushIntervalTicks = 200
queueFlushBatchSize = 32

[playerTelemetry]
enabled = true
sheetWebhookUrl = ""
exportOnLogout = true
requestTimeoutSeconds = 10
sampleRatePercent = 100.0
sampleEveryNth = 1
geoIpEndpoint = ""
accountAgeEndpoint = ""
includeSparkReport = false
sparkWebhookUrl = ""
hashPlayerIdentifiers = false
identifierHashSalt = ""
retryMaxAttempts = 2
retryBaseDelayMs = 500
retryMaxDelayMs = 5000

[eventTelemetry]
enabled = false
webhookUrl = ""
```

Verification, feedback, player telemetry, event telemetry, metadata lookups and
Spark reports are independently optional. Leave geolocation and account-history
lookups blank unless the playtest explicitly needs them. Missing lookup data is
represented as unavailable; it must not suppress login/logout reporting.
Player identity hashes are optional. If enabled, use a private stable salt so
administrators can correlate sessions without exporting raw identifiers.

Telemetry persistence and enrichment use the existing bounded asynchronous work
system. Keep `[asyncThreading].enabled = true` in the common config for telemetry.
Network queue saturation must never run HTTP requests on a server tick.

## Minecraft to Discord linking

Players use `/wo link <code>`. Accepted codes are 4–64 ASCII letters, digits,
hyphens or underscores. Codes remain case-sensitive and are relayed unchanged.
The bot issuing codes must use this format.

The existing Discord webhook payload is preserved: its `content` is JSON with
`type = "wo_minecraft_verify"`, `code`, `minecraftUuid` and `minecraftName`.
Mentions are suppressed. The relay bot must trust messages from the private
server webhook, consume codes once, enforce expiration and match the code to
the requesting Discord account.

A successful HTTP delivery **does not establish that the account is linked**.
The player is told to check Discord for the bot's decision. An expired code can
therefore produce a successful webhook delivery followed by a rejection in
Discord. The mod cannot read a later bot decision through this one-way webhook.
It never grants a role or verifies an account based on HTTP success alone.

Each player has a configurable cooldown (30 seconds by default). Reconnecting
does not reset that cooldown. Each command also limits starts to one per second
across the server and allows at most four requests in flight. HTTP 429 imposes
a 60-second shared pause. Requests are never automatically replayed by these
commands; after an ambiguous timeout, check Discord before submitting again.

## Feedback and HTTP outcomes

Players use `/feedback <message>`. They first see a sending message, then a
success or retry message after completion on the server thread. A disconnected
player does not receive a stale reply in a later session. Feedback uses a
Discord embed with the message and the submitting player's name/UUID; mentions
are disabled. Its default maximum is 500 characters, configurable to 2000.

Official Discord webhook calls use `wait=true`, which asks Discord to confirm
that the message was saved. Discord documents why default `wait=false` is
insufficient for that guarantee in its
[Execute Webhook API](https://docs.discord.com/developers/resources/webhook#execute-webhook).

For a custom relay, the existing Discord-shaped request must be supported.
Accepted acknowledgements are HTTP 204, a 2xx Discord message object with a
numeric `id`, or a 2xx object with a boolean `accepted` or `success`.
An explicit false is rejection. Arbitrary HTML, empty 200 responses, malformed
JSON and string-valued booleans are not success.

HTTP 400/409/410/422 is rejection; 401/403/404 means configuration/unavailability;
429 means throttling; other HTTP errors, timeout and network failure fail
gracefully. Responses are capped at 16 KiB, requests at the configured 1–60
seconds, and redirects are not followed. Use HTTPS in production. Private
loopback HTTP is useful for local relay/testing.

Administrator logs include a status or failure category, never webhook URLs,
response bodies, codes, tokens or exception text that could contain a URI.

## Telemetry delivery and recovery

Login and logout use the same sampled session ID. The server captures identity,
timestamps, total play time and monotonic session duration before handing
immutable snapshots to workers. Connected players receive a final session-end
capture during orderly server shutdown, before workers and the queue close.
The subsequent vanilla logout event does not create a duplicate session end.

Reports enter the existing retry queue before optional metadata requests run.
The worker pool performs HTTP requests and coalesces spool writes; orderly
shutdown writes one final snapshot. Optional metadata and Spark enrichment are
best effort and may be absent if the basic report has already been delivered.
Disabling a telemetry producer or its logout export can intentionally leave a
previously reported login without a reported end.

The private spool is `config/wildernessodysseyapi/telemetry-queue.jsonl` under
the server's working directory. It contains delivery URLs and pending player
data. Failed requests stay queued and retry in bounded batches. Each report has
a stable `report_id` across retries; receivers should deduplicate by that ID.
An interrupted request may already have reached the receiver, so replay can
deliver a duplicate. This is at-least-once delivery for retained reports, not an
exactly-once or lossless guarantee.

Capacity is bounded by `queueMaxSize` (at most 10,000 entries), a 16 MiB total
serialized budget and a 256 KiB reserved per-report budget. When full, enqueueing
discards the oldest retained report and increments the dropped counter.
Malformed persisted rows are skipped while valid rows are recovered, with
bounded parsing and scan size. Recovery stops at the entry/scan limit; the
dropped counter does not count every row beyond those limits. Failed, dropped
and last-success diagnostics are process-local; pending reports and their
attempt metadata persist.

Disk writes are asynchronous during play. A hard crash or power failure can lose
the latest changes before a snapshot reaches disk. A disk failure is logged and
leaves reports in memory for a later write or orderly shutdown. No queue limit
can preserve every report through an indefinitely unavailable endpoint.

## Administrator checks

Operators with permission level 2 can run:

- `/wo playtest status`: enablement, configured endpoint flags, queue state and
  latest telemetry success; no secret values are displayed.
- `/telemetrystats`: existing telemetry queue diagnostics.

A configured endpoint flag means the URL is syntactically usable, not that the
remote service is healthy. These commands do not probe external services.

## Automated validation

Use JDK 21 and the checked-in wrapper; run these sequentially:

```powershell
.\gradlew.bat :compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :playtestTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :test '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :build '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :runGameTestServer '-PcodexBuildDir=.codex-build' --no-parallel
```

The focused task uses the repository's existing offline JUnit runtime. Its HTTP
tests use only an ephemeral loopback server. They exercise real requests,
response parsing, deadlines, cooldowns and deferred server-thread replies.
The GameTest checks actual command registration and operator permissions in the
loaded server runtime. With `codexBuildDir`, its world/config output is isolated
under that build directory.

The build workflow explicitly runs `test` because this repository intentionally
keeps `build` as packaging. It already runs on all pushes, including `in-dev`.
GameTests run on PRs, manual dispatch, and code/build/workflow pushes to
`main` and `in-dev`. Documentation-only pushes avoid the additional GameTest
run. Superseded runs are canceled.

## Multiplayer acceptance checklist

Before inviting testers, use a fresh dedicated NeoForge server and two real
clients with private staging endpoints:

1. Check status as an operator and verify ordinary players cannot access it.
2. Join/leave with both players. Confirm UUID/name, login/logout timestamps,
   matching session identity and sensible duration/playtime in collected data.
3. Restart the server with queued reports while the telemetry endpoint is
   unavailable. Restore it and confirm pending reports drain without growth
   beyond configured bounds. Inspect dropped/failed counters.
4. Submit feedback, verify the sending message precedes confirmation, and
   compare the saved Discord message. Test HTTP failure and timeout with a
   local relay.
5. Link a valid code, then an expired and reused code. Check both the Minecraft
   delivery message and the bot's actual account/role decision in Discord.
6. Repeat commands quickly, reconnect during a request, and verify cooldown,
   mention suppression and absence of stale messages.
7. Repeat a login/logout and feedback check in integrated single-player/LAN.
8. Review server logs and ensure production webhook credentials are not exposed.

JUnit and headless GameTests do not prove the external Discord bot's behavior,
real account linking, client UX, or two-player production readiness.