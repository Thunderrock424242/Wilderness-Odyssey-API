# Playtesting implementation and validation audit

Date: 2026-09-15. Branch requested: `in-dev`.

**Readiness: implementation and isolated validation delivered; the full production-readiness definition of done is not satisfied.** The focused tests, package build and headless GameTests pass. Four failures in the broader Java suite and validation of the combined current checkout remain release gates. Real two-client sessions and the external Discord bot have not been acceptance-tested.

## What was wrong and what changed

- Player login telemetry returned early on dedicated servers. The logical-server handlers now capture sampled session starts and matching ends, with a stable session ID, captured UUID/name, wall-clock timestamps, total play time and monotonic duration. Duplicate starts/ends are suppressed. Orderly shutdown captures ends before closing workers and the spool.
- Feedback acknowledged queued work before HTTP delivery. A small service now returns the actual asynchronous result; sending and completion are separate messages, with completion dispatched onto the server thread. Replies are bound to the original connection, including across player respawns, and cannot leak into a reconnect.
- Link codes and request volume were insufficiently bounded. Codes now accept 4–64 ASCII letters, digits, hyphens or underscores without changing case. Feedback and linking each have per-player cooldowns, a bounded shared start rate, bounded concurrent requests and a shared HTTP-429 pause. The existing Discord relay message contract is preserved.
- Webhook completion previously lacked a strong delivery contract. Official Discord requests use `wait=true`; custom relays must supply a supported acknowledgement. Invalid configuration, rejected codes, HTTP errors, malformed or oversized responses, timeout and transport failures produce safe results. A delivery acknowledgement never claims the bot actually linked an account.
- Playtesting credentials were in NeoForge SERVER configuration, which is synchronized to joining clients. All five private service categories now belong to installation-local COMMON config. A startup migration saves COMMON before removing old installation SERVER categories, preserves existing COMMON choices and other settings, and stops registration on unsafe migration without printing secrets. Per-world overrides require the documented manual selection/migration step.
- Reports could depend on successful optional enrichment/worker submission before entering durable retry ownership. Producers now enqueue basic immutable data first and enrich best effort. The existing queue remains the owner of retries and persistence; no separate telemetry store or executor was added.
- Persistence recovery and queue memory required stronger bounds. The queue skips invalid rows, limits nesting/scan size/entry count/serialized bytes, retains in-flight records for shutdown snapshots and assigns stable report IDs for receiver deduplication. Failed reports remain pending and rotate fairly. Overflow is bounded and reported.
- Network work stays on HTTP/I/O workers. Minecraft player data is captured on the server; Spark's command invocation is scheduled back to that server with a bounded worker wait. Optional metadata endpoints now default to empty.
- Administrators lacked a combined playtesting view. `/wo playtest status` requires permission level 2 and reports configured/enabled flags plus pending, retrying, in-flight, failed, dropped and last-success diagnostics without printing endpoints.
- The build workflow's former “tests and build” step did not run JUnit because this repository defines `build` as packaging. CI now explicitly runs `test`. GameTests include relevant `in-dev` pushes, retain main/PR support, and use path filters, cancellation of superseded runs and a timeout.
- Documentation and README now describe safe private configuration, cooldowns, acknowledgements, queue behavior and an actual multiplayer acceptance checklist.

