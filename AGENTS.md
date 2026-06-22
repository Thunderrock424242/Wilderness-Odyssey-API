# Repository Guidelines

## AI Coding Expectations

When working in this repository, act like a careful Minecraft/NeoForge developer, not just a code generator.

Before making large changes:

* Inspect the existing package structure and follow the project’s current patterns.
* Briefly explain the implementation plan before editing files.
* Prefer small, modular changes over giant all-in-one classes.
* Do not rewrite unrelated systems unless specifically asked.
* Ask for clarification only when a requirement is truly blocked; otherwise make a reasonable implementation choice and explain it.

When writing code:

* Add short comments above major sections explaining what that section does.
* Use Javadocs for public classes, public methods, registries, config classes, event handlers, capabilities, mixins, and API-facing systems.
* Explain why Minecraft/NeoForge systems are being used, especially registries, event buses, data generation, capabilities, mixins, networking, worldgen, and config syncing.
* Do not over-comment obvious lines like simple assignments, getters, or basic conditionals.
* Keep comments useful for a future developer who did not write the system.
* Prefer readable code over clever code.

When finishing a task:

* Summarize what files changed.
* Explain what each new class or major method does.
* Explain how to test the feature in-game.
* Mention any limitations, assumptions, or follow-up work.
* Run the relevant Gradle task when possible and fix compile errors before calling the task complete.

## Project Structure & Module Organization

Production code lives in `src/main/java/com/thunder/wildernessodysseyapi`, organized into feature packages such as `worldgen`, `cloak`, `ai`, and `riftfall`.

Keep new code with the feature it supports rather than creating broad utility packages. For example:

* Cloaking systems belong in `cloak`.
* Riftfall systems belong in `riftfall`.
* AI companion systems belong in `ai`.
* World generation, structures, biome logic, placement, and data generation helpers belong in `worldgen`.

Minecraft assets, data-pack JSON, shaders, YAML, and defaults belong in `src/main/resources`.

Mod metadata templates live in `src/main/templates`.

Generated data is written to `src/generated/resources`.

JUnit tests mirror the production package tree under `src/test/java`.

Design notes and subsystem documentation belong in `docs/`.

Treat `build/` and `run/` as generated local output. Never commit generated local output unless the task specifically requires generated resources.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper and JDK 21.

On Windows PowerShell:

* `.\gradlew.bat build` — compile, test, and produce the mod JAR under `build/libs/`.
* `.\gradlew.bat test` — run the JUnit test suite.
* `.\gradlew.bat runClient` — launch a development Minecraft client.
* `.\gradlew.bat runServer` — launch the dedicated development server without a GUI.
* `.\gradlew.bat runGameTestServer` — execute registered NeoForge GameTests.
* `.\gradlew.bat runData` — regenerate data into `src/generated/resources`.

Use `clean` only when stale outputs are suspected.

Use `--refresh-dependencies` only when dependency resolution or IDE imports fail.

When changing gameplay code, run the most relevant task:

* For pure Java logic, run `.\gradlew.bat test`.
* For registry, startup, or common setup changes, run `.\gradlew.bat build`.
* For worldgen, entities, dimensions, rendering, or in-game behavior, also describe how to test with `runClient`.
* For data generation changes, run `.\gradlew.bat runData`.

## Coding Style & Naming Conventions

Java sources use UTF-8, four-space indentation, and opening braces on the declaration line.

Use:

* `PascalCase` for classes.
* `camelCase` for methods and fields.
* `UPPER_SNAKE_CASE` for constants.
* Lowercase package names.
* Lowercase resource identifiers and namespaces.

The mod namespace is `wildernessodysseyapi`.

Match surrounding NeoForge registration and event-handler patterns.

No automatic formatter is configured, so keep diffs tidy and use Qodana findings as the lint baseline.

Avoid broad utility classes unless the same helper is clearly needed by multiple systems. Feature-specific helpers should stay inside that feature package.

## Minecraft and NeoForge Guidelines

Follow NeoForge 1.21.1 conventions already used by the project.

When adding or editing a Minecraft system:

