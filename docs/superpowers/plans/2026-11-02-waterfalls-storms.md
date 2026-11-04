# Waterfalls, finite discharge and coastal storms implementation plan

> Execute inline with superpowers:executing-plans, test-driven-development and verification-before-completion. Repository instructions retain main-agent ownership and prohibit automatic commits.

**Goal:** Add bounded waterfall sheet detail, impact turbulence and gravity-aware finite discharge, then an opt-in conserved coastal storm surge, followed by regression, compatibility and performance checks.

**Architecture:** Extend the existing canonical voxel flow, authority sampling, regional hydrology and WaterRenderCoordinator. No second volume owner, scheduler or renderer event. Native falling-fluid geometry remains the compatible water curtain; coordinated whitewater detail dresses its exposed faces. All additional simulation reads are loaded-only and bounded.

**Tech stack:** Minecraft 1.21.1, NeoForge 21.1.262, Java 21, JUnit Jupiter, existing GameTest and GPU fixtures.

## Scope and design decisions

- User selected waterfall forces and finite discharge first; larger experimental open-water regions are excluded from this pass.
- Waterfall gravity/impact velocities derive from an actually wet fall and nonzero physical inflow, never from planned speed alone. Authority samples feed existing entity drag/buoyancy. The model owns no water.
- Existing finite vertical transfers gain velocity-aware discharge; accepted destination receipts still determine the source debit. SPH remains optional and bounded. Preserve the current fall-speed cap and elapsed-time rules.
- Waterfall detail is a coordinator subpass, with fixed candidate/quad budgets and no separate event or buffer flush. Only verified wet/exposed cells receive animated whitewater material. Receiving-water impacts reuse the capped impulse/effect pools.
- Coastal surge is a coarse open-ocean boundary approximation, disabled by default. Cached weather and neighboring regional terrain determine onshore exposure. Accepted OCEAN_IN funds FLOODPLAIN; recession records OCEAN_OUT. Outstanding canonical projections count toward the target so projecting water cannot cause repeated funding. Existing protected, connected, loaded-only flood projection remains responsible for actual blocks.
- Surge forcing survives save/reload and uses the regional catch-up forcing policy. Existing saves default missing forcing to zero. Disabling the option allows funded water to recede.
- Compatibility checks distinguish automated ownership/fallback contracts from actual installed-mod runtime tests. Performance results distinguish bounded-work/fixture timings from gameplay frame and GPU measurements.
- Preserve the uncommitted earlier foam fix and acceptance reports. No commits, unrelated branches, dependency installation, security changes or concurrent development processes.

## Alternatives considered

1. Replace waterfall/fluid rendering and add an independent solver: rejected because it duplicates ownership and compromises renderer compatibility.
2. Extend native curtain material and existing flow/authority paths: selected; retains fallback geometry and conservation while adding local detail and forces.
3. Cosmetic storm water-level offset: rejected for physical flooding because it has no funding receipt. Use the explicit regional ocean boundary instead.

## Review focus

Dry/blocked or interrupted falls must lose their derived forces and whitewater; still metadata cannot create current. Multiple waterfall samples cannot multiply the force. Partial receipts and full receivers conserve units. Projection/reload cannot mint surge inventory. Disabled features and missing optional renderers retain working fallback behavior.

## Task 1 — Waterfall hydraulics and finite discharge

- [x] Add failing numerical tests for ballistic acceleration, bounded impact turbulence, dry/still gates, vertical momentum, receiver capacity and elapsed time.
- [x] Implement a small shared waterfall arithmetic model and loaded-only read adapter.
- [x] Feed waterfall current through AuthorityWaterAccess, preserving one authoritative entity-force path.
- [x] Integrate velocity-aware discharge into the canonical voxel path and its existing SPH handoff.
- [x] Compile and run focused tests; add/run loaded-world conservation and authority regressions.

## Task 2 — Waterfall surface and impact presentation

- [x] Add behavior tests for wet continuity, exposed faces, receiving geometry and fixed budgets.
- [x] Add coordinated whitewater sheet detail without hiding fallback geometry or registering another renderer event.
- [x] Reuse existing bounded surface impulses/effect pools for receiving-water disturbance, foam and mist.
- [x] Validate presentation contracts, shader regression and teardown behavior.

## Task 3 — Conserved coastal storm surge

- [x] Add failing tests for onshore/pressure forcing, bounded rise/decay, exact ocean receipts, projection funding, saved forcing and disabled/default behavior.
- [x] Add server config, cached forcing and regional ocean-boundary integration.
- [x] Exercise expansion/recession and reload with existing canonical flood ownership.
- [x] Run focused storm/regional regressions.

## Task 4 — Acceptance, compatibility, performance and bug checks

