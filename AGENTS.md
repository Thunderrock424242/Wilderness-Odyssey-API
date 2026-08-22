# AGENTS.md

# Repository Guidelines

## Priority Rules

These rules take priority during normal repository work.

1. Follow the user's requested scope. Do not expand a task into unrelated cleanup, redesign, or refactoring.
2. Inspect the existing implementation and architecture before making substantial changes.
3. Prefer source-code changes over generated-output manipulation.
4. Keep changes modular, focused, and compatible with existing project patterns.
5. Use the smallest meaningful validation step first.
6. Only one agent or process controlled by Codex may run Gradle, NeoGradle, NeoForm, Minecraft development runs, or data generation at a time.
7. Treat generated NeoForm and Gradle JARs as build infrastructure, not source files.
8. Do not modify Windows permissions, NTFS ACLs, antivirus settings, Codex sandbox permissions, or system security settings without explicit user approval.
9. Do not describe an environment or permission failure as a code failure.
10. Never claim validation passed unless the validation command actually completed successfully.

---

## AI Coding Expectations

When working in this repository, act like a careful Minecraft/NeoForge developer, not merely a code generator.

### Before making changes

* Inspect the existing package structure and follow current project patterns.
* Inspect relevant callers, registrations, configs, resources, and documentation before changing architecture.
* Briefly explain the implementation plan before making substantial changes.
* Prefer small, modular changes over giant all-in-one classes.
* Reuse existing architecture, helpers, registries, configs, APIs, and conventions when appropriate.
* Do not rewrite unrelated systems unless specifically requested.
* Avoid speculative refactors that are unnecessary for the requested task.
* Ask for clarification only when a requirement is genuinely blocked.
* Otherwise make a reasonable implementation decision and clearly state the assumption.

### When writing code

* Prefer readable code over clever code.
* Keep methods focused.
* Avoid unnecessarily large classes.
* Add short comments above major or non-obvious sections when useful.
* Use Javadocs for public classes, public methods, registries, config classes, event handlers, capabilities or attachments, mixins, and API-facing systems.
* Explain why Minecraft or NeoForge systems are being used when the reasoning is not obvious.
* This is especially important for registries, event buses, data generation, attachments, mixins, networking, worldgen, threading, rendering, and config synchronization.
* Do not over-comment simple assignments, getters, setters, or obvious conditions.
* Keep comments useful for a future developer who did not write the system.
* Avoid abstractions that do not solve a real project problem.
* Do not introduce a new dependency when the existing stack can reasonably handle the task.

### When finishing a task

* Review the final diff.
* Summarize meaningful files and systems changed.
* Explain new classes or major methods.
* Explain how to test the feature in-game when applicable.
* Mention assumptions, limitations, compatibility concerns, or deferred work.
* Run the relevant Gradle task when practical.
* Fix genuine compile or test failures caused by the change before calling the implementation complete.
* Report environment-blocked validation separately from code failures.
* Prefer targeted validation before broader validation.
* Do not invent alternate build pipelines when the normal Gradle/NeoForge build system is sufficient.

---

# Standard Task Workflow

Use this workflow unless the task requires something different.

## 1. Inspect

Determine:

* Which feature owns the requested behavior.
* Which classes currently implement related behavior.
* Which registrations, configs, resources, events, mixins, or networking paths are involved.
* Whether tests already exist.
* Whether the change affects client, server, or both.

## 2. Plan

For non-trivial changes, briefly describe:

* Files likely to change.
* Architecture being reused.
* New responsibilities being introduced.
* Important compatibility or lifecycle considerations.

## 3. Implement

* Keep the change focused.
* Preserve surrounding style.
* Avoid unrelated cleanup.
* Keep intermediate states compile-safe when practical.

## 4. Validate

Use the smallest meaningful validation command first.

Escalate only when broader validation adds useful confidence.

## 5. Review

Before finishing:

* Review the diff.
* Remove debug code.
* Check for unintended generated files.
* Check for unrelated formatting changes.
* Check for secrets or machine-specific paths.

## 6. Report

Clearly separate:

* Implementation result.
* Validation result.
* Environment limitations.
* Remaining manual testing.

---

# Project Structure & Module Organization

Production code lives in:

`src/main/java/com/thunder/wildernessodysseyapi`

Organize code into feature packages such as:

* `worldgen`
* `cloak`
* `ai`
* `riftfall`

Keep new code with the feature it supports rather than creating broad generic utility packages.

Examples:

* Cloaking systems belong in `cloak`.
* Riftfall systems belong in `riftfall`.
* AI companion systems belong in `ai`.
* World generation, structures, biome logic, placement, and data generation helpers belong in `worldgen`.

Avoid generic packages such as:

* `util`
* `manager`
* `helper`

unless the functionality is genuinely shared by several independent systems.

Feature-specific helpers should remain inside their feature package.

Minecraft assets, data-pack JSON, shaders, YAML, configs, and defaults belong in:

`src/main/resources`

Mod metadata templates live in:

`src/main/templates`

Generated data is written to:

`src/generated/resources`

JUnit tests mirror the production package tree under:

`src/test/java`

Design notes and subsystem documentation belong in:

`docs/`

Treat the following as generated local output:

* `build/`
* `.codex-build/`
* `run/`

Never commit generated local output unless the task explicitly requires generated resources.

---

# Build, Test, and Development Commands

Use the checked-in Gradle wrapper and JDK 21.

On Windows PowerShell:

`.\gradlew.bat compileJava`

Compiles Java sources without performing a full build.

`.\gradlew.bat test`

Runs the JUnit test suite.

`.\gradlew.bat build`

Compiles, tests, packages resources, and produces the mod JAR.

`.\gradlew.bat runClient`

Launches the development Minecraft client.

`.\gradlew.bat runServer`

Launches the dedicated development server.

`.\gradlew.bat runGameTestServer`

Runs registered NeoForge GameTests.

`.\gradlew.bat runData`

Regenerates data under `src/generated/resources`.

Use `clean` only when stale generated output is reasonably suspected.

Do not routinely use:

`.\gradlew.bat clean build`

as normal validation.

Use:

`--refresh-dependencies`

only when dependency resolution, cache corruption, or IDE import problems provide a reason to refresh dependencies.

Do not use dependency refresh as routine troubleshooting.

---

# Codex Validation Strategy

Codex should prefer the smallest validation step that meaningfully exercises the change.

Do not automatically run a full `build` after every edit.

Use this general escalation order:

1. Compile the affected source set.
2. Run the most relevant targeted test.
3. Run relevant subsystem tests.
4. Run the full `test` task when broader regression coverage is useful.
5. Run `build` when packaging, startup, registrations, resources, or integration behavior requires it.
6. Launch Minecraft only when behavior genuinely requires a running Minecraft environment.