* Keep registration code consistent with existing registry patterns.
* Keep mod IDs, resource locations, translation keys, and JSON paths lowercase.
* Prefer config-driven behavior when values may need balancing.
* Avoid hardcoding player-facing balance values directly into gameplay classes when a config is more appropriate.
* Keep client-only logic separated from common/server logic.
* Be careful with worldgen, ticking, and event handlers because they can affect performance.
* Avoid doing expensive work every tick unless it is cached, rate-limited, or clearly necessary.

When using events:

* Add a short comment explaining what triggers the event and why this handler belongs there.
* Avoid duplicate event registrations.
* Make server/client side checks explicit when needed.

When using mixins:

* Keep mixins narrow and targeted.
* Add comments explaining why a mixin is necessary and why an event/config/API approach is not enough.
* Avoid fragile injections when a safer hook exists.

When using networking:

* Keep packet names clear.
* Validate server-side data.
* Avoid trusting client input.
* Add comments explaining what each packet syncs and when it is sent.

## Documentation and Code Comments

This project should be friendly to future contributors.

Use comments like this:

```java
// Tracks cloak cooldown separately from the active cloak timer so the player
// cannot instantly re-trigger the ability after it ends.
```

Avoid comments like this:

```java
// Set cooldown to 100.
```

Use Javadocs for API-facing classes and methods:

```java
/**
 * Handles server-side cloak state for players.
 *
 * <p>This class owns cloak activation, duration tracking, and cooldown timing.
 * Client rendering should read synced state instead of duplicating this logic.</p>
 */
```

For new systems, include a short file or class-level explanation covering:

* What the system does.
* What owns the state.
* What runs on the server.
* What runs on the client.
* How the system is tested or manually verified.

## Testing Guidelines

Tests use JUnit Jupiter 5.

Name test classes `*Test`, mirror the source package, and give test methods behavior-focused names such as `ignoresEmptyAllocations`.

Add unit tests for isolated logic and GameTests for behavior requiring a loaded Minecraft world.

There is no declared coverage threshold, but every bug fix should include a regression test where practical.

Run `test` and any relevant client, server, or GameTest task before opening a PR.

If a test cannot be added, explain why and provide manual test steps.

## Feature Development Guidelines

When adding a feature, keep it split into clear parts when possible:

* Registration
* Config
* Runtime logic
* Data/resources
* Client behavior, if needed
* Tests or manual validation steps
* Documentation notes, if the system is large

For larger features, prefer a phased implementation:

1. Minimal compile-safe skeleton.
2. Core gameplay behavior.
3. Config and balancing.
4. Client visuals/audio/UI.
5. Tests, docs, and polish.

Do not add unnecessary dependencies.

Do not invent new architecture if the existing project already has a clear pattern.

## Commit & Pull Request Guidelines

Recent commits use short, lowercase summaries such as `performance tweaks` and `fixed cloak issue`.

Keep that concise style, but make the scope specific and use an imperative verb.

Examples:

* `add cloak cooldown config`
* `fix riftfall spawn check`
* `document worldgen placement rules`

Keep each commit focused.

Pull requests should explain:

* The change.
* The player-facing impact.
* Relevant issues.
* Validation commands.
* Screenshots or logs for rendering, UI, world-generation, startup, or crash fixes.

Never commit tokens, webhook URLs, local server configs, logs, crash reports, or other secrets from `run/`.

## Security and Secrets

Never commit:

* API keys
* Discord bot tokens
* Webhook URLs
* Local server IPs or credentials
* Private configs
* Crash logs with personal paths
* Files from `run/`
* IDE-specific secrets

If a feature needs a token or external service, use a local ignored config file or environment variable and document the setup safely.

## Final Response Format for AI Agents

After completing a coding task, respond with:

1. **What changed**

   * List the important files and systems changed.

2. **How it works**

   * Explain the main classes, methods, events, registries, configs, or resources.

3. **How to test**

   * Give exact Gradle commands and in-game steps.

4. **Validation**

   * State which commands were run and whether they passed.

5. **Notes**

   * Mention limitations, assumptions, or follow-up ideas.
