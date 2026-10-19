# River generation continuation

The user requested continuing the complete river system. Execute inline, reusing
the validated planner, canonical water, generated spans and current owners.

## Architecture

Minecraft's FEATURES step guarantees CARVERS terrain within one chunk and
structure starts within eight. Capture immutable terrain at the end of carvers.
The first river invocation owns a fixed 2x2-chunk catchment wholly inside that
permitted write radius; preflight the complete assembly before any placement.
Only new ProtoChunks may participate. Accept a route only when it reaches actual
receiving water or a sealed basin. Do not fabricate tile-border outlets.

This provides connected local tributaries and mountain pools; it does not claim
a global terrain oracle or unbounded catchments. Existing large waterways remain
receivers. Larger catchments need a separately certified generation contract.

## Tasks

- [x] Pure terrain/catchment/raster tests: sealed elevated pools, joined downhill
  channels, receivers, internal chunk crossings, order/concurrency, cavity veto
  and whole-assembly work limits; observe RED before implementation.
- [x] Compact generation terrain capture and cohesive bounded lake/channel plan.
- [x] Registered generation pass, whole-tile structure/terrain/write preflight,
  canonical generation mapping and opt-in common settings. Supersede overlapping
  glacial stream carving while the new pass is enabled.
- [x] Compact persisted/synchronized reach metadata through existing attachments
  and current/render owners, retaining volume ownership and SI conversion.
- [x] Supported vertical drops and biome-aware beds; shared current reaches existing bounded cosmetic hooks.
- [x] Focused unit tests, disposable GameTests, independent review, package.

## Constraints and ledger

- Work from saved river branch commit 86bcae9b in this managed worktree. The
  original checkout changed back to in-dev during exploration; preserve it.
- One repository Gradle process at a time; use isolated .codex-build output.
- No recursive chunk requests, synchronous future waits, worker world mutation,
  new scheduler, second water inventory, old-world recarving or security changes.
- Keep source changes modular and report actual feature/compatibility limits.

## Verification result

165 water tests, 325 rendering tests and 31 required GameTests passed. Root
`:build` and final package checks passed; three independent review findings were
fixed. See `docs/watersystem/river-network.md` for exact scope and deferred work.

## PR isolation

Create the PR from `codex/wilderness-river-network-pr`, based directly on `in-dev`
commit `a9b56c665406931b977e628cef251fc8eb5552f7`. The original feature branch also
contains prior OPC/WAGE/coastal commits, so it is preserved separately. Port only
the reviewed river source/tests/resources and river-specific config/build hunks.
Revalidate the clean checkout before publishing; its results are recorded in the
river-network document.