- [x] Run relevant water/weather tests and GameTests serially, followed by full tests and packaging.
- [x] Review dedicated-server separation, native/external ownership, optional renderer guards, loaded reads, budgets, lifecycle and persistence.
- [x] Run reproducible bounded-work/performance fixtures and the smallest useful live acceptance; document any unavailable optional-mod matrix or runtime limitation precisely.
- [x] Inspect the complete diff; document implementation, actual validation, limitations and remaining manual acceptance in docs/watersystem.

## Execution ledger

- Initial inspection: shared branch `in-dev`; six earlier uncommitted foam/acceptance files preserved. Original advanced-water request read; repository concurrency and authority rules retained.
- Scope clarified: user chose waterfall forces and finite discharge before larger open-water regions.
- Initial numerical RED: 17 tests, five expected missing-behavior failures. Numerical GREEN: 17/17, wrapper 17 seconds.
- First world fixture had an invalid namespaced template identifier; corrected the fixture. The corrected 32-test safety run reproduced the missing generated-waterfall jet; the standalone finite parcel already retained its current, narrowing that finding.
- Sheet/storm RED: 10 tests, six expected missing-behavior failures. Storm integration RED: 25 tests, two opt-in/persistence failures. After integration, 30 focused tests had only the new exposed-face winding regression failing; winding was corrected afterward and awaits rerun.
- Validation interruption: automatic approval review could not execute the coastal GameTest because the account usage limit prevented review; no action ran and no settings changed. User resumed after the reported reset time; approved isolated coastal test launched normally.
- Coastal fixture corrections: the original flat seed declared the wrong relative waterline. The corrected lower seed reproduced the missing vertical start. A bounded mean-waterline guard and coastal-only vertical connection were implemented. Later heightmap diagnostics identified the shared template roof above the four-block clear; the fixture now clears its bounded 20-block enclosure. Recession now waits through normal claim aging instead of retrying at the same game tick.
- Final review: one read-only reviewer, no builds or edits by that agent. All three Important findings were reproduced and fixed: live ownership replaces lifetime projection receipts for surge targeting; one absolute coastal mean surface is shared by mesh/immersion/API; lip velocity alone seeds ballistic acceleration. The rediscovery flicker was also reproduced and fixed by retaining cache leases/geometry/cadence. No review finding was deferred.
- Additional loaded regression reproduced generated-surface lookup ignoring actual temporary water above its baseline. Connected canonical flood lookup is bounded to four cells; the repaired 33/33 water-safety GameTests passed (32-second wrapper), with normal save/shutdown.
- Final coastal world fixture passed (33-second wrapper): OCEAN_IN 1,572,880,926; OCEAN_OUT 1,571,856,926; projection out 12,288,000; projection return 11,264,000 milli-units; external withdrawal 1,024 canonical units; regional residual zero and all remaining detailed claims receded. These round trips are regional serialization tests, not a separate disk restart acceptance.
- Broad regression: 881 water/weather/mixin tests across 194 suites, zero failures/errors/skips; offline waterSimulationTest 241 tests/45 suites, also zero failures/errors/skips. Suites overlap.
- Full test/build/benchmark succeeded in 1 minute: root 1,895 tests/478 suites, zero failures/errors, five documented pre-existing capability/opt-in skips. Subproject test tasks were up-to-date. Normal packaging succeeded.
- Final arithmetic benchmark: Temurin 21.0.6, 32 processors exposed, 512 MiB heap, three warmups and seven rounds. Each round: 105,600 regional intervals plus 100,000 jet/impact samples; median 12.319 ms, slowest 13.002 ms, residual zero, final projected claims zero. Not gameplay FPS/MSPT/GPU performance.
- Loaded waterfall query fixture: 128 warmups, five 128-query rounds, median 7,038 ns/query and slowest round 15,891 ns/query. This is a small in-world public-API fixture, not an entity-count or whole-server budget guarantee.
- Ruling: optional-mod/visual/multiplayer/long-duration performance acceptance is judged from actual runs only; numerical, ownership and shader fixtures do not certify that runtime matrix. Dedicated-server startup retained the repository's scoped compiler workarounds and one session-only Guava exclusion; no general native-crash fix is claimed.
- Live acceptance: native renderer water/foam and F3+T reload passed, followed by normal save/shutdown and exit 0. Sodium 0.8.12 was activated from the complete published JAR through a disposable mods directory; actual F3 renderer identity, water/foam and reload passed, with normal save/shutdown and exit 0. Earlier inactive-classpath launches were not counted as Sodium acceptance.
- Iris 1.8.14 beta 1 plus Distant Horizons 2.4.3-b loaded the copied world; Complementary Unbound r5.6.1 activated and visibly rendered the scene. Before further diagnostics/reload/travel, the user requested to do remaining hands-on testing themselves. Stop testing, save/close the owned client and hand off the exact remaining coverage in docs/watersystem/water-dynamics-runtime-acceptance.md.
