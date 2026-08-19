# AGENTS.md

# Repository Guidelines

## AI Coding Expectations

When working in this repository, act like a careful Minecraft/NeoForge developer, not just a code generator.

Before making large changes:

* Inspect the existing package structure and follow the project’s current patterns.
* Briefly explain the implementation plan before editing files.
* Prefer small, modular changes over giant all-in-one classes.
* Do not rewrite unrelated systems unless specifically asked.
* Reuse existing architecture, helpers, registries, configs, and patterns when appropriate.
* Ask for clarification only when a requirement is truly blocked. Otherwise, make a reasonable implementation choice and explain the assumption.
* Avoid speculative refactors that are not necessary to complete the requested task.

When writing code:

* Add short comments above major sections explaining what that section does.
* Use Javadocs for public classes, public methods, registries, config classes, event handlers, capabilities, mixins, and API-facing systems.
* Explain why Minecraft/NeoForge systems are being used, especially registries, event buses, data generation, capabilities, mixins, networking, worldgen, and config syncing.
* Do not over-comment obvious lines such as simple assignments, getters, setters, or basic conditionals.
* Keep comments useful for a future developer who did not write the system.
* Prefer readable code over clever code.
* Keep methods focused and avoid unnecessarily large classes.
* Avoid adding abstractions unless they solve a real project need.
* Do not introduce new dependencies when the existing stack can reasonably handle the task.

When finishing a task:

* Summarize what files changed.
* Explain what each new class or major method does.
* Explain how to test the feature in-game when applicable.
* Mention limitations, assumptions, or follow-up work.
* Run the relevant Gradle task when possible and fix compile errors before calling the task complete.
* Prefer targeted validation before broad validation.
* Do not perform unnecessary recovery work or alternate build pipelines when the normal project build system is sufficient.

---

## Project Structure & Module Organization

Production code lives in:

`src/main/java/com/thunder/wildernessodysseyapi`

Organize code into feature packages such as:

* `worldgen`
* `cloak`
* `ai`
* `riftfall`

Keep new code with the feature it supports rather than creating broad utility packages.

Examples:

* Cloaking systems belong in `cloak`.
* Riftfall systems belong in `riftfall`.
* AI companion systems belong in `ai`.
* World generation, structures, biome logic, placement, and data generation helpers belong in `worldgen`.

Avoid creating generic packages such as `util`, `manager`, or `helper` unless the functionality is genuinely shared by several independent systems.

Feature-specific helpers should stay inside their feature package.

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

Never commit generated local output unless the task specifically requires generated resources.

---

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper and JDK 21.

On Windows PowerShell:

* `.\gradlew.bat build` — compile, test, and produce the mod JAR under `build/libs/`.
* `.\gradlew.bat test` — run the JUnit test suite.
* `.\gradlew.bat compileJava` — compile Java sources without performing a full build.
* `.\gradlew.bat runClient` — launch a development Minecraft client.
* `.\gradlew.bat runServer` — launch the dedicated development server without a GUI.
* `.\gradlew.bat runGameTestServer` — execute registered NeoForge GameTests.
* `.\gradlew.bat runData` — regenerate data into `src/generated/resources`.

Use `clean` only when stale outputs are reasonably suspected.

Use:

`--refresh-dependencies`

only when dependency resolution, corrupted caches, or IDE imports indicate that dependency refresh is actually needed.

Do not routinely combine `clean` with every Gradle invocation.

---

## Codex Validation & Usage Efficiency

Prefer the smallest validation step that gives meaningful confidence in the change.

Do not automatically run a full `build` after every edit.

Use this validation order when appropriate:

1. Compile the affected source set or run the most targeted relevant test.
2. Run the affected test class or subsystem tests.
3. Run the full `test` task when broader regression coverage is useful.
4. Run the full `build` when changes affect registration, startup, resources, packaging, multiple systems, or final integration validation.

Examples:

For a small Java implementation change:

`.\gradlew.bat compileJava -PcodexBuildDir=.codex-build`

For pure Java logic with tests:

`.\gradlew.bat test -PcodexBuildDir=.codex-build`

For registration, startup, integration, or packaging changes:

`.\gradlew.bat build -PcodexBuildDir=.codex-build`

For data generation:

`.\gradlew.bat runData -PcodexBuildDir=.codex-build`

Do not repeatedly rerun an unchanged failing command without first determining why it failed.

Do not launch:

* `runClient`
* `runServer`
* `runGameTestServer`
* `runData`

unless the requested change actually requires that environment.

For small Java-only changes, prefer compilation and targeted tests over launching Minecraft.

Avoid unnecessary diagnostic commands when the previous validation already establishes the relevant result.

Do not perform multiple equivalent validation passes simply to increase confidence unless the task is high risk or the user specifically requests deeper validation.

---

## Codex Gradle Build Isolation

Windows may lock generated JAR files that are being used by Minecraft, IntelliJ, Gradle, antivirus software, another Java process, or another development tool.

Routine Codex validation should therefore use the isolated Codex build directory:

`-PcodexBuildDir=.codex-build`

Examples:

`.\gradlew.bat compileJava -PcodexBuildDir=.codex-build`

`.\gradlew.bat test -PcodexBuildDir=.codex-build`

`.\gradlew.bat build -PcodexBuildDir=.codex-build`

Do not use the normal `build/` directory for routine Codex compile and test validation when isolated output is available.

The `.codex-build/` directory is generated local output and must never be committed.

The Gradle build must support the `codexBuildDir` project property.

The root build configuration should contain equivalent behavior to:

```gradle
def codexBuildDir = providers.gradleProperty("codexBuildDir")

if (codexBuildDir.isPresent()) {
    layout.buildDirectory.set(file(codexBuildDir.get()))
}
```

Do not remove or bypass this behavior unless the project architecture changes and an equivalent isolated validation mechanism replaces it.

---

## Windows Gradle Lock Recovery

If Gradle reports that a generated JAR, build directory, or other generated output is locked:

1. Confirm that the failure is actually a file-lock problem rather than a compilation, dependency, or test failure.

2. Confirm that the command is using:

   `-PcodexBuildDir=.codex-build`

3. If it was not using isolated output, retry once using the isolated Codex build directory.

4. Do not repeatedly rerun the same locked command unchanged.

5. Do not immediately extract dependency JARs.

6. Do not manually construct large Java classpaths.

7. Do not replace Gradle validation with manual `javac` compilation unless genuinely necessary as a last-resort diagnostic.

8. Do not kill unrelated Java processes automatically.

9. Do not delete global Gradle caches simply because a generated project JAR is locked.

10. Do not disable Gradle performance features globally as a first-line workaround.

11. If the isolated Gradle build is also blocked, identify and report the exact locked file.

12. Continue with safe validation that does not require inventing a separate build system.

Manual dependency extraction, alternate compilation pipelines, or custom classpath reconstruction are last-resort diagnostics and should not become the normal response to a Windows file lock.

The goal is to avoid turning a simple validation problem into a large diagnostic workflow.

---

## Agent and Subagent Efficiency

Use the main agent for normal repository exploration, implementation, validation, testing, and review.

Do not spawn subagents for:

* Routine feature development.
* Small or medium bug fixes.
* Simple repository exploration.
* Normal Gradle validation.
* One-system refactors.
* Tasks that can reasonably be completed in one context.

Use subagents when the task contains clearly independent workstreams where parallel analysis provides meaningful value.

Examples include:

* Large repository audits.
* Independent security, performance, architecture, or correctness reviews.
* Large migrations involving separate modules.
* Broad investigations involving multiple unrelated systems.
* Tasks where several independent areas of the repository can be inspected without overlapping work.

Avoid having several agents:

* Investigate the same problem.
* Read the same files unnecessarily.
* Edit overlapping files.
* Run duplicate validation.
* Produce redundant summaries.

Prefer one focused agent over multiple agents when parallelism does not substantially improve the result.

---

## Validation Rules by Change Type

When changing pure Java logic:

* Prefer `compileJava`.
* Run relevant JUnit tests when available.
* Run the full `test` suite when the change could affect multiple logic paths.

When changing registries, startup logic, event registration, networking registration, configs, or common setup:

* Run `build`.

When changing worldgen, entities, dimensions, rendering, shaders, gameplay behavior, or systems requiring an actual Minecraft environment:

* Run appropriate compile/test validation first.
* Describe the relevant `runClient` or `runServer` manual test procedure.
* Launch the game only when practical and necessary.

When changing data generation:

* Run `runData`.
* Review the resulting generated resource diff.

When fixing a bug:

* Add a regression test when practical.
* Validate the original failure condition when possible.
* Avoid unrelated refactoring during the bug fix.

---

## Coding Style & Naming Conventions

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