The configuration scope follows the [NeoForge 1.21.1 configuration contract](https://docs.neoforged.net/docs/1.21.1/misc/config/). The delivery confirmation follows [Discord's Execute Webhook API](https://docs.discord.com/developers/resources/webhook#execute-webhook).

## Validation scope and results

The initial shared-checkout attempt could not configure Gradle because its unrelated `settings.gradle` included a missing `aether-server` directory. That directory appeared during final review; its presence has not yet been verified by a combined-current-checkout build. The user authorized an isolated playtesting copy. Validation used an archive of `in-dev` commit `27bc157fdc8e0ff26f711f87eae3ed1f223ff11e`, overlaid with the playtesting changes, under:

`.codex-build/playtest-validation`

Its build output is `.codex-build/playtest-validation/.codex-build`. The copy excludes the concurrent Aether, Echo, RAM and standalone structure-viewer changes. The isolated build script includes only the playtesting changes; the unrelated structure-viewer task in the live script was preserved.

All commands used JDK 21, the checked-in wrapper, quoted `'-PcodexBuildDir=.codex-build'`, `--no-parallel` and `--console=plain`. Commands were sequential.

| Check | Actual result |
| --- | --- |
| Shared-checkout compile attempt | Blocked before Java compilation by missing `aether-server` project directory |
| Isolated `:compileJava` | Passed |
| Final isolated `:playtestTest` | Passed: 47 tests, zero failures/errors/skips |
| Broader isolated `:test` | 1,183 tests: 1,176 passed, four failed, three skipped |
| Isolated `:build` | Passed; this is packaging, not an assertion that the broader tests passed |
| Isolated `:runGameTestServer` | Passed: all 32 required GameTests; Gradle reported BUILD SUCCESSFUL |
| Scoped diff and source review | No whitespace errors under the repository's normal Git settings; no TODO/FIXME stubs or client-only references in the playtesting/feedback/telemetry packages |
| Tested-source comparison | All reviewed playtesting source files match the isolated copy; the documented build-script exclusion is intentional |
| Package inspection | New transport/status/migration/session classes and updated language resource are present |
| Remote CI, real clients and external Discord bot | Not run |

The broader-suite count is from the earlier full run, before the final session-payload regression and shutdown adjustment. Final focused tests, compilation, packaging and GameTests cover the final production changes. The last change after packaging/GameTests strengthened only a test and updated docs; the final focused task passed again.

The headless server loaded the mod and tested real command registration, permission predicates and configuration boundaries. It is not a two-client login/logout, LAN, reconnect UX or Discord role-assignment test.

The isolated StructureGen step warned that no full mod registry catalog was present and used its verified vanilla fallback. A normal release/CI run should use the existing data-generation step. This audit did not overwrite real worlds, live configs or generated dependency JARs.

### Broader-suite failures

| Test | Failure |
| --- | --- |
| `AIConfigLoaderTest.bundledConfigDefinesAllSixFirstClassSubsystemProfiles` | Bundled Ollama executable setting parsed as null where the test expects an empty string |
| `OllamaLocalRuntimeTest.resolvesOnlyValidatedLoopbackHealthEndpoints` | IPv6 loopback endpoint lookup returned an empty optional |
| `ReleaseArtifactContractTest.packagedRecipesAndTagsUseMinecraft121SingularDirectories` | Packaged `logo.png` is missing |
| `CryoWakeupVoiceAssetsTest.manifestMatchesSourceTextAndEveryMeasuredFemaleVoiceClip` | `filtration_offline` subtitle duration expects 87 ticks; measured calculation yields 88 |

Relevant AI/Ollama source/tests and the voice manifest/WAV/test in the isolated copy were compared with baseline Git blobs and match. The packaging contract test also matches baseline; the playtesting build edits do not alter logo packaging. The language diff changes only feedback/link messages, not narration. These are failures in unchanged baseline components, not evidence of a failing playtesting regression. A separate pristine-baseline test run was not performed. They were not hidden or “fixed” by changing unrelated systems.

### Output and local evidence

The regular mod JAR is:

`.codex-build/playtest-validation/.codex-build/libs/wildernessodysseyapi-4.2.0.jar`

SHA-256: `F0A1A1A2F01433CFF0F83730A5B71F35E890B4B005B76C26EDB50EF61E4831CD`

This is an isolated validation artifact, not a certified combined-current-checkout release.

- Focused report: `.codex-build/playtest-validation/.codex-build/reports/tests/playtestTest/index.html`
- Broader report: `.codex-build/playtest-validation/.codex-build/reports/tests/test/index.html`
- Headless server log: `.codex-build/playtest-validation/.codex-build/run-gametest/logs/latest.log`
- Baseline record: `.codex-build/playtest-validation-base.txt`

To reproduce the focused check from the isolated copy on Windows:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat :playtestTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

Run `:build` or `:runGameTestServer` with the same options sequentially for packaging or server verification. The shared checkout now needs a fresh combined-source validation after the unrelated project changes settle.

## Tests added or changed

- `PlaytestRequestLimiterTest`: player cooldown, reconnect-style reuse, total starts, concurrent limits and bounded tracking.
- `PlaytestWebhookClientTest`: Discord wait parameter, safe endpoint validation, acknowledgements/rejections/HTTP outcomes, response limit, deadlines and network failure.
- `MinecraftVerificationRelayClientTest`: valid, malformed and overlong codes; missing config; preserved relay payload; rejected/expired code; HTTP error; malformed response and timeout.
- `FeedbackSubmissionServiceTest`: confirmed asynchronous delivery, no early success, scheduled server reply, failure, malformed response and exceptional completion.
- `PlaytestConfigMigrationTest`: private-category migration, preservation, precedence/idempotence, malformed-config redaction and missing files.
- `WildernessConfigSpecsTest`: all five private-service aliases target COMMON.
- `PlayerTelemetrySessionsTest`: session identity, deduplicated starts/ends, monotonic duration and cleanup; source regression guard against reintroducing the dedicated-server early return. The source guard is explicitly not live multiplayer proof.
- `PlayerTelemetryReporterTest`: added start/end payload assertions for session ID, timestamps, duration, playtime and absent optional metadata.
- `TelemetryReliabilityTest`: corrupt-middle-row recovery, bounded parsing, stable report ID through failure and actual spool reload followed by delivery at the recovered endpoint, bounded overflow and interrupted flush retention.
- `LocalWebhook`: loopback-only HTTP fixture; no real Discord messages sent.
- `PlaytestGameTests`: loaded-server command registration, level-2 permissions and private-config boundaries.

Existing telemetry persistence tests remain in the focused suite.

## Remaining release gates and operational limits

1. Validate the now-present Aether project configuration and rerun the combined current source. The isolated result does not certify unrelated concurrent work.
2. Resolve the four broader-suite failures and obtain a passing full test/CI run. Local workflow edits have not been pushed or observed in GitHub Actions.
3. Run the [multiplayer acceptance checklist](playtesting.md#multiplayer-acceptance-checklist) on staging with two real clients, then integrated single-player/LAN. Verify actual join/leave/reconnect/shutdown reports, player messages and optional Spark integration.
4. Verify the actual Discord bot's code issuance, expiry, single-use checks, identity mapping and role/account confirmation. HTTP delivery cannot observe a later bot decision.
5. Select and migrate any existing per-world SERVER overrides and default-config templates manually as documented. Installation-level migration does not select between multiple worlds. If webhook URLs were previously synchronized to players, rotate them before public use.
6. Keep COMMON config and the retry spool private. They contain credentials and pending player data. Optional geolocation/account lookups remain off unless explicitly configured.
7. Delivery is at least once for retained records. Receivers should deduplicate `report_id`; an ambiguous timeout can precede a replay. Bounded overflow, disk failures and the asynchronous persistence window mean lossless delivery is not guaranteed. Some rows beyond recovery limits are not included in the dropped counter; diagnostic counters reset on restart.
8. Metadata/Spark enrichment can be absent if a basic report already left the queue. Disabling a producer or logout export mid-session can intentionally leave an unmatched previously exported login.

No remaining TODO/stub in the touched playtesting code was found. No commit, push, deployment, credential rotation, live Discord submission or modification of existing worlds was performed.

## Every file changed by this task

The following inventory covers **43 files**, including this report. Paths are relative to the repository. Unrelated dirty or staged files belong to other work and are excluded. Only the playtesting portions of `build.gradle` were changed by this task.

- [.github/workflows/build.yml](../.github/workflows/build.yml)
- [.github/workflows/gametest.yml](../.github/workflows/gametest.yml)
- [build.gradle](../build.gradle)
- [docs/configuration.md](../docs/configuration.md)
- [docs/playtesting-audit.md](../docs/playtesting-audit.md)
- [docs/playtesting.md](../docs/playtesting.md)
- [README.md](../README.md)
- [src/main/java/com/thunder/wildernessodysseyapi/command/ModCommands.java](../src/main/java/com/thunder/wildernessodysseyapi/command/ModCommands.java)
- [src/main/java/com/thunder/wildernessodysseyapi/config/ModConfigRegistration.java](../src/main/java/com/thunder/wildernessodysseyapi/config/ModConfigRegistration.java)
- [src/main/java/com/thunder/wildernessodysseyapi/config/PlaytestConfigMigration.java](../src/main/java/com/thunder/wildernessodysseyapi/config/PlaytestConfigMigration.java)
- [src/main/java/com/thunder/wildernessodysseyapi/config/WildernessConfigSpecs.java](../src/main/java/com/thunder/wildernessodysseyapi/config/WildernessConfigSpecs.java)
- [src/main/java/com/thunder/wildernessodysseyapi/feedback/FeedbackCommand.java](../src/main/java/com/thunder/wildernessodysseyapi/feedback/FeedbackCommand.java)
- [src/main/java/com/thunder/wildernessodysseyapi/feedback/FeedbackConfig.java](../src/main/java/com/thunder/wildernessodysseyapi/feedback/FeedbackConfig.java)
- [src/main/java/com/thunder/wildernessodysseyapi/feedback/FeedbackSubmissionService.java](../src/main/java/com/thunder/wildernessodysseyapi/feedback/FeedbackSubmissionService.java)
- [src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestGameTests.java](../src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestGameTests.java)
- [src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestReplies.java](../src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestReplies.java)
- [src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestRequestLimiter.java](../src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestRequestLimiter.java)
- [src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestStatusCommand.java](../src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestStatusCommand.java)
- [src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestWebhookClient.java](../src/main/java/com/thunder/wildernessodysseyapi/playtest/PlaytestWebhookClient.java)
- [src/main/java/com/thunder/wildernessodysseyapi/playtest/verification/MinecraftVerificationCommands.java](../src/main/java/com/thunder/wildernessodysseyapi/playtest/verification/MinecraftVerificationCommands.java)
- [src/main/java/com/thunder/wildernessodysseyapi/playtest/verification/MinecraftVerificationRelayClient.java](../src/main/java/com/thunder/wildernessodysseyapi/playtest/verification/MinecraftVerificationRelayClient.java)
- [src/main/java/com/thunder/wildernessodysseyapi/playtest/verification/MinecraftVerificationRelayConfig.java](../src/main/java/com/thunder/wildernessodysseyapi/playtest/verification/MinecraftVerificationRelayConfig.java)
- [src/main/java/com/thunder/wildernessodysseyapi/server/ServerLifecycleEvents.java](../src/main/java/com/thunder/wildernessodysseyapi/server/ServerLifecycleEvents.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/EventTelemetryConfig.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/EventTelemetryConfig.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/EventTelemetryReporter.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/EventTelemetryReporter.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryConfig.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryConfig.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryReporter.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryReporter.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetrySessions.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetrySessions.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryConfig.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryConfig.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryHttp.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryHttp.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryQueue.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryQueue.java)
- [src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryQueueProcessor.java](../src/main/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryQueueProcessor.java)
- [src/main/resources/assets/wildernessodysseyapi/lang/en_us.json](../src/main/resources/assets/wildernessodysseyapi/lang/en_us.json)
- [src/test/java/com/thunder/wildernessodysseyapi/config/PlaytestConfigMigrationTest.java](../src/test/java/com/thunder/wildernessodysseyapi/config/PlaytestConfigMigrationTest.java)
- [src/test/java/com/thunder/wildernessodysseyapi/config/WildernessConfigSpecsTest.java](../src/test/java/com/thunder/wildernessodysseyapi/config/WildernessConfigSpecsTest.java)
- [src/test/java/com/thunder/wildernessodysseyapi/feedback/FeedbackSubmissionServiceTest.java](../src/test/java/com/thunder/wildernessodysseyapi/feedback/FeedbackSubmissionServiceTest.java)
- [src/test/java/com/thunder/wildernessodysseyapi/playtest/LocalWebhook.java](../src/test/java/com/thunder/wildernessodysseyapi/playtest/LocalWebhook.java)
- [src/test/java/com/thunder/wildernessodysseyapi/playtest/PlaytestRequestLimiterTest.java](../src/test/java/com/thunder/wildernessodysseyapi/playtest/PlaytestRequestLimiterTest.java)
- [src/test/java/com/thunder/wildernessodysseyapi/playtest/PlaytestWebhookClientTest.java](../src/test/java/com/thunder/wildernessodysseyapi/playtest/PlaytestWebhookClientTest.java)
- [src/test/java/com/thunder/wildernessodysseyapi/playtest/verification/MinecraftVerificationRelayClientTest.java](../src/test/java/com/thunder/wildernessodysseyapi/playtest/verification/MinecraftVerificationRelayClientTest.java)
- [src/test/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryReporterTest.java](../src/test/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryReporterTest.java)
- [src/test/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetrySessionsTest.java](../src/test/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetrySessionsTest.java)
- [src/test/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryReliabilityTest.java](../src/test/java/com/thunder/wildernessodysseyapi/telemetry/TelemetryReliabilityTest.java)
