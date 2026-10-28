# Canonical Water Barrier Lifecycle Implementation Plan

**Goal:** Make accepted direct barrier changes wake existing finite water and
prove a bounded inlet/basin/breach/drainage lifecycle with exact canonical units.

**Architecture:** Keep `CanonicalWater`, `WaterVolumeChunk`, the existing finite
flow queue/ticker, and `notifyTerrainChanged` as owners. Add a narrow successful
`LevelChunk#setBlockState` observer because NeoForge placement/break events do
not cover direct automation writes. The observer only admits relevant loaded
canonical/generated neighborhoods and performs no projection or transfer itself.

**Tech stack:** Minecraft 1.21.1, NeoForge 21.1.x, JDK 21, existing Mixin/JUnit/GameTest tooling.

**Spec:** User's Hybrid Water Engine 2.0 request; `docs/watersystem/hybrid-water-engine-2.md`.

## Constraints and review focus

- Preserve one water authority, accepted-destination accounting, and loaded-only mutation admission.
- Ignore no-op, provisional/restoring, internal projection, starter generation,
  fluid-only, off-thread and dedicated-client writes as applicable.
- No terrain scan, new scheduler, new inventory, new save format, or config default change.
- Use normal Gradle serially; no remote Git operations or unrelated edits.
- Attachment serialization/re-registration is not a disk eviction/server-restart test.
- This is a canonical voxel barrier checkpoint, not completion of the physical height-field regions.

## Tasks

- [x] Compile current baseline; add GameTests for direct flags-2 breach wake,
  hidden reservoir release, no-op/provisional writes, and a finite inlet/basin
  conservation lifecycle using natural server ticks.
- [x] Run the new profile and observe the missing automatic wake fail.
- [x] Add the successful chunk-write observer and feature-owned notification
  admission through existing owners; prevent internal-write feedback.
- [x] Compile, run focused water tests, and rerun the loaded-world safety profile.
- [x] Extend cross-chunk/generated-boundary and persistence regression coverage
  where it adds meaningful proof without force-loading terrain.
- [x] Review scoped diff, package, and document measured results and remaining acceptance.

**Result (2026-10-28):** 199 focused offline tests, 30/30 required loaded-world
GameTests with the documented session-only JVM compiler exclusion, and a final
580-test root water/weather pass with successful packaging. Review also added
and reproduced a detached-chunk regression before requiring loaded-instance
identity. See [measured results and remaining acceptance](../../watersystem/canonical-dam-checkpoint.md).
