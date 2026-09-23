# Aether Bundled Launcher Implementation Plan

Historical record: the subsequent public-access update removes access keys and credential generation. Current behavior and upgrade steps are in [the bundled server guide](aether-bundled-server.md). References to authentication below describe the earlier implementation.

> Execution: main agent, sequential implementation and Gradle validation. Follow the existing repository's one-Gradle-process rule and preserve unrelated changes.

**Goal:** Deliver one self-contained, platform-specific Aether JAR that extracts and starts its own Ollama runtime and approved llama3.1:8b model.
**Architecture:** Add a bootstrap owner around the existing standalone gateway. Keep packaging, extraction, configuration and process lifecycle in separate units. Do not change the Minecraft networking path.
**Tech stack:** Java 21, existing Gson/SnakeYAML, Gradle ZIP64 packaging, native Ollama 0.17.7.
**Spec:** aether-bundled-launcher-design.md

## Global constraints

- No Minecraft or NeoForge dependency in the standalone service.
- Model: llama3.1:8b with Aether's current personality and lore, explicitly approved by the user.
- No system installer, elevated startup, OS service modification or global Ollama model-store writes.
- Full bundle contains native libraries and model bytes. Do not substitute download-on-first-run without approval.
- User configuration and credentials survive restart. Never print credentials.

## Review focus

- Malicious/incorrect manifest paths and filesystem symlinks: installation must fail before writing outside the application directory.
- Corrupt or interrupted extraction: no partial file may become a valid installed asset.
- Existing settings and failed startup: configuration must survive and owned child processes must stop.
- A busy loopback port or unsupported host: do not reuse/kill an unrelated runtime; report a clear startup failure.
- Multi-gigabyte model data: stream files, use ZIP64, and validate free disk space without loading blobs into heap.

## Task 1: Verified distribution and setup

Files: bootstrap/BundleManifest.java, BundleInstaller.java, DataDirectory.java, SetupConfig.java; matching bootstrap tests.

- [x] Check accepted/rejected host platforms and test manifest paths, hash mismatch, repeated extraction and singleton locking.
- [x] Implement immutable manifest records and bounded manifest parsing.
- [x] Implement streamed atomic extraction with root/symlink checks and per-asset SHA-256 verification.
- [x] Test and implement first-launch defaults, credential generation and preservation of existing settings.
- [x] Run focused standalone bootstrap tests.

## Task 2: Runtime lifecycle and executable entry

Files: bootstrap/ManagedOllama.java, AetherLauncher.java; existing AetherServer main helper and executable tests.

- [x] Test launcher arguments and missing/mismatched bundle failures.
- [x] Implement owned native process startup, bounded readiness/model setup and cleanup on failure/shutdown.
- [x] Add a launcher entry point that starts managed mode when a bundle is present and preserves explicit external-gateway mode.
- [x] Run all standalone tests and executable process tests.

## Task 3: ZIP64 packaging and actual model smoke test

Files: aether-server/bundle.gradle, build.gradle, deployment/configuration docs.

- [x] Build a manifest from explicit runtime/model inputs; include only the selected model and its referenced blobs, plus licenses.
- [x] Produce a platform-labelled complete JAR using stored ZIP entries and ZIP64.
- [x] Launch the Windows bundle in an isolated application data folder; check health, authentication and a bounded actual-model request, then verify shutdown/restart.
- [x] Record artifact sizes/hashes, platform limits and concrete hosting setup instructions.
- [x] Confirm the public gateway interface is unchanged; no mod-side test rerun was required for this launcher extension. Preserve unrelated source changes.

Completed with Windows live inference/startup/shutdown checks and Linux packaging. See [delivery evidence](aether-bundle-verification.md) for exact results, remaining Linux/hosting/gameplay checks and the reply-format corrections discovered during validation.