Keep diffs tidy and use Qodana findings as the lint baseline.

Avoid formatting unrelated files.

Avoid changing whitespace, imports, comments, or ordering in unrelated code unless required.

Keep commits and diffs focused on the requested task.

---

## Minecraft and NeoForge Guidelines

Follow the NeoForge 1.21.1 conventions already established by the project.

When adding or editing a Minecraft system:

* Keep registration code consistent with existing registry patterns.
* Keep mod IDs, resource locations, translation keys, config keys, and JSON paths lowercase when required.
* Prefer config-driven behavior when values may require balancing.
* Avoid hardcoding player-facing balance values directly into gameplay classes when a config is more appropriate.
* Keep client-only logic separated from common/server logic.
* Avoid loading client-only Minecraft classes from common/server code.
* Make logical-side and physical-side assumptions explicit when relevant.
* Be careful with worldgen, ticking, chunk operations, rendering, and event handlers because they can significantly affect performance.
* Avoid expensive work every tick unless it is genuinely necessary.
* Cache, batch, rate-limit, precompute, or use dirty-state updates when practical.
* Avoid unnecessary chunk loading.
* Avoid repeatedly scanning large world areas.
* Avoid allocations in hot tick/render loops when a reusable or cached approach is reasonable.

Prefer existing NeoForge hooks and APIs over invasive techniques.

Use mixins only when an appropriate supported hook does not exist or cannot satisfy the requirement.

---

## Event Guidelines

When using events:

* Add a short comment explaining what triggers the event and why the handler belongs there.
* Avoid duplicate event registrations.
* Make server/client side checks explicit when required.
* Avoid subscribing broad handlers to high-frequency events unless necessary.
* Keep expensive work out of per-tick events when possible.
* Ensure lifecycle events are registered on the correct event bus.
* Follow existing project patterns before introducing new event architecture.

---

## Mixin Guidelines

When using mixins:

* Keep mixins narrow and targeted.
* Add comments explaining why the mixin is necessary.
* Explain why an event, config option, NeoForge API, or other supported hook is insufficient.
* Avoid fragile injections when a safer hook exists.
* Avoid broad method overwrites unless absolutely necessary.
* Prefer targeted injections with clear assumptions.
* Document assumptions about target methods, ordinals, locals, or invocation order when applicable.
* Consider compatibility with other mods modifying the same code path.
* Avoid placing unrelated behavior into one mixin.

---

## Networking Guidelines

When using networking:

* Keep packet names clear and descriptive.
* Validate server-side data.
* Never trust client input simply because it came through a registered packet.
* Validate IDs, ranges, positions, permissions, dimensions, and player state when relevant.
* Avoid sending packets every tick unless genuinely required.
* Batch or rate-limit synchronization when practical.
* Add comments explaining what each packet synchronizes and when it is sent.
* Keep clientbound and serverbound responsibilities clear.
* Avoid duplicating authoritative state on the client.
* Treat the server as authoritative for gameplay state.

---

## Worldgen Guidelines

World generation changes can have large performance and compatibility impacts.

When changing worldgen:

* Follow existing project worldgen architecture.
* Avoid unnecessary chunk lookups during generation.
* Avoid forcing neighboring chunks to load.
* Be careful with biome queries, structure placement, heightmaps, fluids, and feature ordering.
* Prefer deterministic behavior when world seed and position should control results.
* Keep generation-time logic separate from runtime ticking systems.
* Avoid doing work during every world tick that could have been determined during generation.
* Use data-driven registration where practical.
* Validate resource locations and generated JSON paths.
* Consider compatibility with other terrain, biome, and structure mods.

Large worldgen systems should include documentation under `docs/` when their architecture is not obvious.

---

## Performance Guidelines

Performance-sensitive code includes:

* Tick handlers.
* Entity AI.
* Worldgen.
* Chunk access.
* Rendering.
* Networking.
* Capability or attachment synchronization.
* Large collections.
* Data scanning.
* File I/O.
* Pathfinding.
* Repeated registry/resource lookups.

For performance-sensitive systems:

* Identify the hot path before optimizing.
* Avoid unnecessary work rather than only making expensive work slightly faster.
* Prefer event-driven or dirty-state updates over polling when appropriate.
* Cache stable values where safe.
* Avoid caching values whose invalidation rules are unclear.
* Rate-limit expensive checks.
* Spread large workloads across ticks when latency allows.
* Avoid blocking the main server thread with file or network I/O.
* Avoid unbounded collections.
* Remove stale cached state when worlds, players, chunks, or entities unload.
* Avoid premature micro-optimization that makes the code much harder to maintain.

