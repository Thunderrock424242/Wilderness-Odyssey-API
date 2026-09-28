AGENTS.md

Repository Guidelines

Project Purpose

This repository contains the core Wilderness Odyssey Minecraft API and gameplay systems for Minecraft 1.21.1 using NeoForge.

The API supports systems such as:

* World generation and structures.
* Aether AI integration.
* Riftfall systems.
* Cloaking and gameplay mechanics.
* Weather and environmental systems.
* Networking and synchronized state.
* Client rendering and effects.
* Configurable gameplay systems.
* Data generation and resources.
* Other shared Wilderness Odyssey systems used by the modpack.

Treat this repository as production Minecraft mod code.

Prefer supported NeoForge APIs and existing project architecture over invasive or speculative solutions.

⸻

Priority Rules

These rules take priority during normal repository work.

1. Follow the requested scope.
2. Do not expand a task into unrelated cleanup, redesign, or refactoring.
3. Inspect the existing implementation before making substantial changes.
4. Prefer source-code changes over generated-output manipulation.
5. Reuse existing architecture, registries, configs, services, and patterns.
6. Keep changes modular and focused.
7. Use the smallest meaningful validation step first.
8. Only one Codex-controlled Gradle, NeoForm, data-generation, or Minecraft development process may run at a time.
9. Treat generated NeoForm, Minecraft, dependency, and Gradle JARs as build infrastructure, not editable source files.
10. Do not modify Windows permissions, NTFS ACLs, antivirus settings, Codex sandbox settings, or system security settings without explicit user approval.
11. Do not describe an environment failure as a source-code failure without evidence.
12. Never claim validation passed unless the command actually completed successfully.
13. Do not manually reconstruct the Gradle/NeoForge build pipeline when the normal build system can perform the task.
14. Preserve existing working behavior unless the requested change intentionally replaces it.

⸻

Codex Context Efficiency

Use the smallest useful repository context for the task.

Do not read the entire repository before every change.

Start with the feature that owns the requested behavior and expand outward only when dependencies require it.

For example:

Gameplay mechanic

Inspect:

* the owning feature package,
* relevant registration,
* config,
* events,
* networking,
* tests.

World generation

Inspect:

* the relevant worldgen package,
* registrations,
* placement logic,
* data-generation code,
* related resources.

Client rendering

Inspect:

* the affected client package,
* renderer or event handler,
* synchronized state,
* shader/resource files where relevant.

Networking

Inspect:

* packet definitions,
* registration,
* sender,
* receiver,
* authoritative server state.

Do not automatically inspect:

* build/,
* .codex-build/,
* run/,
* the entire Gradle cache,
* dependency binaries,
* unrelated feature packages,
* generated resources unrelated to the task.

Prefer targeted source searches over broad repository scanning.

Do not repeatedly re-read files already understood unless the task changes or new evidence requires it.

For localized changes, keep reasoning and context localized.

Use deeper reasoning for:

* architecture changes,
* cross-system bugs,
* threading,
* networking security,
* worldgen,
* rendering,
* performance,
* difficult compatibility problems.

Routine config, content, or small Java changes should remain narrow.

⸻

Standard Task Workflow

Use this workflow unless the task clearly requires something different.

1. Inspect

Determine:

* Which feature owns the behavior.
* Which classes implement related behavior.
* Which registrations, configs, resources, events, mixins, or networking paths are involved.
* Whether the behavior is client-side, server-side, or shared.
* Whether relevant tests already exist.

Inspect direct callers and dependencies before changing architecture.

2. Plan

For non-trivial changes, briefly identify:

* files likely to change,
* existing architecture being reused,
* new responsibilities being introduced,
* lifecycle concerns,
* client/server implications,
* compatibility concerns.

Do not create an elaborate plan for a tiny localized change.

3. Implement

* Keep changes focused.
* Match surrounding style.
* Prefer small cohesive classes and methods.
* Reuse existing systems.
* Avoid unnecessary new abstractions.
* Avoid unrelated cleanup.
* Keep intermediate states compile-safe when practical.

