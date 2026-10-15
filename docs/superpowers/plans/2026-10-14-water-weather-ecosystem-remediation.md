# Water, weather, and ecosystem remediation plan

> **For agentic workers:** Use test-driven development and serial Gradle validation. The root agent owns integration and all Gradle/Minecraft processes.

**Goal:** Correct all thirteen actionable findings in the October 14 audit.

**Architecture:** Retain the existing canonical water, regional hydrology, weather,
group AI, and vegetation owners. Repair admission, lifecycle, sampling, and
decision paths without new simulation authorities or save-format changes.

**Tech stack:** Java 21, Minecraft 1.21.1, NeoForge, JUnit Jupiter, NeoForge GameTests.

**Spec:** `docs/audits/water-weather-ecosystem-audit-2026-10-14.md`, approved for
correction by the user's request to fix all findings.

## Global constraints

- Preserve unrelated dirty work and existing worlds; no implicit legacy-water conversion.
- No force-loaded chunks, background Minecraft mutation, unbounded work, or new dependencies.
- One Gradle/NeoForm/Minecraft process at a time; use `'-PcodexBuildDir=.codex-build' --no-parallel`.
- Use real loaded-world tests for ownership/lifecycle behavior; pure policy/model tests supplement those paths.
- Preserve external AI/NoAI ownership, independent compatibility toggles, and existing packet/save schemas.
- Work in the current `in-dev` checkout; do not commit or change branches automatically.

## Review focus

- Reject unowned fluid destinations without losing source volume or replacing waterlogged hosts.
- Persisted enabled water must keep working when the initial-choice config later changes.
- Weather batch retention must not invalidate captured active inputs during apply.
- Followers must update cheap local needs without duplicating expensive leader searches.
- Actual terrain, changing flood safety, cold shelter, and biome borders must reach runtime decisions.

## Task 1: Water ownership, configuration, and terrain sampling

Owner: root agent. Findings F01, F02, F08, F09.

Likely files: `CanonicalWater`, `WaterSimulationConfig`, `WildernessFluidRegistry`,
`WildernessWeatherWaterInfluence`, `RegionalSoilSampler`, relevant JUnit and
new dedicated GameTest classes. Preserve the already modified water safety
GameTest and debug-command files.

- [ ] Add regression coverage for vanilla/custom gravity and lateral boundaries,
  persisted mode with a changed startup setting, shallow surface water, and
  grass-versus-bare soil admission.
- [ ] Run the smallest selected regressions and record failing behavior.
- [ ] Reject unowned fluid-bearing destinations; make runtime budgets agree with
  persisted authority; sample occupied surface water and vegetation correctly.
- [ ] Run focused tests, then loaded-world regressions; inspect compatibility gates.

## Task 2: Ecosystem needs, owner lifecycle, targets, and habitat

Owner: one ecosystem implementer; root runs all tests. Findings F05, F06, F07,
F10, F11, F12. No edits outside ecosystem source/tests unless agreed with root.

Likely files: `EcosystemBehaviorGoal`, `GroupFollowerGoal`, `AnimalGroupManager`,
`EcosystemSimulationManager`, `CachedWaterSourceLocator`,
`CachedFoodAvailabilityService`, `DefaultEcosystemBehaviorController`,
`DistantWildlifeManager`, and focused unit/GameTest coverage.

- [ ] Add failing regressions for follower need progression/action completion,
  suspended leader recovery, unsafe cached water, canopy forage, clear cold
  shelter discovery/execution/release, and final candidate habitat rejection.
- [ ] Have root execute red tests before production changes for each batch.
- [ ] Implement cheap per-animal physiology and completion using shared group
  decisions; preserve leader ownership of expensive context work.
- [ ] Reassign ineligible group owners through existing lifecycle paths.
- [ ] Revalidate drinking targets and abilities at reuse and interaction start.
- [ ] Use bounded ground forage probes, connect cold shelter behavior, and check
  final materialization habitat before insertion/debit.
- [ ] Run focused unit and loaded-world regressions through root; self-review.

## Task 3: Weather snow ownership, batch retention, and severe cadence

Owner: one weather implementer after Task 2's implementation agent finishes;
root runs all tests. Findings F03, F04, F13. No water/ecosystem edits.

Likely files: `ServerLevelLocalizedPrecipitationMixin`, `AtmosphereSavedData`,
`WeatherAuthority`, `SevereWeatherScheduler`, and focused JUnit/GameTests.

- [ ] Add failing regressions for modeled snow through real vanilla precipitation,
  active footprint exceeding a valid retention cap, and severe IDs 1-10.
- [ ] Have root execute red tests before production changes.
- [ ] Preserve modeled SWE ownership, prevent lookup-time destructive trimming,
  and schedule disturbance opportunities in the scheduler's actual time slots.
- [ ] Run focused unit and loaded-world regressions through root; self-review.

## Task 4: Integration, independent review, and report

- [ ] Compile the affected source and run selected root subsystem tests.
- [ ] Run the applicable GameTest namespaces serially and confirm tests actually execute.
- [ ] Obtain independent review of conservation, lifecycle, and cross-system behavior;
  correct actionable findings and rerun affected checks.
- [ ] Run root packaging if needed for mixin/GameTest/resource integration.
- [ ] Update the audit with a correction/coverage matrix and exact evidence.
- [ ] Review diffs for unrelated changes, generated output, temporary diagnostics,
  secrets, and machine-specific paths. Report manual client/modpack limits honestly.