For routine Java changes:

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel
```

For Java logic with tests:

```powershell
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel
```

For integration, registration, startup, resource, or packaging changes:

```powershell
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel
```

For data generation:

```powershell
.\gradlew.bat runData -PcodexBuildDir=.codex-build --no-parallel
```

Do not repeatedly rerun an unchanged failing command without first identifying why it failed.

Do not launch:

* `runClient`
* `runServer`
* `runGameTestServer`
* `runData`

unless the requested change actually requires that environment.

For small Java-only changes, prefer compilation and targeted tests over launching Minecraft.

Do not perform several equivalent validation passes simply to increase confidence unless the change is high-risk or deeper validation was specifically requested.

---

# Gradle and NeoForm Execution Safety

NeoForge development uses Gradle, NeoGradle, NeoForm, Minecraft artifacts, transformed JARs, generated JARs, and temporary build files.

These are normal parts of the build system.

## Generated JAR policy

Generated or dependency JARs are build infrastructure.

Examples include files under paths resembling:

```text
build/tmp/neoformruntime/
.codex-build/tmp/neoformruntime/
.gradle/
```

Codex must not treat these JARs as normal editable project files.

Do not manually:

* Patch generated NeoForm JARs.
* Replace generated NeoForm JARs.
* Rename generated NeoForm JARs.
* Delete individual NeoForm output JARs while Gradle is running.
* Modify Minecraft dependency JARs.
* Edit Gradle cache JARs.
* Repackage dependency JARs as a routine workaround.
* Extract entire dependency caches without a specific diagnostic reason.

Gradle, NeoGradle, Java, and NeoForm are expected to read JAR dependencies during normal compilation.

That is not itself a problem.

Codex should access those artifacts indirectly through the normal Gradle build pipeline whenever possible.

## Dependency inspection

When source or API inspection is necessary, prefer:

1. Existing project source.
2. Existing generated sources.
3. Source JARs.
4. NeoForge or library documentation.
5. Targeted dependency inspection.

Do not recursively inspect or unpack the entire Gradle cache merely to understand one API.

---

# Codex Gradle Build Isolation

Routine Codex validation should use:

`-PcodexBuildDir=.codex-build`

Examples:

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel
```

```powershell
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel
```

```powershell
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel
```

The isolated directory reduces conflicts between Codex validation and normal IDE or local development output.

Do not use the normal `build/` directory for routine Codex validation when isolated output is available.

`.codex-build/` is generated local output and must never be committed.

The Gradle build must support the `codexBuildDir` project property.

Equivalent root build behavior should remain available:

```gradle
def codexBuildDir = providers.gradleProperty("codexBuildDir")

if (codexBuildDir.isPresent()) {
    layout.buildDirectory.set(file(codexBuildDir.get()))
}
```

Do not remove or bypass this behavior unless equivalent isolation replaces it.

## Important limitation

Build-directory isolation reduces output collisions.

It does **not** guarantee protection from:

* Windows filesystem locks.
* NTFS permission problems.
* Codex Windows sandbox restrictions.
* Antivirus interference.
* Gradle cache permissions.
* External processes holding files.
* NeoForm-specific temporary-file failures.

Therefore:

A failure inside `.codex-build/` must not automatically be interpreted as a source-code failure.

---

# Gradle and NeoForm Concurrency

Only one Codex-controlled Gradle or Minecraft development process may operate on this repository at a time.

This includes:

* `compileJava`
* `test`
* `build`
* `clean`
* `runClient`
* `runServer`
* `runData`
* `runGameTestServer`
* dependency refreshes
* NeoForm setup or transformation tasks

Do not have multiple subagents run Gradle concurrently.

Do not start another Gradle validation command while one is still active.

Do not have one agent run `runClient` while another runs `build`.

Do not run parallel NeoForm initialization attempts.

Subagents may independently inspect unrelated code, but Gradle and NeoForm validation should normally remain owned by the main agent.

If multiple agents modify independent files, return control to the main agent before repository-wide validation.

---

# Windows Lock and Access-Denied Diagnosis

Do not treat every Windows filesystem failure as the same problem.

## Likely file-lock indicators

Messages such as:

```text
The process cannot access the file because it is being used by another process
```

or explicit Windows sharing violations strongly indicate an active file handle.

Possible holders include:

* Minecraft.
* Gradle.
* Java.
* IntelliJ.
* Antivirus.
* Another terminal.
* Another Codex process.
* Another development tool.

## Access-denied indicators

Errors such as:

```text
java.nio.file.AccessDeniedException
```

or:

```text
Access is denied
```

may indicate:

* NTFS permissions.
* Codex sandbox permissions.
* Ownership or ACL behavior.
* Antivirus or security software.
* A locked file.
* A process attempting an unsupported filesystem operation.

An `AccessDeniedException` alone is not enough evidence to claim that a JAR is locked.