When requirements are clear, implement them rather than repeatedly requesting confirmation.

Ask for clarification only when a missing requirement genuinely prevents a safe implementation.

4. Validate

Use the smallest validation command that meaningfully exercises the change.

Escalate only when broader validation adds useful confidence.

5. Review

Before finishing:

* review the diff,
* remove debug code,
* remove temporary diagnostics,
* check imports,
* check generated files,
* check unrelated formatting changes,
* check machine-specific paths,
* check secrets.

6. Report

Clearly separate:

* implementation result,
* validation result,
* environment limitations,
* remaining manual testing.

⸻

Project Structure

Production Java code:

src/main/java/com/thunder/wildernessodysseyapi

Organize code by feature.

Existing or expected feature packages include:

* worldgen
* cloak
* ai
* riftfall

Keep feature-specific helpers inside the feature they support.

Examples:

* Cloaking behavior belongs in cloak.
* Riftfall behavior belongs in riftfall.
* Aether systems belong in ai.
* Structures, biomes, terrain, placement, and world-generation helpers belong in worldgen.

Avoid creating broad packages such as:

* util
* helper
* manager
* misc

unless the functionality is genuinely shared across multiple independent systems.

Shared infrastructure should have a clearly defined purpose.

Resources:

src/main/resources

This may contain:

* Minecraft assets,
* data-pack JSON,
* shaders,
* configs,
* YAML,
* defaults,
* metadata.

Mod metadata templates:

src/main/templates

Generated resources:

src/generated/resources

Tests:

src/test/java

Mirror the production package structure where practical.

Subsystem documentation:

docs/

Generated local output:

* build/
* .codex-build/
* run/

Do not commit generated local output unless the task specifically requires generated resources.

⸻

Java & Coding Style

Use:

* UTF-8.
* Four-space indentation.
* Opening braces on the declaration line.
* PascalCase for classes and interfaces.
* camelCase for methods, fields, and local variables.
* UPPER_SNAKE_CASE for constants.
* Lowercase Java package names.
* Lowercase Minecraft resource identifiers and namespaces.

The mod namespace is:

wildernessodysseyapi

No automatic formatter is currently configured.

Use nearby code as the formatting baseline.

Qodana findings may be used as the lint baseline.

Avoid formatting unrelated files.

Do not reorder imports, declarations, comments, or whitespace across unrelated code merely for cleanup.

Keep diffs reviewable.

⸻

Code Quality

Prefer readable code over clever code.

Keep methods focused.

Avoid unnecessarily large classes.

Separate responsibilities when a feature involves:

* registration,
* configuration,
* runtime logic,
* networking,
* client behavior,
* resources,
* persistence,
* testing.

Avoid abstractions that do not solve an actual project problem.

Do not introduce a dependency when the existing stack can reasonably implement the feature.

Avoid unnecessary null assumptions, unchecked casts, reflection, or dynamic behavior.

Use public APIs and stable NeoForge hooks where practical.

⸻

Documentation & Comments

Comments should explain:

* intent,
* lifecycle assumptions,
* compatibility constraints,
* non-obvious Minecraft behavior,
* reasoning behind unusual implementation choices.

Do not comment obvious assignments or straightforward conditions.

Good:

// Tracks cooldown independently from cloak duration so ending the cloak does
// not immediately allow another activation.

Avoid:

// Set cooldown.

Use Javadocs for important API-facing classes and methods, including where appropriate:

* public APIs,
* registries,
* configuration classes,
* important event handlers,
* attachments,
* networking systems,
* mixins,
* reusable subsystem interfaces.

For substantial systems, document:

* authoritative state ownership,
* server responsibilities,
* client responsibilities,
* synchronization,
* lifecycle,
* performance implications,
* testing expectations.

Documentation belongs in docs/ when the architecture is too large or conceptual to explain cleanly in source comments.

