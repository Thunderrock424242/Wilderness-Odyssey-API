# Invited client playtest diagnostics

The client collector supports single-player and multiplayer installations. It is independent of the older logical-server telemetry described in [playtesting.md](playtesting.md). It does not require `/minecraft link` or `/wo link`. The backend/website implementation must follow [the v1 API contract](playtest-diagnostics-contract.md) before the complete hosted workflow can operate.

## Tester use

1. Obtain the public API address and private single-use invitation from the approved playtest invitation page or staff.
2. Install the test build and open **Playtest** at the top left of the title or pause menu. `/woplaytest` is an in-world fallback.
3. Enter the API address and invitation, read the collection notice, and select **I agree - enroll this game**. Never put the code in public chat.
4. Play normally. The collector starts only when the backend explicitly enables this tester/campaign/collection period. The HUD displays **Playtest recording** while permission is active.
5. **Stop and leave** immediately disables log admission locally and invalidates in-flight results. The independent watchdog cancels our sampler; worker cleanup removes credentials, saved interruption marker and pending artifacts. A fresh invitation is needed to enroll again.

The invitation authorizes this installation's diagnostic submissions. A reported player name/UUID is not verified Minecraft account ownership. Staff website authentication remains a separate authority.

## Collection and separation

- Every game connection has a new UUID session, with `singleplayer` or `multiplayer` metadata. The worker sends observed recording/end status and periodic session acknowledgements.
- Logs, interrupted-session evidence, session metadata and Spark recordings are separate immutable artifacts with their own IDs, linked by tester/campaign/collection/session. Raw log text is not copied into session-summary artifacts.
- A root Log4j appender admits messages only during active collection. It filters known chat sources/formats, redacts recognized credentials, emails, IPs and user home paths, and retains at most 256 KiB. Redaction is best effort; no arbitrary files or full crash-directory scans are uploaded.
- Interrupted recovery uses the already-redacted last saved session tail. It is labeled incomplete evidence, not a guaranteed final crash report. Recovery needs current permission for the exact same collection period. Stable recovery artifact IDs avoid ordinary duplicate recovery.
- The installation credential and bounded pending queue live under `config/wildernessodysseyapi/playtest-diagnostics/`, which is ignored by Git. Do not distribute this directory in a pack. No credentials are placed in synchronized SERVER config.

## Control and failure behavior

One daemon I/O worker performs the five-second bounded HTTP calls and disk work. A separate one-second watchdog checks monotonic authorization expiry independently of the game loop. Policy polling happens every ten seconds; the backend grants at most 60 seconds of permission. No HTTP or disk work occurs on client tick handlers. Tick snapshots contain no live world/player references.

Backend Off/revocation must immediately reject uploads. The client stops on receipt or lease expiry and discards unsent data. Each off/on cycle has a different `collectionId`; old queued data cannot leak into the new collection period. Previously committed backend artifacts remain until explicitly deleted. Minecraft's normal local logs continue independently.

Queue capacity is 16 artifacts / 8 MiB, with oldest-data dropping and a counter in session acknowledgements. Artifact IDs survive retries; the backend must deduplicate and return matching durable acknowledgements. Recovery parsing scans at most 64 candidate entries / 8 MiB. Failed or ambiguous network delivery is not a lossless/exactly-once guarantee.

Normal logout finalizes logs and attempts Spark export and delivery. Process shutdown allows only a two-second final worker window before cancellation. Pending data may be delivered after a later authorized launch. A crash, force-close, power loss or unavailable endpoint can leave an incomplete report. No helper process remains running after Minecraft closes.

## Optional Spark compatibility

The adapter discovers the client Spark plugin through the registered `sparkc` command, not `SparkProvider`, which may point at an integrated server. It uses runtime-checked internal APIs from the installed `curse.maven:spark-361579:6225208` generation. Unsupported API shapes produce `unavailable`, never a fake success.

It starts its own Java sampler of the client game thread at 10 ms intervals, bounded to 120-second segments. It declines to start if Spark already has an active sampler and never replaces or stops another owner's sampler. Export produces actual raw `.sparkprofile` protobuf data, capped at 1 MiB, as a private backend artifact. There is no automatic public Spark-host upload. Creator information, arbitrary server configurations, extra platform metadata, system/JVM statistics and platform statistics are removed before serialization. Class-source mappings are omitted; sampled Java stacks and mod versions remain. This narrows metadata compared with a normal Spark export.

The API-shape inspection and automated collector tests do **not** prove real Minecraft/Spark export, viewer compatibility or overhead. Validate those with the installed modpack before a public playtest. Shared dedicated-server profiles require a separate trusted server producer and must be labeled `server` on the website.

## Validation and live acceptance

Run serially with JDK 21:

```powershell
.\gradlew.bat :compileJava :playtestTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

This includes real loopback HTTP tests of enrollment, authorization headers and durable acknowledgements; lease expiry and withdrawal fencing; per-kind separation; interrupted recovery; queue bounds; chat filtering/redaction; and collection-period changes.

For live acceptance, stage the backend contract, enroll two independent installations, and check single-player plus multiplayer. Verify the website profiles stay separate, inspect an exported Spark file in the viewer, toggle Off during recording and while a client is offline, test missing/busy Spark, disconnect/reconnect, restart with pending data, and check keyboard/narrow-window use of the enrollment screen. Confirm native Save & Quit still completes without an unbounded wait.

## Branch handoffs

- [Bot/backend Codex message](handoffs/2026-10-25-bot-playtest-diagnostics.md)
- [Website Codex message](handoffs/2026-10-25-website-playtest-diagnostics.md)

The messages are copy-ready instructions, not claims that those branches have been modified or deployed.
