# Canonical water and Sable 2.0.6 chunk-access stall

## Evidence and correction

The supplied `latest(10).log` records six ModernFix watchdog dumps for one
integrated-server tick, from 40,001 ms to 292,841 ms. Every dump contains
`CanonicalWater.projectCompatibility -> LevelChunk.setBlockState -> Sable
RapierPhysicsPipeline.handleBlockChange -> VoxelNeighborhoodState.isSolid ->
PointedDripstoneBlock.getShape -> Supplementaries getOffset ->
LevelAccelerator.grabChunkFast -> ServerChunkCache.getChunk`. The thread waits
inside the chunk executor, with nested chunk-access frames. This is evidence
of blocking chunk access, not proof of a C2ME dependency cycle, a physics CPU
overload, or repeated water mutations during that tick.

The exact Sable 2.0.6 tag (`ad8b4d377741fbd923f4d863b2cf9e462daff9a6`) shows two
contributing conditions:

- [VoxelNeighborhoodState](https://github.com/ryanhcode/sable/blob/mc1.21.1-2.0.6-neoforge/common/src/main/java/dev/ryanhcode/sable/physics/chunk/VoxelNeighborhoodState.java)
  accepts a position but evaluates both solidity and fullness at `BlockPos.ZERO`
  through caches keyed only by block state.
- [LevelAccelerator](https://github.com/ryanhcode/sable/blob/mc1.21.1-2.0.6-neoforge/common/src/main/java/dev/ryanhcode/sable/util/LevelAccelerator.java)
  falls back to `Level.getChunk` when its non-blocking full-chunk lookup fails.
  Supplementaries' planter-offset mixin reads below the supplied shape position.
  With that accessor, a shape evaluation can therefore request an unavailable
  origin chunk even when the water mutation's own chunk is loaded.

The exact requested chunk coordinates and why its future remained unresolved
are absent from the log. The origin request is an inference from the matching
version's source and stack, not a coordinate captured by the watchdog.

## Ownership and invariants

`CanonicalWater` and `WaterVolumeChunk` still own fixed-point volume. Ordinary
destination acceptance still determines the source debit; an unavailable
destination accepts zero. A local source with an unavailable callback halo is
deferred before any part of its transfer. It remains awake and retains its
volume, momentum, temperature and flood provenance.

Direct canonical sets preserve their desired final cell even when projection
must wait. `FLAG_PROJECTION_PENDING` keeps that intent in the existing cell
flags. A pending zero-volume cell is retained until physical water is removed,
preventing the stale block from being imported as new water. Multiple writes
to one position replace its desired cell; the work index holds one entry for
that position and retries the latest state. Existing unchanged-projection
deduplication remains in place. Normal ready writes do not create an extra
pending-flag revision.

This adds one flag to the existing format-1 cell payload, without changing its
layout or requiring a world conversion. Old saves have no pending flag and
load normally. A projection backlog should be drained with this version before
downgrading: older runtime code does not understand the pending-air intent.
Generated dry overrides retain their existing distinct meaning.

## Chunk access and optional physics bridge

`WaterMutationSafety` uses only `ServerChunkCache.getChunkNow` for admission.
The two-block callback halo covers Sable's adjacent-voxel classification and
its immediate neighbors. The flow ticker checks a three-block halo, covering
each immediate transfer destination's callbacks before starting a transaction.
Border water pauses until Minecraft naturally supplies that halo; it does not
issue tickets, join futures, or generate chunks.

`SableWaterShapeMixin` is a documented optional integration with these exact
Sable methods: `VoxelNeighborhoodState.isSolid(BlockGetter, BlockPos,
BlockState)` and `isFullBlock(BlockGetter, BlockPos, BlockState)`. The existing
mixin plugin gates the target by class resource; no Sable class is linked from
common water code. There is no public Sable hook for supplying the position
to those state-only memoized calculations, so this narrowly scoped mixin is
necessary for the demonstrated seam.

Only inside a water-owned block write, those classifiers evaluate the supplied
position through a loaded-only shape view. They retain the moving-piston
solidity special case. Rapier block-change notifications, TreePhysics' call
wrapper, and Minecraft's original update flags remain active. Calls outside
that scope keep Sable's original behavior. Reentrant scopes restore their
caller in `finally`, including when a callback throws.

The loaded view returns air for an absent column, matching a bounded region
view. Known Sable/Supplementaries neighbor reads are admitted before mutation.
An arbitrary mod shape that reads beyond the admitted halo gets this boundary
approximation instead of synchronously loading terrain. That case needs
separate compatibility testing; this repair does not promise exact collision
answers for unloaded distant terrain. Sable's independent collider bakery
already uses its own non-loading synthetic block getter and is unchanged.

Water-driven erosion and reversible terrain restoration use the same admitted
mutation scope. The direct fluid-write reconciler admits its physical write
before committing canonical displacement. External player/automation writes
remain subject to their normal placement and snapshot rules.

## Scheduling and lifecycle

The existing level ticker owns all processing. Its work index now contains
only existing canonical cells, partitioned by naturally loaded chunks. Each
cell appears at most once; it cannot grow by enqueueing arbitrary air positions.
The index is bounded by the canonical attachment's finite packed positions,
and the level index holds only participating loaded water chunks. Each poll
examines at most eight chunks and eight cells per chunk. Deferred entries rotate
fairly and use a ten-tick cooldown, preventing busy retries and head-of-line
starvation.

Chunk unload drops the level's reference. Chunk load restores work from awake
and pending cells. Covered displacement residuals start sleeping; an explicit
terrain wake clears that flag and persists through deferral and reload. A retry
that finds the solid still present returns the parcel to sleep. Old awake
reservoirs also get that one bounded retry. No block writes happen in the
load/unload hooks. Normal world saving
persists awake state and projection intent. Dimension unload clears runtime
indexes and diagnostics through the existing water cleanup owner. A stopping
server admits no new water mutation. No worker or executor is introduced.

`localFlowCellsPerTick` still limits cell transactions. `localFlowBudgetMillis`
adds a configurable soft elapsed-time budget (default 5 ms per dimension,
range 0.25–25 ms), checked between cells. It cannot interrupt a third-party
callback already in progress; prevention of loading happens before the write.

## Diagnostics and repeatable verification

The existing `/wowater budget` output includes current flow-pass
projection, deferral, deduplication and availability counts, mutation time,
maximum mutation time and maximum flow-pass time. `/wowater summary` includes
the pending cell count.
With the existing watershed `debugLogging` enabled, completed mutations above
50 ms emit dimension/position/state context at most once per 200 ticks per
dimension. There is no per-cell logging and ModernFix's watchdog is unchanged.

Use JDK 21 and one Gradle process at a time:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat waterSimulationTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat runGameTestServer '-PcodexBuildDir=.codex-build' -PwaterSafetyGameTests -PwaterSafetyProfile=baseline --no-parallel
```

`waterSafetyProfile` accepts `baseline`, `sable`, `c2me`, or `sable-c2me` and
selects a distinct disposable `.codex-build/run-water-safety-<profile>` world.
To run optional combinations, put the selected test mod JARs and their
dependencies in that profile's `mods` directory. This does not modify the
launcher profile or any player world.

The runtime classifier probe uses a registered shulker box opened at the real
test position and deliberately supplies an accessor that throws instead of
loading a chunk. Outside the water scope it reproduces Sable's origin-shaped
unsafe access. Inside the water scope it verifies solid classification, the
opened box's non-full collision shape at the actual block entity position, and
absence of any call to that loading accessor. This tests the identified seam without manufacturing
a real multi-minute deadlock. With Sable absent that optional probe exits;
the canonical round-trip and scope-lifecycle tests still run. Additional runtime
checks exercise downward and lateral conservation, solid displacement,
repeated projection writes, no-delta direct writes, and deferral at a naturally
loaded/unloaded border. The lifecycle fixture verifies attachment serialization
and runtime-index unload/load hooks separately; it does not naturally evict and
replace the live chunk attachment.

Remaining live acceptance must use a copy or disposable world: loaded interior
flow; unloaded borders; repeated load/unload; dripstone and planter offsets;
large finite-water movement; repeated writes; Save and Quit with pending work;
integrated and dedicated servers; and each Sable/C2ME combination. Compare
exact before/after water budgets and pending counts, record maximum flow and
mutation time, and confirm no new watchdog stack follows this loading path.
The original full modpack/world hang and representative gameplay performance
are separate from these focused runtime proofs.

## Validation record

Recorded on 2026-10-08, using JDK 21.0.12 and NeoForge 21.1.256:

- `compileJava -PcodexBuildDir=.codex-build --no-parallel`: completed successfully.
  The final focused test run also recompiled the updated production source.
- `waterSimulationTest -PcodexBuildDir=.codex-build --no-parallel`: **128 tests,
  zero failures/errors**, `BUILD SUCCESSFUL` in 25 seconds. Deferred dry intent,
  optional-target absence, retry cooldowns and reservoir persistence regressions
  were observed failing before their corresponding fixes.
- `runGameTestServer -PcodexBuildDir=.codex-build -PwaterSafetyGameTests
  -PwaterSafetyProfile=<profile> --no-parallel`: all four commands exited
  successfully, including normal headless server saving/shutdown.

| Profile | Sable 2.0.6 | C2ME 0.4.0-alpha.0.122 | Required GameTests | Fixture suite duration |
| --- | --- | --- | --- | --- |
| `baseline` | Absent | Absent | 10 passed | 2.794 s |
| `sable` | Present | Absent | 10 passed | 1.811 s |
| `c2me` | Absent | Present | 10 passed | 1.164 s |
| `sable-c2me` | Present | Present | 10 passed | 3.981 s |

The Sable profiles also load TreePhysics 2.4. Supplementaries 3.9.9 and
Moonlight 3.7.1 are present through the project's normal dependencies; the
Sable profiles use matching copied launcher JARs. These are focused development
profiles, not the complete original modpack. In Sable-absent profiles the
optional classifier probe exits without making its Sable assertions; the other
nine tests execute. Suite durations are test evidence, not a representative
gameplay performance comparison.

An initial broader offline selection ran 341 tests with six initialization
failures in existing `StructureWaterMarkerAdapterTest` (two),
`VanillaWaterBucketCompatibilityTest` (one), and `ErosionSavedDataTest` (three),
in the vanilla bootstrap/`SoundEvents` initialization path. That broader run
failed; the final offline selection is limited to the applicable canonical,
flow, hydrology, SPH and compatibility tests. No full test-suite pass is claimed.
The initial runtime namespace registration and post-freeze custom-block fixture
were corrected before the successful matrix above.

Logs and the JUnit HTML report are under the isolated build directory:

- `.codex-build/water-safety-unit-console.log`
- `.codex-build/water-safety-<profile>-console.log`
- `.codex-build/reports/tests/waterSimulationTest/index.html`

Normal Gradle packaging produced
`.codex-build/libs/wildernessodysseyapi-5.0.0.jar`. Its entries were inspected
for `SableWaterShapeMixin`, `WaterMutationSafety`, `WaterCellWorkQueue`, the
mixin configuration and the isolated test template. SHA-256:
`130e5572376e4959efd6456d862d13d0bc3643fefcc74a4a98012720ef1beb87`.
This artifact includes the current workspace's other in-progress source
changes. The standalone full `build` task was not run.

**Still unverified:** the original full-modpack hang/world, natural repeated
chunk eviction/reload, active-water Save and Quit, integrated-server and client
gameplay, ordinary dedicated-server multiplayer, and representative large-water
performance/long-running stability. The throwing-accessor probe proves removal
of the identified unsafe classifier read; it does not reproduce the original
multi-minute unresolved chunk future or establish why that future stalled.

## Implementation map

Production packages below are relative to
`src/main/java/com/thunder/wildernessodysseyapi/`.

| Files | Responsibility |
| --- | --- |
| `watersystem/water/compat/neoforge/WaterMutationSafety.java` | Non-loading neighborhood admission, scoped shape view, mutation timing and diagnostics. |
| `mixin/SableWaterShapeMixin.java`, `mixinconfig/WildernessMixinConfigPlugin.java`, `src/main/resources/mixins.wildernessodysseyapi.json` | Optional classifier bridge and existing target-presence gating. |
| `watersystem/water/compat/neoforge/WorldFluidMutationReconciler.java` | Scope canonical, direct solid, same-volume and compatibility-disabled physical writes while retaining flags and recursion limits. |
| `watersystem/water/volume/CanonicalWater.java`, `WaterVolumeChunk.java`, `WaterCellWorkQueue.java` | Loaded-only authority reads, persisted projection intent, coalesced retry work and durable reservoir wakes. |
| `watersystem/water/volume/WildernessWaterAuthority.java` | Generated-water authority lookup uses the already-loaded chunk. |
| `watersystem/water/fluid/WildernessFluidRegistry.java`, `watersystem/water/config/WaterSimulationConfig.java` | Transfer-halo admission, bounded elapsed-time processing, chunk lifecycle hooks and diagnostics. |
| `watersystem/water/erosion/ErosionManager.java`, `watersystem/water/hydrology/TemporaryFloodManager.java` | Admit and scope water-driven terrain mutations. |
| `watersystem/water/compat/neoforge/WaterMutationSafetyGameTests.java` | Ten isolated runtime scenarios, reusing existing flow/displacement fixtures. |
| `src/test/java/.../WaterMutationSafetyTest.java`, `WaterCellWorkQueueTest.java`, `WaterVolumeChunkTest.java`, `CanonicalWaterDisplacementTest.java`, `WildernessMixinConfigPluginTest.java` | Admission, fair cooldowns, durable intent/wakes, conservation and optional-absence regressions. |
| `build.gradle` | Focused offline test task and separate GameTest profiles/template alias. |

The headless server tests prove the known Sable classifier seam. They do not
establish correctness for arbitrary external callbacks that themselves mutate
canonical water during another cell's transfer, or for arbitrary shapes that
reach beyond the admitted neighborhood. Those require their own concrete
compatibility cases rather than an unconditional reentrancy guarantee.