Any new per-tick system should clearly justify why it must run at that frequency.

---

## Threading & Async Safety

Minecraft state is generally not safe to modify from arbitrary background threads.

When using asynchronous work:

* Do not modify world, entity, player, registry, or other main-thread-owned Minecraft state from an unsafe thread.
* Perform expensive pure computation asynchronously only when its inputs can safely be captured.
* Schedule Minecraft state changes back onto the appropriate game thread.
* Avoid creating uncontrolled thread pools.
* Reuse project executors or standard APIs when available.
* Ensure async work cannot continue indefinitely after shutdown or world unload.
* Be careful with concurrent collections and lifecycle cleanup.

Do not add asynchronous behavior purely because it sounds faster.

---

## Config Guidelines

Prefer config-driven values when server owners or pack developers may reasonably want to tune behavior.

Examples include:

* Cooldowns.
* Durations.
* Spawn chances.
* Distances.
* Feature toggles.
* Performance budgets.
* Rates.
* Damage values.
* Limits.

Do not create config options for internal constants that users should never need to change.

Validate config values where appropriate.

Document units such as:

* Ticks.
* Blocks.
* Seconds.
* Percentages.
* Probabilities.

Keep server-authoritative settings synchronized safely when clients require them.

---

## Documentation and Code Comments

This project should remain understandable to future contributors.

Use comments that explain intent.

Good:

```java
// Tracks cloak cooldown separately from the active cloak timer so the player
// cannot instantly re-trigger the ability after it ends.
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
 * Client rendering should read synced state instead of duplicating this logic.</p>
 */
```

For substantial new systems, include a short class-level or documentation explanation covering:

* What the system does.
* Which class or subsystem owns the state.
* What runs on the server.
* What runs on the client.
* How state is synchronized.
* Important lifecycle behavior.
* Performance considerations when relevant.
* How the system is tested or manually verified.

Avoid creating documentation that only repeats method names or obvious implementation details.

---

## Testing Guidelines

Tests use JUnit Jupiter 5.

Name test classes:

`*Test`

Mirror the source package structure.

Use behavior-focused test method names such as:

`ignoresEmptyAllocations`

Add unit tests for isolated Java logic.

Use GameTests for behavior requiring:

* A loaded Minecraft world.
* Blocks.
* Entities.
* Structures.
* Game rules.
* Server lifecycle.
* Other environment-specific behavior.

There is no declared coverage threshold.

Every bug fix should include a regression test where practical.

When adding a regression test:

* Reproduce the old failure condition.
* Assert the corrected behavior.
* Avoid testing unrelated implementation details.

Run relevant tests before considering the task complete.

If a useful automated test cannot reasonably be added:

* Explain why.
* Provide manual validation steps.

Do not create meaningless tests solely to increase test count.

---

## Feature Development Guidelines

When adding a feature, split it into clear responsibilities when practical:

* Registration.
* Config.
* Runtime logic.
* Data/resources.
* Networking.
* Client behavior.
* Tests or manual validation.
* Documentation.

For larger features, prefer phased implementation:

1. Minimal compile-safe skeleton.
2. Core gameplay behavior.
3. Config and balancing.
4. Client visuals, audio, or UI.
5. Tests, documentation, compatibility, and polish.

Keep each phase usable and compile-safe when practical.

Do not invent a new architecture if the existing project already has a clear suitable pattern.

Do not add unnecessary dependencies.

Avoid placing an entire major feature in one class.

Prefer composition and clearly separated responsibilities.

---

## Compatibility Guidelines

Wilderness Odyssey may run alongside many other mods.

When modifying vanilla or NeoForge behavior:

* Prefer additive behavior over destructive replacement when possible.
* Avoid assuming Wilderness Odyssey is the only mod modifying a system.
* Avoid hard dependencies on optional mods unless specifically intended.
* Guard optional integrations safely.
* Keep compatibility logic isolated from core logic.
* Avoid directly referencing optional mod classes unless the dependency is known to be loaded.
* Be especially careful with mixins, worldgen, rendering, shaders, fluids, networking, and registries.

If a change may create compatibility concerns, mention them in the final response.

---

## Resource and Data Guidelines

For JSON, textures, models, shaders, tags, loot tables, recipes, structures, and other resources:

