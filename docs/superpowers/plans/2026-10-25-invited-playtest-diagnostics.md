# Invited Playtest Diagnostics Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement inline. Preserve unrelated dirty work and use one Gradle process.

**Goal:** Implement the mod's invited, remotely controlled client diagnostic collector and prepare exact bot/website handoffs.

**Architecture:** Feature-owned pure Java protocol, state, bounded persistence and transport under `playtest/diagnostics`; client-only NeoForge/Spark/UI adapters under its `client` child. Existing server telemetry is unchanged.

**Tech Stack:** Java 21, NeoForge 21.1.256, Gson, JDK HTTP, Log4j2, JUnit Jupiter.

**Spec:** `docs/superpowers/specs/2026-10-25-invited-playtest-diagnostics.md`

## Global Constraints

- Invitation enrollment, no required Minecraft ownership verification; both single-player and multiplayer.
- Default off, explicit consent, fail closed after a maximum 60-second lease.
- No client staff secrets, arbitrary upload URLs, file paths or remote command execution.
- Keep logs, crashes, Spark and session artifacts separate, linked by stable IDs.
- Preserve unrelated work and execute Gradle serially with `-PcodexBuildDir=.codex-build --no-parallel`.

## Review Focus

- A stale network completion after local stop must not restart capture or restore credentials.
- A denied or expired policy must discard pending payloads before any further upload.
- A different campaign/tester must not receive old pending artifacts.
- Spark must never cancel another owner's recording or publish a profile to a public host.
- Large/corrupt persisted state and logs must stay bounded and fail closed.

## Tasks

- [x] Write behavior tests for protocol, leases, redaction, queue and HTTP using real loopback requests; run to demonstrate missing implementation.
- [x] Implement pure protocol/transport and bounded disk store; verify the preceding tests.
- [x] Implement client lifecycle, diagnostic appender, optional Spark adapter and enrollment screen; compile and run focused tests.
- [x] Write shared API contract and copy-ready branch handoffs; review stop/recovery semantics against the code.
- [x] Review scoped diff and required validation. Report real-client/backend acceptance separately.

Execution ruling: the user explicitly requested implementation here and copy-ready messages for other branches. Proceed inline without another approval round, no commits/deployment, and no worktree switch. The unrelated dirty config/build/audio work remains intact.

## Execution record

- Initial RED: new diagnostics types absent; normal test compilation also found concurrently changing water test signatures. No unrelated tests were edited. A generated Gradle init filter temporarily narrowed test compilation to this package, using the existing compiler and JUnit task.
- Behavior RED/GREEN: category-off retained a saved log tail; test failed, cleanup corrected. Vanilla `<player>` chat was not filtered; test failed, admission filter corrected. HTTP tests first failed because the implementation did not exist.
- Core/collector checks: 14 new tests cover origins, monotonic expiry/generation, malformed policy, redaction/chat, Unicode bounds, separate queue kinds, persistence, quota, HTTP enrollment/acknowledgement, separation, interruption, category-off, late policy and new collection periods.
- Final normal command (without init filter): `:compileJava :playtestTest -PcodexBuildDir=.codex-build --no-parallel --console=plain` passed; 61 tests, zero failures/errors/skips. The concurrent water test compilation blockers were absent in this final checkout run.
- Review: kept all production additions inside `playtest/diagnostics`; existing gameplay/server telemetry/config/build owners are unchanged. Client-only event subscriber prevents dedicated-server class loading. Spark's own raw-protobuf export path and adapter signatures were inspected in the installed dependency, without modifying it.
- External acceptance not run: website/backend implementation, installed-client menu/HUD/Spark exports, multiplayer sessions, real invitation delivery, measured recording overhead and hosted deployment. No commit, push, task messaging, live command registration or deployment performed.
