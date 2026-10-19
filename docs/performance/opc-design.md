# Odyssey Performance Core design and implementation plan

User brief: consolidate existing performance infrastructure, implement evidenced improvements, preserve public APIs/default gameplay/native ownership, and validate with Java 21 and the wrapper. Work in the current checkout. No measured speedup is assumed.

## Ownership

OPC replaces the implementation of `PerformanceCoordinator`; the old class forwards to it. `CoordinatorGovernor` remains the pure hysteresis/watchdog policy. OPC owns one measured optional-work ledger and the production deferred scheduler. It owns no executor, native chunk lifecycle, gameplay state, or persistence queue.

Both public deferred scheduler APIs become adapters over `DeferredWorkScheduler`. The production instance has two compatibility lanes: Tick and Background. Lane-specific bounds, coalescing, expiry, critical-priority semantics and enablement survive; they are separate contracts, not separate implementations. Empty lanes allocate no task queues and perform no queue scans. External callers constructing a scheduler still receive an isolated scheduler. Cooperative steps remain non-preemptible and must be bounded by callers.

`AsyncComputeManager` borrows `AsyncTaskManager` CPU workers. Its configured worker count becomes a lane admission limit, not another pool size. Local outstanding/result bounds and lifecycle generation guards remain. Shutdown invalidates its lane; it never shuts down shared workers. Required IO and Data Engine result application are not disposable admission work.

Data Engine retains authoritative dirty tracking, scheduled gameplay callbacks, interest subscriptions, completion queues and typed network delivery. OPC constrains optional processing time only. Critical work and network flushing keep their established paths. Legacy elapsed-time simulation and gameplay ownership stay unchanged.

## Configuration and rollback

Add `performance.opc.mode`: `LEGACY` (default, honors the existing coordinator opt-in), `OFF`, `OBSERVE`, or `ADAPTIVE`. Existing `performance.coordinator` tuning keys and `performance.safeMode` remain supported in the unified SERVER file. This deliberately avoids a destructive rename or two conflicting sets of tuning values. ADAPTIVE opts into shared Data/Tick/Background optional budgeting; LEGACY restores previous admission behavior. Safe mode stays true; saveDrain stays OFF. CLIENT and COMMON config ownership does not change.

## Budget contract

Use actual monotonic elapsed time, never add configured allowances. Legacy coordinator methods remain compatible. Adaptive mode caps each optional module's per-tick share, debits a single total ledger, and checks the current tick's wall allowance. Pressure/recovery comes from the existing governor. Required/critical work is outside admission, but its elapsed time reduces wall headroom. Native and third-party work are never rescheduled. A step can overrun because Java callbacks cannot be preempted; the ledger and watchdog stop subsequent admissions.

## Implementation tasks

- [x] Baseline compile and focused tests; record existing failures.
- [x] Add behavioral regressions for shared CPU ownership, stale-session publication, scheduler fairness/expiry/bounds, OPC aggregate accounting and wall deadline, and legacy config defaults; document subscriber publication checks for live acceptance.
- [x] Move coordinator implementation into `performance.core.OdysseyPerformanceCore`, retain forwarding API, add mode configuration and adaptive Data Engine integration.
- [x] Replace duplicated scheduler algorithms with one `DeferredWorkScheduler` implementation and two compatibility lanes; retain old APIs and tests.
- [x] Replace secondary compute executor with shared CPU admission and session-local result state. Verify failure isolation and shared-pool survival.
- [x] Share raw JVM samples while preserving each diagnostics consumer's GC delta window and RAM advisor GC notifications.
- [x] Guard debug snapshot production when there are no subscribers; skip empty compatibility batching drains.
- [x] Unify `/wo perf` status/queues/RAM diagnostics and preserve old commands. Document inventory, architecture, compatibility and hypotheses.
- [x] Compile, focused/subsystem/architecture tests, full test/build, coordinator GameTest, final diff and independent review. Report failures and live-pack acceptance separately.

## Review focus

Restart/reload during an interruption-insensitive worker must not publish stale results. Rejection must never execute work on the caller. Required callbacks/networking must remain independent from optional admission. Long periods of overload must not accumulate a burst of catch-up executions. Config defaults/legacy keys must preserve current behavior. No queue or callback may retain a prior server into a new session.

## Execution record

- Initial checkout clean on `in-dev`; Java 21.0.12.
- Baseline `:compileJava :performanceDiagnosticsTest`: compile passed; 127 tests, one failure in `AsyncComputeManagerTest.computedResultWaitsForExplicitServerThreadDrain` (counter publication can precede queue insertion).
- Ruling: implement inline in the current checkout as explicitly requested; audit agents may write their independent audit documents only. No automatic commits or branch changes.
- Ruling: preserve existing configuration paths as compatibility tuning rather than rewrite user TOML files. The new mode provides explicit opt-in and rollback.
- Ruling: the debug publisher's subscriber guard is source-verified; no live operator/client was attached. Subscriber/no-subscriber/unsubscribe delivery remains an explicit manual acceptance item rather than a claimed automated test.

- Final: 137 focused tests, 1,621 full-suite tests (four existing skips), packaging and both required native GameTests passed. See opc-validation.md for exact commands, resolved intermediate failures, and manual proof boundaries.
