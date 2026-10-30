# Reactive reservoir columns implementation plan

**Goal:** Run a bounded multi-depth, stepped dam through the opt-in physical surface solver with exact canonical accounting.

**Architecture:** Extend the existing controller to own disjoint vertical column footprints. Derive each substep's column depth from canonical cells, reuse shared inertial/CFL math with explicit face sills and ceilings, and transfer accepted parcels from donor tops into receiver bottoms. Keep the existing queue, config gate, attachments, renderer, provenance and lifecycle owners.

**Spec:** Original Hybrid Water Engine 2.0 request, `../../watersystem/hybrid-water-engine-2.md` and the user's approved next-step sequence.

## Bounds and decisions

- Remain default disabled. Retain the existing config key for compatibility.
- Initial reservoir admission: at most 32 horizontal columns, eight cells per column and 256 canonical capacity positions per region. Unknown/unbounded frontiers and ambiguous multiple vertical openings fall back to voxel flow.
- Admit a contiguous supported column from an actual horizontal opening; do not join disconnected rooms merely because they share X/Z. Full solid floors remain required.
- Numeric surfaces are bed elevation plus canonical depth. Face overlap bounds discharge; a dry high sill cannot spend lower water. Outgoing faces share donor availability above their sills, and incoming faces share snapshot capacity.
- Transfer only accepted canonical units. Drain highest wet donor cells; fill lowest available receiver cells. Preserve parcel temperature and ordinary provenance. No interpolated volume writeback, extra reservoir, generated ocean import or new save format.
- Revalidate packed columns and the terrain envelope. Growth outside cached vertical bounds invalidates the old footprint before replacement. Every owned voxel is excluded from voxel simulation during a physical evaluation.
- Use the original controlled dam fixture in a separate opt-in test, requiring physical membership before and after breach. Attachment round trips remain distinct from actual disk unload/restart tests.
- Main agent implements and reviews; serial Gradle only, no commits or remote changes, preserve unrelated playtest work.

## Tasks

- [x] Require physical participation in the dam fixture; observe the flat-only implementation fail.
- [x] Add numerical regressions for stepped rest, high sills, shared overtopping volume, deep capacity and bounded openings; implement column-capable flux planning.
- [x] Add accepted partial column-transfer tests and implement the top-to-bottom canonical bridge.
- [x] Implement bounded column topology and integrate disjoint footprints, resampling, growth invalidation and existing lifecycle handling.
- [x] Run opt-in and default GameTests serially; exercise unsupported geometry, layer gaps, overflow and exact dam balances.
- [x] Run relevant unit/regression suites, package, review diff and record actual results and remaining acceptance.

## Review focus

No flow through a closed sill; no shared parcel spent by multiple outlets; no mutation of unknown chunks; no independent ownership of two rooms at one X/Z; no stale region when a column grows; partial receiver rejection must preserve the remaining donor and temperature. Deep/multi-room three-dimensional flow outside the verified envelope remains voxel-owned.

## Result

The new physical dam assertion failed on the old flat implementation. Column
integration passed the 17-test profile; overtopping and loaded-boundary cases
expanded it to 19. A new 20th test reproduced a full-depth admission bug caused
by unused air headroom. Floor-first capacity selection repaired it; all 20
opt-in tests passed. The default baseline passed 30/30 after integration.

Final root water/weather coverage passed 592 tests; the overlapping offline
suite passed 211, with successful packaging. Server runs used the documented
session-only JVM exclusion. See [measured results and remaining work](../../watersystem/reactive-reservoir-columns.md).
Actual disk/restart and subsequent weather/river/client acceptance remain outside
this bounded reservoir checkpoint.