Report it as an access-denied failure unless stronger evidence identifies a file lock.

---

# Windows Gradle and NeoForm Recovery

If Gradle or NeoForm fails because of a generated JAR, temporary JAR, or generated directory:

## Step 1 — Capture the failure

Record:

* The Gradle command.
* The exact exception.
* The exact path.
* The task that failed.

Do not immediately begin destructive recovery.

## Step 2 — Check Codex concurrency

Confirm that Codex does not currently have:

* Another Gradle task running.
* `runClient` running.
* `runServer` running.
* Another subagent performing validation.
* Another NeoForm operation running.

## Step 3 — Confirm isolated output

Routine Codex validation should use:

`-PcodexBuildDir=.codex-build`

If isolated output was not used, retry once with isolated output when appropriate.

## Step 4 — Stop Gradle daemons if a stale handle is plausible

Use:

```powershell
.\gradlew.bat --stop
```

Do this only when relevant.

Do not repeatedly stop and restart Gradle without evidence that it may help.

## Step 5 — Retry the smallest validation once

For example:

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-daemon --no-parallel
```

`--no-daemon` is primarily a recovery or diagnostic option.

It does not need to be used on every successful normal build.

## Step 6 — Stop escalating if access remains blocked

If the same NeoForm or generated-JAR path still fails with `AccessDeniedException` after one reasonable recovery attempt:

* Do not endlessly retry.
* Do not call the code broken without evidence.
* Report validation as blocked by the environment.
* Include the exact path and exception.
* Continue only with safe validation that does not require destructive workarounds.

---

# Forbidden Automatic Recovery Actions

Codex must not automatically perform the following solely to work around a Gradle, NeoForm, JAR, Windows, or sandbox failure:

* Modify NTFS ACLs.
* Run `icacls` to broaden permissions.
* Run `takeown`.
* Give sandbox users Full Control.
* Grant broad access to the user's home directory.
* Grant broad access to a drive.
* Disable Windows Defender.
* Add antivirus exclusions.
* Disable security software.
* Run the entire development environment as Administrator.
* Switch Codex to Full Access.
* Change Codex sandbox mode.
* Delete the global Gradle cache.
* Delete the entire user Gradle directory.
* Kill unrelated Java processes.
* Kill IntelliJ.
* Kill unrelated Minecraft instances.
* Delete arbitrary JAR files from caches.
* Reconstruct the build system manually.
* Build giant hand-written Java classpaths.
* Replace Gradle validation with manual `javac` compilation.

If one of these actions appears necessary, explain:

1. Why it may help.
2. The exact scope required.
3. The risk involved.

Then wait for explicit user approval.

---

# Clean and Cache Policy

`clean` is not normal validation.

Use it only when:

* Outputs are clearly stale.
* Generated state is known to be inconsistent.
* A task explicitly requires clean-build behavior.
* The user requests it.

Do not repeatedly use:

```powershell
.\gradlew.bat clean build
```

as a generic fix.

Do not delete:

```text
.gradle/
```

or the global Gradle cache merely because NeoForm encountered one access error.

Use:

```text
--refresh-dependencies
```

only when dependency corruption or resolution problems are reasonably suspected.

Generated-output deletion should target the smallest relevant project-local scope.

---

# Agent and Subagent Efficiency

Use the main agent for normal:

* Repository exploration.
* Implementation.
* Validation.
* Testing.
* Review.
* Gradle execution.

Do not spawn subagents for:

* Routine feature development.
* Small or medium bug fixes.
* Simple repository exploration.
* Normal Gradle validation.
* One-system refactors.
* Tasks that comfortably fit in one context.

Use subagents when there are genuinely independent analysis workstreams.

Examples:

* Large repository audits.
* Independent performance reviews.
* Security reviews.
* Architecture reviews.
* Large migrations involving separate systems.
* Broad investigations involving unrelated areas.

Subagents should avoid:

* Reading the same files unnecessarily.
* Investigating the same problem.
* Editing overlapping files.
* Producing duplicate findings.
* Running duplicate validation.
* Running Gradle concurrently.
* Running NeoForm concurrently.
* Launching multiple Minecraft development instances.

When subagents are used, assign clear ownership boundaries.

The main agent should normally perform final integration validation.

---

# Validation Rules by Change Type

## Pure Java logic

Prefer:

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel
```

