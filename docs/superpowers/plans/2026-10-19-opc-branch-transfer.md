# OPC branch transfer implementation plan

> **For agentic workers:** Use superpowers:executing-plans to carry out this Git transfer inline. Keep Gradle execution under one owner.

**Goal:** Move the existing OPC consolidation and package organization from `codex/wilderness-river-network` to `in-dev`.

**Architecture:** Extract OPC and its consumers from mixed commit `f8d6635a`, retaining the existing package layout, behavior, configuration and lifecycle. Apply the focused patch to `in-dev` and reverse it on the river branch without rewriting history. Keep the river branch's WAGE JVM sampler and existing test with its rendering owner so the separate experiment still compiles.

**Tech stack:** Git, Java 21, NeoForge 21.1.262, checked-in Gradle wrapper.

**Spec:** User request in this chat to take OPC out of the river branch and put it into `in-dev`; existing OPC scope and package map in `docs/performance/opc-packages.md`.

## Constraints and review focus

- Preserve the river worktree's 33 pre-existing modified/untracked files. Only `WildernessConfigSpecs.java` overlaps the OPC patch; preserve its uncommitted river configuration edits while changing the performance imports.
- Exclude WAGE rendering behavior and coastal changes from the OPC transfer. Check the path manifest and mixed Gradle hunks.
- Keep legacy forwarding compatibility, package architecture guards, native save authority, opt-in defaults and client isolation intact. Run the existing focused performance tests.
- Update consumer imports and Gradle selectors with the source move. Compile both branches sequentially; check for unresolved OPC references on the river branch.
- Commit only the transfer on each branch. Verify the original river changes against their captured hashes and changed lines, and keep remote publication separate from this local transfer.

## Task 1: Transfer and validate

- [x] Capture branch heads and river worktree changes in ignored local output; build and dry-run a focused forward/reverse patch.
- [x] Apply the OPC patch to `in-dev`; compile and run focused performance/package tests.
- [x] Reverse the OPC patch on the river branch, retaining the WAGE sampler/test under rendering; compile and run affected tests sequentially.
- [x] Review both diffs and verify preserved river work. Record the transfer with focused commits on both branches; report commit IDs and publication status in the chat.

## Execution record

Initial heads: `in-dev=3892addbf1603849502ac0eb8cdd56eb98e71388`, river=`7097da119e9a172f6e57d83082273d52afe489e1`. Forward and reverse patch dry runs passed. The source OPC subtree has no changes after `f8d6635a` on the river branch.

No new gameplay or performance behavior is introduced; existing regression tests accompany the transfer. Git patch checks, source comparison and dirty-change preservation checks cover the relocation itself.

`in-dev` validation: `:compileJava`, `:performanceDiagnosticsTest` (176 tests, zero failures/errors/skips) and `:test` (1,647 tests, zero failures/errors, four existing conditional skips) passed through the checked-in Gradle wrapper with `'-PcodexBuildDir=.codex-build' --no-parallel`. The first unquoted PowerShell property argument arrived empty in the Aether subproject; quoting the argument corrected the invocation without source changes.

Source review confirmed that every transferred source/document blob matches its original OPC commit; only the OPC hunks of the mixed Gradle change were selected. Coastal and WAGE implementation changes are excluded. River source compilation passed after the removal, including the existing uncommitted river generation sources.

River validation: `:performanceDiagnosticsTest :renderingTest` passed with isolated output and sequential execution. Final review checked all 461 destination and 466 removal paths explicitly, compared the transferred/restored blobs against the original source/base commits, checked whitespace, and verified all 33 original river files by SHA-256 or their original uncommitted changed lines. No OPC Java references remain in the river source or Gradle build.

The transfer does not establish fresh client, GPU, multiplayer or saved-world runtime acceptance. Remote branches are not pushed as part of this local branch transfer.