⸻

Minecraft & NeoForge Guidelines

Follow the NeoForge 1.21.1 conventions already established in this repository.

When adding or changing a system:

* follow existing registration patterns,
* use supported NeoForge hooks where practical,
* keep resource locations lowercase,
* keep translation keys consistent,
* separate client-only code from common/server code,
* treat the server as authoritative for gameplay,
* make logical-side assumptions explicit where necessary.

Never load client-only Minecraft classes from common or dedicated-server code.

Be especially careful with:

* ticking,
* worldgen,
* chunks,
* rendering,
* shaders,
* networking,
* entities,
* dimensions,
* events,
* resource reloads.

Prefer additive behavior over destructive replacement.

Use mixins only when an appropriate NeoForge event, hook, extension point, or supported API cannot reasonably satisfy the requirement.

⸻

Registration & Lifecycle

Use the project’s existing registration approach before introducing a new one.

Pay attention to:

* correct event bus,
* correct registration phase,
* client vs common registration,
* server lifecycle,
* resource reload lifecycle,
* world unload,
* player logout,
* chunk unload.

Avoid duplicate registration.

Avoid retaining world-, player-, entity-, or server-specific state beyond its lifecycle.

Clean up listeners, caches, scheduled work, and temporary state when their owning lifecycle ends.

⸻

Event Guidelines

For event-driven systems:

* use the most specific useful event,
* avoid broad high-frequency handlers when a narrower event exists,
* avoid expensive work every tick,
* make client/server checks explicit,
* avoid duplicate listeners,
* keep event handlers focused.

Move substantial feature logic out of event handlers into feature-owned classes when appropriate.

Events should normally coordinate behavior rather than become giant gameplay systems themselves.

⸻

Performance Guidelines

Performance-sensitive areas include:

* tick handlers,
* entity AI,
* pathfinding,
* worldgen,
* chunk access,
* rendering,
* shaders,
* networking,
* attachments and synchronization,
* large collections,
* resource lookups,
* file I/O,
* external AI/network requests.

Before optimizing, identify the real hot path when practical.

Prefer eliminating unnecessary work over making unnecessary work slightly faster.

Prefer:

* event-driven updates,
* dirty-state updates,
* caching stable values,
* batching,
* rate limiting,
* scheduled work,
* precomputation,
* bounded queues.

Avoid:

* unnecessary work every tick,
* repeated registry lookups in hot paths,
* repeated large-area scans,
* avoidable allocations in render/tick loops,
* unnecessary chunk loading,
* blocking file I/O on the game thread,
* blocking network I/O on the game thread,
* unbounded collections,
* caches without invalidation rules.

Any new per-tick behavior should have a clear reason for running at that frequency.

If work can safely run every 5, 10, or 20 ticks instead of every tick, consider whether that better fits the feature.

Do not prematurely micro-optimize simple cold-path code at the cost of maintainability.

⸻

Threading & Async Safety

Minecraft state is generally not safe to modify from arbitrary background threads.

Do not directly modify:

* worlds,
* players,
* entities,
* registries,
* chunks,
* block entities,
* Minecraft-owned state

from an unsafe background thread.

Asynchronous work may be appropriate for:

* external HTTP requests,
* model inference requests,
* file operations,
* expensive pure computation,
* preprocessing.

Capture required immutable inputs before leaving the game thread.

Schedule Minecraft state changes back onto the appropriate game thread.

Do not create uncontrolled thread pools.

Prefer existing project executors or standard bounded executors.

Async work must respect lifecycle shutdown.

Avoid background work continuing indefinitely after:

* server shutdown,
* world unload,
* player disconnect,
* feature disablement.

Do not make a system asynchronous merely because asynchronous code sounds faster.

⸻

Aether / AI Integration

Aether-related systems belong primarily under the ai feature architecture.

External model or AI requests must not block the main Minecraft server thread.

AI integrations should use:

* bounded timeouts,
* cancellation where practical,
* bounded request sizes,
* bounded response sizes,
* authentication where required,
* rate limiting where appropriate,
* graceful unavailable states.

Do not assume the external model service is always reachable.

Minecraft gameplay should degrade safely when the AI service is unavailable.

Avoid sending unnecessary world or player information to external services.

Do not expose:

* internal prompts intended to remain private,
* credentials,
* service tokens,
* internal service addresses unnecessarily.

Keep AI inference separate from authoritative gameplay logic where practical.

The server remains authoritative for gameplay state.

⸻

Networking Guidelines

Use descriptive payload or packet names.

Clearly separate:

* clientbound behavior,
* serverbound behavior.

Never trust client-supplied data merely because it came through a registered packet.

Validate server-side:

* IDs,
* UUIDs,
* positions,
* distances,
* dimensions,
* ranges,
* permissions,
* player state,
* feature state.

Avoid sending packets every tick unless truly required.

Prefer:

* dirty-state synchronization,
* batching,
* rate limiting,
* event-triggered synchronization.

Do not duplicate authoritative gameplay state on the client.

The client should render or present synchronized state rather than independently deciding authoritative gameplay outcomes.

Document important packets when their synchronization behavior is not obvious.

⸻

Worldgen Guidelines

World generation can have major performance and compatibility consequences.

Worldgen code belongs under the relevant worldgen architecture.

When changing worldgen:

* avoid forcing unrelated chunks to load,
* avoid unnecessary neighboring chunk access,
* use deterministic behavior where seed and position should control results,
* validate biome and structure queries,
* use heightmaps appropriately,
* separate generation-time logic from runtime ticking,
* prefer data-driven registration where appropriate,
* validate resource locations,
* validate generated JSON paths.

Do not calculate at runtime what can safely be determined during generation.

Be cautious with:

* terrain modification,
* fluids,
* structure placement,
* feature ordering,
* biome selection,
* large structures,
* cross-chunk placement.

Consider compatibility with:

* terrain mods,
* biome mods,
* structure mods,
* dimension mods.

Large or unusual worldgen architecture should be documented under docs/.

⸻

Terrain-Aware Structure Placement

For Wilderness Odyssey structures that use terrain-aware placement:

* validate terrain fit before committing placement,
* avoid unnecessary large-area scans,
* avoid loading chunks solely to inspect distant terrain when possible,
* keep placement deterministic where appropriate,
* separate terrain analysis from block placement,
* fail gracefully when a valid placement cannot be found.

Structure placement should not leave partially generated structures when the fit check fails.

Keep terrain-fit logic reusable where multiple structure systems depend on it.

⸻

Weather & Environmental Systems

Weather and environmental systems can become expensive if implemented as global polling.

Prefer:

* localized state,
* spatial partitioning,
* bounded active regions,
* event-driven updates,
* scheduled updates,
* cached calculations,
* gradual simulation.

Avoid scanning every loaded chunk every tick.

Avoid updating distant environmental effects at the same frequency as nearby gameplay-critical effects.

Keep gameplay simulation independent from optional rendering features where practical.

Rendering enhancements should fail gracefully when unsupported.

⸻

Rendering & Shader Guidelines

Client rendering belongs in client-only code.

Never make dedicated-server startup depend on rendering classes.

Avoid:

* unnecessary allocations each frame,
* repeated expensive world scans during rendering,
* synchronous file or network access during rendering,
* rebuilding stable rendering data every frame.

Cache render resources when safe.

Invalidate them when the owning state changes.

Optional graphics integrations should not control core gameplay behavior.

Where rendering integrations depend on external shader or graphics mods, guard them safely and keep fallback behavior available.

⸻

Ray-Tracing / Advanced Rendering

Advanced rendering support must remain optional.

Gameplay must not require ray tracing.

When implementing RT or RT-style effects:

* isolate rendering integration from gameplay logic,
* detect capability safely,
* provide non-RT fallback behavior,
* avoid assuming a specific shader pack,
* avoid hard dependencies on optional rendering mods unless explicitly intended.