* Follow Minecraft resource naming conventions.
* Keep paths lowercase where required.
* Use the `wildernessodysseyapi` namespace.
* Do not create duplicate resource IDs.
* Keep generated resources separate from handwritten resources.
* Review generated diffs after running data generation.
* Avoid manually editing generated output when the generator should own it.

When adding player-facing text:

* Prefer translation keys over hardcoded display strings when appropriate.

---

## Error Handling & Logging

Use logging for information that is useful during development or diagnosis.

Do not spam logs from:

* Tick handlers.
* Render loops.
* Entity AI.
* High-frequency networking.
* Worldgen inner loops.

Use appropriate log levels.

Avoid logging sensitive information.

Do not silently swallow exceptions unless failure is intentionally recoverable and properly handled.

Error messages should provide enough context to diagnose the failing subsystem.

Avoid printing full stack traces repeatedly for an expected recoverable condition.

---

## Security and Secrets

Never commit:

* API keys.
* Discord bot tokens.
* Webhook URLs.
* Local server IPs or credentials.
* Private configs.
* Authentication tokens.
* Crash logs containing personal paths or sensitive information.
* Files from `run/`.
* IDE-specific secrets.
* Environment files containing credentials.

If a feature needs a token or external service:

* Use an environment variable or ignored local configuration file.
* Keep secrets out of source code.
* Document setup without including real credentials.
* Never include placeholder values that look like real active secrets.

Do not expose server-authoritative sensitive information to clients unless necessary.

---

## Git and Diff Hygiene

Keep changes focused on the requested task.

Before finishing:

* Review the final diff.
* Check for accidental unrelated modifications.
* Check for generated files that should not be committed.
* Check for debug logging or temporary diagnostics.
* Check for commented-out experimental code.
* Check for temporary assets or test files.
* Check for local paths or machine-specific values.
* Check for secrets.

Avoid large formatting-only diffs unless formatting was specifically requested.

Do not modify unrelated files simply because they could be improved.

---

## Commit & Pull Request Guidelines

Recent commits use concise lowercase summaries.

Keep that style while making the scope specific and using an imperative verb.

Examples:

* `add cloak cooldown config`
* `fix riftfall spawn check`
* `document worldgen placement rules`
* `optimize weather tick scheduling`
* `add lab keycard validation`

Keep each commit focused.

Pull requests should explain:

* What changed.
* The player-facing impact.
* Relevant issues.
* Important architecture decisions.
* Validation commands.
* Test results.
* Screenshots or logs for rendering, UI, world-generation, startup, or crash fixes when useful.

Do not include generated local output in commits.

---

## Large Refactors

Do not perform large refactors unless they are necessary for the requested task or specifically requested.

Before a large refactor:

* Inspect all major callers.
* Identify API or behavior compatibility risks.
* Explain the intended architecture.
* Keep the refactor staged when practical.
* Preserve behavior unless behavior changes are intentional.
* Run broader regression validation afterward.

Avoid combining a large refactor with an unrelated feature implementation.

---

## Repository Audit Tasks

When asked to audit the repository, inspect for:

* Duplicate or overlapping features.
* Incomplete implementations.
* Dead code.
* TODO/FIXME markers.
* Potential crashes.
* Incorrect side handling.
* Registry problems.
* Event registration issues.
* Networking trust problems.
* Mixins with fragile injections.
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

Do not make speculative changes during an audit unless the task also requests fixes.

Separate confirmed problems from possible concerns.

---

## Final Response Format for AI Agents

After completing a coding task, respond with:

### 1. What changed

* List the important files and systems changed.
* Keep the summary focused on meaningful changes rather than every trivial edit.

### 2. How it works

* Explain the main classes, methods, events, registries, configs, resources, networking, or other architecture involved.

### 3. How to test

* Give exact relevant Gradle commands.
* Include in-game testing steps when applicable.

### 4. Validation

State exactly which validation commands were run.

Example:

`.\gradlew.bat test -PcodexBuildDir=.codex-build`

Report whether each command:

* Passed.
* Failed.
* Was blocked by the environment.
* Was not run and why.

Never claim validation passed when the command was not actually executed.

### 5. Notes

Mention:

* Limitations.
* Assumptions.
* Compatibility concerns.
* Deferred work.
* Useful follow-up ideas.

Keep the final response concise enough to review quickly while still providing the information needed to understand and verify the change.
