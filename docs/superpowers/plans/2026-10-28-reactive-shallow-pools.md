# Reactive shallow-pool implementation plan

**Goal:** Add the first opt-in physical surface region without adding water inventory or a scheduler.

**Architecture:** The existing canonical queue admits closed, flat, one-voxel-deep pools of at most 16 cells. A disjoint membership index retains only face momentum and timing. Reuse the coastal local-inertial face calculation; reconcile integer requests through canonical accepted-credit transactions. All other geometry falls back to existing voxel flow. This is an initial Phase 3 subset, not general height-field/dam completion.

**Tech stack:** Minecraft 1.21.1, NeoForge, JDK 21, existing Gradle/JUnit/GameTest support.

**Spec:** Original Hybrid Water Engine 2.0 request and `../../watersystem/hybrid-water-engine-2.md`.

## Decisions and boundaries

- Default disabled server config; maximum active regions is bounded. No dependency, authority mode, packet or persistent schema change.
- Sample only admitted loaded chunks; full solid floor, open air above, air/owned plain projections inside, closed dry solid sides. Reject untracked generated spans, external fluids, temporary claims, residual displacement and pending projection.
- Numerical depth is refreshed from canonical units every substep. Requests cannot spend incoming water during the same substep. Integer remainders stay canonical; rejected credits cannot debit sources.
- Region members bypass voxel flow during the region evaluation. Membership is disjoint, and a region advances at most once per world tick. Existing cell/time budgets remain the caller.
- Terrain changes invalidate indexed neighbors even when no water is adjacent to the edit. Invalidation wakes existing water; sleeping, eviction, disablement, chunk and level cleanup discard only caches.
- Initial support deliberately excludes deep/stepped/open-ended pools and every ocean boundary. These remain future extensions after this bounded path is proven.
- Main agent implements and validates serially in the existing checkout, preserving prior and unrelated edits. No commits, remote changes or parallel Gradle.

## Tasks

- [x] Add failing tests for shallow face flux: still water, exact integer donor/capacity limits, no same-step respending, finite momentum, and denied receipts.
- [x] Extract shared coastal face math; add the integer request planner and canonical flux bridge, with focused tests.
- [x] Add bounded terrain admission, disjoint region membership, retained CFL time, sleep and lifecycle cleanup; integrate into the current canonical ticker and terrain notifications.
- [x] Add isolated opt-in GameTests exercising real server ticks, same-tick suppression, topology invalidation, rejection/fallback and lifecycle inventory preservation.
- [x] Compile, run focused numerical tests, opt-in and baseline GameTests serially, then root water/weather tests and packaging.
- [x] Review the focused diff and record measured results, explicit unsupported cases and remaining engine acceptance.

## Review focus

Unknown terrain must never load; geometry growth must fall back without retaining false ownership; multiple outlets must share donor availability; target rejection must leave source units and temperature intact; disabled or unloaded regions must leave canonical inventory and pending work recoverable. Test each before reporting this checkpoint complete.

## Execution result

17 initial focused numerical/coastal tests passed. Final validation: 12/12
opt-in GameTests and 30/30 baseline GameTests using the documented session-only
JVM exclusion; 586 root water/weather tests and 205 overlapping offline tests
passed, with successful packaging. Empty-pool and unsupported-provenance cache
leaks were reproduced before their callback repairs. Main-agent review followed
the repository's ordinary-work guidance; no subagents or remote operations.

See [the checkpoint report](../../watersystem/reactive-shallow-pools.md) for
commands, measured results, test-isolation corrections and remaining scope.