Integrations with Iris or other shader systems should remain compatibility-focused and should not compromise dedicated-server behavior.

⸻

Mixin Guidelines

Use mixins only when supported APIs are insufficient.

Mixins must be:

* narrow,
* targeted,
* documented,
* compatibility-aware.

Explain why the mixin is necessary when it is not obvious.

Prefer targeted injections over broad method overwrites.

Avoid fragile assumptions about:

* ordinals,
* locals,
* invocation order,
* implementation details.

Document such assumptions when unavoidable.

Consider other mods modifying the same code path.

Keep unrelated mixin behavior separate.

⸻

Config Guidelines

Use configuration for values pack developers or server owners may reasonably need to tune.

Examples:

* cooldowns,
* durations,
* distances,
* probabilities,
* spawn chances,
* feature toggles,
* performance budgets,
* rates,
* damage,
* limits.

Do not create configuration entries for internal implementation constants users should never need to change.

Validate ranges where appropriate.

Document units such as:

* ticks,
* seconds,
* blocks,
* percentages,
* probabilities.

Server-authoritative configuration must remain authoritative.

Synchronize values to clients only when the client needs them for correct presentation or behavior.

⸻

Resource & Data Guidelines

For resources including:

* JSON,
* models,
* textures,
* shaders,
* tags,
* loot tables,
* recipes,
* structures,
* sounds,
* translations,

follow Minecraft naming conventions.

Use the namespace:

wildernessodysseyapi

Avoid duplicate resource IDs.

Use lowercase paths where required.

Keep handwritten resources separate from generated resources.

Prefer translation keys over hardcoded player-facing text where appropriate.

Review generated-resource diffs after data generation.

Do not manually modify generated resources when their generator owns the source.

⸻

Data Generation

Use:

.\gradlew.bat runData -PcodexBuildDir=.codex-build --no-parallel

only when the task requires data generation.

Review generated diffs afterward.

Do not run data generation automatically for unrelated Java changes.

Do not manually modify generator-owned output instead of fixing the generator.

⸻

Testing

Tests use JUnit Jupiter 5.

Test class names should use:

*Test

Mirror production package structure where practical.

Prefer behavior-focused names such as:

ignoresEmptyAllocations

Add unit tests for isolated Java logic.

Use NeoForge GameTests where behavior requires:

* loaded worlds,
* blocks,
* entities,
* structures,
* game rules,
* Minecraft lifecycle,
* server-side Minecraft behavior.

Every bug fix should include a regression test when practical.

A regression test should:

* reproduce the previous failure,
* verify the corrected behavior,
* avoid depending on unrelated implementation details.

If an automated test would not provide useful coverage, explain why and provide manual testing steps.

Do not create meaningless tests solely to increase test count.

⸻

Build Environment

Use the checked-in Gradle wrapper.

Required Java version:

JDK 21

Primary Windows commands:

.\gradlew.bat compileJava
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runGameTestServer
.\gradlew.bat runData

Do not invent an alternate build process when Gradle/NeoForge already supports the task.

⸻

Codex Build Isolation

Routine Codex validation should use:

-PcodexBuildDir=.codex-build

Examples:

.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat runData -PcodexBuildDir=.codex-build --no-parallel

The isolated build directory reduces conflicts with normal IDE or developer build output.

.codex-build/ is generated local output and must not be committed.

The Gradle build should preserve support for the codexBuildDir property.

Equivalent behavior:

def codexBuildDir = providers.gradleProperty('codexBuildDir')
if (codexBuildDir.isPresent()) {
    layout.buildDirectory.set(file(codexBuildDir.get()))
}

Do not remove this behavior unless equivalent isolation replaces it.

Build isolation does not eliminate:

* filesystem locks,
* antivirus interference,
* NTFS permission problems,
* Gradle cache failures,
* Codex sandbox restrictions,
* NeoForm temporary-file issues.