Run relevant JUnit tests when available.

Run the broader test suite when the change could affect multiple logic paths.

## Registries and startup

For changes involving:

* Registries.
* Common setup.
* Event registration.
* Networking registration.
* Config registration.
* Startup logic.
* Resource packaging.

Run:

```powershell
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel
```

when practical.

## Minecraft runtime behavior

For changes involving:

* Worldgen.
* Entities.
* Dimensions.
* Rendering.
* Shaders.
* Fluids.
* Gameplay behavior.
* Actual world interaction.

First run compile or test validation.

Then describe the relevant in-game test.

Launch Minecraft only when practical and necessary.

## Data generation

Run:

```powershell
.\gradlew.bat runData -PcodexBuildDir=.codex-build --no-parallel
```

Review generated-resource diffs afterward.

## Bug fixes

When practical:

* Reproduce the original failure.
* Add a regression test.
* Validate the corrected behavior.
* Avoid unrelated refactoring.

---

# Coding Style & Naming Conventions

Java sources use:

* UTF-8.
* Four-space indentation.
* Opening braces on the declaration line.

Use:

* `PascalCase` for classes.
* `camelCase` for methods and fields.
* `UPPER_SNAKE_CASE` for constants.
* Lowercase package names.
* Lowercase resource identifiers and namespaces.

The mod namespace is:

`wildernessodysseyapi`

Match surrounding NeoForge registration and event-handler patterns.

No automatic formatter is currently configured.

Use Qodana findings as the lint baseline.

Avoid formatting unrelated files.

Avoid changing unrelated whitespace, imports, comments, or declaration ordering.

Keep diffs focused.

---

# Minecraft and NeoForge Guidelines

Follow the NeoForge 1.21.1 conventions already established by the project.

When adding or modifying a Minecraft system:

* Keep registration consistent with existing patterns.
* Keep mod IDs, resource locations, translation keys, config keys, and JSON paths lowercase where required.
* Prefer config-driven behavior where values may require balancing.
* Avoid hardcoding player-facing balance values directly into gameplay classes when config is more appropriate.
* Keep client-only logic separate from common and server logic.
* Never load client-only Minecraft classes from common or dedicated-server code.
* Make logical-side and physical-side assumptions explicit when relevant.
* Be especially careful with ticking, worldgen, chunks, rendering, networking, entities, and event handlers.
* Avoid expensive work every tick unless it is truly necessary.
* Cache, batch, rate-limit, precompute, or use dirty-state updates when practical.
* Avoid unnecessary chunk loading.
* Avoid repeated large-area world scans.
* Avoid avoidable allocations in hot tick or render loops.

Prefer supported NeoForge hooks and APIs over invasive modifications.

Use mixins only when an appropriate supported hook cannot reasonably satisfy the requirement.

---

# Event Guidelines

When using events:

* Explain what triggers important handlers and why the handler belongs there.
* Avoid duplicate registration.
* Make server/client checks explicit when needed.
* Avoid broad handlers on high-frequency events unless necessary.
* Keep expensive work out of per-tick events when possible.
* Register lifecycle events on the correct event bus.
* Follow existing project patterns before introducing new event architecture.

---

# Mixin Guidelines

When using mixins:

* Keep them narrow and targeted.
* Explain why the mixin is required.
* Explain why an event, NeoForge API, config option, or supported hook is insufficient.
* Avoid fragile injections where safer hooks exist.
* Avoid broad method overwrites unless absolutely necessary.
* Prefer targeted injections.
* Document assumptions about targets, ordinals, locals, and invocation order where relevant.
* Consider compatibility with other mods modifying the same path.
* Keep unrelated behavior in separate mixins.