A failure inside .codex-build/ is therefore not automatically a source-code failure.

⸻

Validation Strategy

Use the smallest meaningful validation step first.

General escalation order:

1. Compile the affected source.
2. Run the most relevant targeted test.
3. Run relevant subsystem tests.
4. Run the full test suite when broader regression coverage is useful.
5. Run build when packaging, registrations, resources, startup, or integration behavior requires it.
6. Launch Minecraft only when runtime behavior genuinely requires a Minecraft environment.

Routine Java changes

.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel

Java logic with tests

.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel

Registration, resources, packaging, or integration

.\gradlew.bat build -PcodexBuildDir=.codex-build --no-parallel

Data generation

.\gradlew.bat runData -PcodexBuildDir=.codex-build --no-parallel

Do not automatically run the full build after every edit.

Do not repeatedly execute equivalent validation solely to increase confidence.

Do not rerun an unchanged failing command without first understanding why it failed.

Use:

clean

only when stale build output is reasonably suspected.

Use:

--refresh-dependencies

only when dependency resolution or cache corruption is reasonably suspected.

Neither is routine validation.

⸻

Runtime Validation

Launch Minecraft only when it adds meaningful confidence.

Possible runtime commands:

.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runGameTestServer

Do not launch Minecraft for small Java-only changes that compilation and tests adequately validate.

When runtime validation is needed:

1. compile first,
2. run relevant automated tests,
3. launch the smallest applicable Minecraft environment,
4. describe the manual behavior being verified.

⸻

Gradle & NeoForm Concurrency

Only one Codex-controlled Gradle, NeoForm, data-generation, or Minecraft development process may run for this repository at a time.

This includes:

* compileJava,
* test,
* build,
* clean,
* runClient,
* runServer,
* runData,
* runGameTestServer,
* dependency refreshes,
* NeoForm setup or transformation.

Do not:

* run Gradle from multiple subagents simultaneously,
* launch runClient while another Codex process runs build,
* launch multiple NeoForm initialization attempts,
* run duplicate validation in parallel.

Subagents may inspect unrelated source code concurrently when useful.

Return Gradle/NeoForm execution to one owning agent for final validation.

⸻

Generated JAR Policy

Generated Minecraft, NeoForm, Gradle, and dependency JARs are build infrastructure.

Examples may appear under paths resembling:

build/tmp/neoformruntime/
.codex-build/tmp/neoformruntime/
.gradle/

Do not manually:

* patch generated NeoForm JARs,
* replace them,
* rename them,
* repackage them,
* edit Minecraft dependency JARs,
* edit Gradle cache JARs,
* delete individual generated JARs while Gradle is running.

Use the normal Gradle pipeline.

When API inspection is necessary, prefer:

1. project source,
2. generated source,
3. source JARs,
4. official NeoForge or library documentation,
5. targeted dependency inspection.

Do not recursively extract or inspect the entire Gradle cache to understand one API.

⸻

Windows Build Failure Diagnosis

Do not treat all Windows filesystem failures as source-code failures.

Likely lock

Messages such as:

The process cannot access the file because it is being used by another process

or explicit sharing violations indicate a likely file handle conflict.

Possible holders include:

* Gradle,
* Java,
* Minecraft,
* IntelliJ,
* another terminal,
* antivirus,
* another Codex process.

Access denied

Errors such as:

java.nio.file.AccessDeniedException

or:

Access is denied

may result from:

* NTFS permissions,
* sandbox restrictions,
* antivirus,
* ownership/ACL behavior,
* file locks,
* unsupported filesystem operations.

AccessDeniedException alone is not proof of a file lock.

Report it as access denied unless stronger evidence identifies the cause.

⸻

Gradle / NeoForm Recovery

When Gradle or NeoForm fails on generated files:

1. Capture the failure

Record:

* command,
* failing task,
* exact exception,
* exact path.

2. Check concurrency

Confirm Codex is not already running:

* Gradle,
* Minecraft,
* NeoForm,
* data generation.

3. Confirm build isolation

Routine validation should use:

-PcodexBuildDir=.codex-build

If isolation was omitted, retry once using isolated output when appropriate.

4. Stop Gradle daemons when a stale handle is plausible

.\gradlew.bat --stop

Do not repeatedly stop Gradle without reason.

5. Retry the smallest validation once

For example:

.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-daemon --no-parallel

--no-daemon is a diagnostic/recovery option, not a requirement for normal successful builds.

6. Stop escalation if access remains blocked

If the same generated or NeoForm path continues failing after reasonable recovery:

* do not endlessly retry,
* do not claim the source is broken without evidence,
* report validation as environment-blocked,
* include the path and exception,
* continue only with safe non-destructive validation.

⸻

Forbidden Automatic Recovery

Do not automatically perform any of the following solely to make a build succeed:

* modify NTFS ACLs,
* run icacls to broaden access,
* run takeown,
* grant Full Control to sandbox users,
* broaden access to the user’s home directory,
* broaden drive permissions,
* disable Windows Defender,
* add antivirus exclusions,
* disable security software,
* switch Codex sandbox mode,
* enable Full Access,
* run the entire environment as Administrator,
* delete the global Gradle cache,
* delete the user’s entire .gradle directory,
* kill unrelated Java processes,
* kill IntelliJ,
* kill unrelated Minecraft processes,
* delete arbitrary dependency JARs,
* manually reconstruct NeoForm,
* build giant handwritten Java classpaths,
* replace Gradle validation with manual javac.

If one of these appears genuinely necessary:

1. explain why,
2. explain the smallest required scope,
3. explain the risk,
4. wait for explicit user approval.

⸻

Clean & Cache Policy

Do not use:

.\gradlew.bat clean build

as generic troubleshooting.

Use clean only when:

* output is clearly stale,
* generated state is inconsistent,
* clean-build behavior is specifically required,
* the user requests it.

Do not delete .gradle/ or the global Gradle cache because of a single NeoForm access error.

Do not routinely use:

--refresh-dependencies

Target cleanup to the smallest project-local scope.

⸻

Agent & Subagent Usage

The main agent should normally handle:

* repository exploration,
* implementation,
* testing,
* Gradle execution,
* integration,
* review.

Do not spawn subagents for ordinary:

* feature work,
* small or medium bug fixes,
* simple exploration,
* routine validation,
* one-system refactors.

Subagents are appropriate for genuinely independent workstreams such as:

* large repository audits,
* separate security analysis,
* broad performance investigation,
* large migrations,
* independent compatibility research.

When subagents are used:

* assign clear ownership,
* avoid overlapping edits,
* avoid duplicate repository scanning,
* avoid duplicate findings,
* avoid duplicate validation,
* never run Gradle concurrently.

The main agent should perform final integration validation.

⸻

Security & Secrets

Never commit:

* API keys,
* Discord bot tokens,
* webhook URLs,
* authentication tokens,
* credentials,
* private keys,
* sensitive environment files,
* private external-service configuration,
* crash logs containing credentials,
* sensitive files from run/.

External-service credentials should use:

* environment variables,
* ignored local configuration,
* another approved secret mechanism.

Never log credentials.

Never synchronize secrets to Minecraft clients.

Never expose server-authoritative sensitive information unless gameplay genuinely requires it.

If a credential may have been committed, treat it as compromised and rotate it rather than merely deleting the latest copy.

⸻

Error Handling & Logging

Use logging for useful diagnostics.

Avoid log spam from:

* tick handlers,
* render loops,
* entity AI,
* networking hot paths,
* worldgen inner loops.

Use appropriate log levels.

Errors should identify enough subsystem context to locate the failure.

Do not silently swallow unexpected exceptions.

Expected recoverable failures may be handled without stack-trace spam.

Never log secrets or sensitive external-service content.

⸻

Feature Development

For larger features, separate responsibilities where practical:

1. registration,
2. configuration,
3. authoritative runtime behavior,
4. networking,
5. client visuals/audio/UI,
6. resources,
7. tests,
8. documentation,
9. compatibility.

Large features may be implemented in phases:

1. compile-safe foundation,
2. core behavior,
3. configuration,
4. synchronization,
5. client presentation,
6. testing and documentation,
7. compatibility and polish.

Do not place a major system entirely in one oversized class when responsibilities can be meaningfully separated.

Do not invent a new architecture when the repository already has an appropriate one.

⸻

Compatibility

Wilderness Odyssey may run alongside many other mods.

Prefer additive behavior.

Do not assume this mod exclusively owns vanilla or NeoForge behavior.

Avoid hard dependencies on optional mods unless intentionally required.

Guard optional integrations safely.

Keep compatibility logic separate from core gameplay logic.

Do not reference optional-mod classes unless their presence is safely established.

Pay particular attention to compatibility in:

* mixins,
* rendering,
* shaders,
* worldgen,
* fluids,
* networking,
* registries,
* dimensions.

Mention meaningful compatibility concerns when completing relevant work.

⸻

Repository Audits

When specifically asked to audit the repository, inspect for:

* duplicate systems,
* incomplete implementations,
* dead code,
* TODO/FIXME markers,
* potential crashes,
* side-handling mistakes,
* event-registration errors,
* registry problems,
* networking trust issues,
* fragile mixins,
* performance hot paths,
* unnecessary per-tick work,
* unbounded collections,
* memory leaks,
* stale caches,
* chunk-loading risks,
* worldgen performance problems,
* threading issues,
* duplicate resources,
* unused configs,
* missing validation,
* missing regression tests,
* architecture inconsistencies,
* compatibility risks,
* secret exposure.

Separate findings into:

* confirmed problems,
* likely problems,
* possible concerns.

Prioritize by practical impact.

Do not automatically fix every audit finding unless fixes were requested.

⸻

Git & Diff Hygiene

Keep changes scoped to the requested task.

Before finishing, inspect the diff for:

* unrelated modifications,
* generated local output,
* temporary diagnostics,
* debug logging,
* commented-out experiments,
* temporary assets,
* machine-specific paths,
* secrets,
* broad formatting changes.

Do not modify unrelated files simply because they could be improved.

Avoid large formatting-only diffs unless formatting itself was requested.

⸻

Commit & Pull Request Guidelines

Use concise lowercase action summaries.

Examples:

* add cloak cooldown config
* fix riftfall spawn check
* optimize weather scheduling
* add terrain fit validation
* fix aether request timeout

Keep commits focused.

Pull requests should describe:

* what changed,
* player-facing impact,
* architecture decisions,
* relevant issues,
* validation commands,
* validation results,
* compatibility considerations,
* screenshots or logs where useful.

Do not include generated local output.

⸻

Final Response Format

After completing a coding task, use the following structure.

1. What changed

Summarize meaningful files and systems changed.

Do not list every trivial edit.

2. How it works

Explain only the architecture needed to understand the implementation.

Include relevant:

* classes,
* methods,
* events,
* registries,
* configs,
* networking,
* resources.

3. How to test

Provide exact relevant commands.

Include in-game validation steps when applicable.

4. Validation

State exactly which commands were actually run.

Example:

.\gradlew.bat test -PcodexBuildDir=.codex-build --no-parallel

Report each as one of:

* Passed.
* Failed because of code/tests.
* Blocked by environment.
* Not run, with reason.

If Gradle or NeoForm is blocked by Windows filesystem, antivirus, or Codex sandbox behavior, report it as environment-blocked unless evidence specifically shows project code caused the failure.

Never claim validation succeeded when the command did not complete successfully.

5. Notes

Mention only relevant:

* assumptions,
* limitations,
* compatibility concerns,
* environment issues,
* deferred work,
* useful follow-up work.

Keep the final report concise and easy to review.