---

# Networking Guidelines

When using networking:

* Use descriptive packet names.
* Validate server-side data.
* Never trust client input merely because it came through a registered packet.
* Validate IDs, ranges, positions, permissions, dimensions, and player state where relevant.
* Avoid packets every tick unless truly required.
* Batch or rate-limit synchronization where practical.
* Explain what important packets synchronize and when they are sent.
* Keep clientbound and serverbound responsibilities clear.
* Avoid duplicating authoritative gameplay state on the client.
* Treat the server as authoritative for gameplay.

---

# Worldgen Guidelines

World generation can have major performance and compatibility impacts.

When changing worldgen:

* Follow existing project architecture.
* Avoid unnecessary chunk lookups.
* Avoid forcing neighboring chunks to load.
* Be careful with biome queries, structures, heightmaps, fluids, feature ordering, and placement.
* Prefer deterministic behavior when seed and position should control results.
* Keep generation-time logic separate from runtime ticking.
* Avoid calculating at runtime what could safely have been determined during generation.
* Prefer data-driven registration where appropriate.
* Validate resource locations.
* Validate generated JSON paths.
* Consider compatibility with other terrain, biome, and structure mods.

Large worldgen systems should include documentation under `docs/` when the architecture is not obvious.

---

# Performance Guidelines

Performance-sensitive areas include:

* Tick handlers.
* Entity AI.
* Worldgen.
* Chunk access.
* Rendering.
* Networking.
* Attachments or capability synchronization.
* Large collections.
* Data scanning.
* File I/O.
* Pathfinding.
* Repeated registry or resource lookups.

For performance-sensitive systems:

* Identify the hot path before optimizing.
* Prefer eliminating unnecessary work over merely making unnecessary work faster.
* Prefer event-driven or dirty-state updates over polling where appropriate.
* Cache stable values where safe.
* Do not cache values with unclear invalidation rules.
* Rate-limit expensive checks.
* Spread large workloads across ticks when latency allows.
* Avoid blocking the main server thread with file or network I/O.
* Avoid unbounded collections.
* Remove stale state when worlds, players, chunks, or entities unload.
* Avoid premature micro-optimization that makes maintenance significantly harder.

Any new per-tick system should clearly justify why it needs that frequency.

---

# Threading & Async Safety

Minecraft state is generally unsafe to modify from arbitrary background threads.

When using asynchronous work:

* Do not directly modify world, entity, player, registry, or other main-thread-owned Minecraft state from unsafe threads.
* Perform expensive pure computation asynchronously only when inputs can safely be captured.
* Schedule Minecraft state changes back onto the appropriate game thread.
* Avoid uncontrolled thread pools.
* Reuse project executors or standard APIs when available.
* Ensure asynchronous work cannot continue indefinitely after shutdown or world unload.
* Handle lifecycle cleanup.
* Be careful with concurrent collections.

Do not introduce asynchronous behavior merely because it sounds faster.

---

# Config Guidelines

Prefer config-driven values when server owners or pack developers may reasonably need to tune behavior.

Examples:

* Cooldowns.
* Durations.
* Spawn chances.
* Distances.
* Feature toggles.
* Performance budgets.
* Rates.
* Damage.
* Limits.

Do not create config entries for internal constants users should never need to change.

Validate config values where appropriate.

Document units including:

* Ticks.
* Blocks.
* Seconds.
* Percentages.
* Probabilities.

Synchronize server-authoritative settings safely when clients require them.

---

# Documentation and Code Comments

Comments should explain intent, constraints, or non-obvious reasoning.

Good:

```java
// Tracks cloak cooldown separately from the active cloak timer so the player
// cannot immediately re-trigger the ability after it ends.
```

Avoid comments that merely restate code.

Poor:

```java
// Set cooldown to 100.
```

Use Javadocs for API-facing classes and methods.

Example:

```java
/**
 * Handles server-side cloak state for players.
 *
 * <p>This class owns cloak activation, duration tracking, and cooldown timing.
 * Client rendering should read synchronized state instead of duplicating
 * authoritative gameplay logic.</p>
 */
```

For substantial systems, document:

* What the system does.
* Which subsystem owns authoritative state.
* What runs on the server.
* What runs on the client.
* How state is synchronized.
* Important lifecycle behavior.
* Performance considerations.
* Testing strategy.

Avoid documentation that only repeats method or class names.

---

# Testing Guidelines

Tests use JUnit Jupiter 5.

Test classes should use:

`*Test`

Mirror the production package structure.

Use behavior-focused test names such as:

`ignoresEmptyAllocations`

Add unit tests for isolated Java logic.

Use GameTests for behavior requiring:

* A loaded Minecraft world.
* Blocks.
* Entities.
* Structures.
* Game rules.
* Server lifecycle.
* Other Minecraft-specific behavior.

There is no declared coverage threshold.

Every bug fix should include a regression test when practical.

Regression tests should:

* Reproduce the old failure.
* Assert the corrected behavior.
* Avoid unrelated implementation details.

If a useful automated test cannot reasonably be added:

* Explain why.
* Provide manual validation steps.

Do not create meaningless tests merely to increase the test count.

---

# Feature Development Guidelines

When adding a feature, separate responsibilities where practical:

* Registration.
* Config.
* Runtime logic.
* Data/resources.
* Networking.
* Client behavior.
* Tests or manual validation.
* Documentation.

For larger features, phased implementation is preferred:

1. Minimal compile-safe skeleton.
2. Core behavior.
3. Config and balancing.
4. Client visuals, audio, or UI.
5. Tests, documentation, compatibility, and polish.

Keep phases usable and compile-safe when practical.

Do not invent a new architecture when the project already has a suitable pattern.

Do not add unnecessary dependencies.

Avoid placing an entire major feature in one class.

Prefer composition and clear responsibility boundaries.

---

# Compatibility Guidelines

Wilderness Odyssey may run alongside many other mods.

When modifying vanilla or NeoForge behavior:

* Prefer additive behavior over destructive replacement when possible.
* Avoid assuming Wilderness Odyssey is the only mod modifying a system.
* Avoid hard dependencies on optional mods unless intentionally required.
* Guard optional integrations safely.
* Keep compatibility logic separate from core logic.
* Avoid directly referencing optional mod classes unless the dependency is loaded.
* Be especially careful with mixins, worldgen, rendering, shaders, fluids, networking, and registries.

Mention meaningful compatibility concerns in the final response.

---

# Resource and Data Guidelines

For JSON, textures, models, shaders, tags, loot tables, recipes, structures, and related resources:

* Follow Minecraft resource naming conventions.
* Keep paths lowercase where required.
* Use the `wildernessodysseyapi` namespace.
* Do not create duplicate resource IDs.
* Keep generated resources separate from handwritten resources.
* Review generated diffs after data generation.
* Avoid manually editing generated output when a generator owns it.

For player-facing text:

* Prefer translation keys over hardcoded display strings when appropriate.

---

# Error Handling & Logging

Use logging for information useful during development or diagnosis.

Do not spam logs from:

* Tick handlers.
* Render loops.
* Entity AI.
* High-frequency networking.
* Worldgen inner loops.

Use appropriate log levels.

Do not log secrets or sensitive data.

Do not silently swallow exceptions unless the failure is intentionally recoverable and properly handled.

Error messages should identify enough context to locate the failing subsystem.

Avoid repeatedly printing full stack traces for expected recoverable conditions.

---

# Security and Secrets

Never commit:

* API keys.
* Discord bot tokens.
* Webhook URLs.
* Credentials.
* Authentication tokens.
* Private configuration.
* Sensitive environment files.
* Crash logs containing sensitive personal paths or credentials.
* Files from `run/`.
* IDE-specific secrets.

If a feature requires a token or external service:

* Use an environment variable or ignored local configuration.
* Keep secrets out of source code.
* Document setup without including real credentials.
* Never use placeholders that resemble active secrets.

Do not expose sensitive server-authoritative information to clients unless necessary.

---

# Git and Diff Hygiene

Keep changes focused on the requested task.

Before finishing:

* Review the final diff.
* Check for unrelated modifications.
* Check for generated files that should not be committed.
* Check for temporary diagnostics.
* Check for debug logging.
* Check for commented-out experiments.
* Check for temporary assets or test files.
* Check for machine-specific paths.
* Check for secrets.

Avoid large formatting-only diffs unless formatting was requested.

Do not modify unrelated files simply because they could be improved.

---

# Commit & Pull Request Guidelines

Use concise lowercase commit summaries.

Prefer imperative, specific descriptions.

Examples:

* `add cloak cooldown config`
* `fix riftfall spawn check`
* `document worldgen placement rules`
* `optimize weather tick scheduling`
* `add lab keycard validation`

Keep commits focused.

Pull requests should explain:

* What changed.
* Player-facing impact.
* Relevant issues.
* Important architecture decisions.
* Validation commands.
* Validation results.
* Useful screenshots or logs for UI, rendering, worldgen, startup, or crash fixes.

Do not include generated local output.

---

# Large Refactors

Do not perform large refactors unless required by the requested task or specifically requested.

Before a large refactor:

* Inspect major callers.
* Identify API compatibility risks.
* Identify behavioral compatibility risks.
* Explain the intended architecture.
* Stage the work when practical.
* Preserve behavior unless behavior changes are intentional.
* Run broader regression validation afterward.

Do not combine an unrelated feature with a large refactor.

---

# Repository Audit Tasks

When asked to audit the repository, inspect for:

* Duplicate or overlapping features.
* Incomplete implementations.
* Dead code.
* TODO or FIXME markers.
* Potential crashes.
* Incorrect side handling.
* Registry problems.
* Event registration issues.
* Networking trust problems.
* Fragile mixins.
* Performance hot spots.
* Unbounded ticking work.
* Memory leaks.
* Stale caches.
* Chunk-loading risks.
* Worldgen performance risks.
* Thread-safety problems.
* Duplicate resource IDs.
* Unused configs.
* Missing validation.
* Missing regression tests.
* Architecture inconsistencies.
* Compatibility risks.
* Security or secret exposure.

Prioritize findings by practical impact.

Separate:

* Confirmed problems.
* Likely problems.
* Possible concerns.

Do not make speculative fixes during an audit unless the task also asks for fixes.

---

# Final Response Format for AI Agents

After completing a coding task, respond with the following sections.

## 1. What changed

List meaningful files and systems changed.

Do not enumerate every trivial edit.

## 2. How it works

Explain relevant:

* Classes.
* Methods.
* Events.
* Registries.
* Configs.
* Resources.
* Networking.
* Architecture.

Keep this focused on what is necessary to understand the implementation.

## 3. How to test

Provide exact relevant commands.

Include in-game testing steps when applicable.

## 4. Validation

State exactly which commands were actually run.

For example:

```powershell
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel
```

Report each command as:

* Passed.
* Failed because of code or tests.
* Blocked by the environment.
* Not run, with the reason.

If NeoForm or Gradle is blocked by a Windows or Codex sandbox access failure, explicitly identify it as an environment-blocked validation result unless evidence shows that project code caused the failure.

Never claim validation passed when a command was not actually executed successfully.

## 5. Notes

Mention relevant:

* Limitations.
* Assumptions.
* Compatibility concerns.
* Environment problems.
* Deferred work.
* Useful follow-up work.

Keep the final response concise enough to review quickly.
