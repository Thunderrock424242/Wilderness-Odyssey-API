# OPC package reorganization validation

Date: 2026-10-17. Follow-up to the OPC consolidation: place shared performance implementations under `com.thunder.wildernessodysseyapi.opc`. Work continued in the same `in-dev` checkout, preserving the preceding uncommitted changes. No commit, push, release, config rewrite or world migration was performed.

## Result

**148 production Java files and 49 test files moved.** The canonical layout and downstream import mapping are in [opc-packages.md](opc-packages.md). An additional package index is at `src/main/java/com/thunder/wildernessodysseyapi/opc/package-info.java`. Sixty empty former package directories were removed nonrecursively; no files or non-empty directories were deleted during that cleanup.

| Check | Result |
| --- | --- |
| `:compileJava` | Passed through the checked-in wrapper and Java 21. Three existing OpenGL/water deprecation-removal warnings appeared during recompilation. |
| `:performanceDiagnosticsTest` | Passed: 176 tests in 52 suites; zero failures, errors or skips. The task now selects all OPC tests, retaining its architecture/mixin checks. |
| `:test` | Passed: 1,623 tests in 434 suites; zero failures/errors, four existing skips. |
| `:build` | Passed. Full tests were explicitly requested because this repository wires build to assembly. |
| `:runGameTestServer` | Passed: both required tests in the existing coordinator namespace. All dimensions saved and server shut down normally. |
| Production relocation comparison | All 148 moved production files have unchanged bodies after normalizing package declarations, imports, qualified names and source-path references against their pre-move copies. |
| Packaged namespace regression | Passed against the actual 5.0.0 JAR: representative OPC implementations present; no legacy implementations under former roots; only the deprecated coordinator forwarding alias allowed. |
| Legacy alias session regression | Passed: old and new coordinator entry points observe and clear the same OPC session. |
| Architecture boundaries | Passed at the new locations, including native chunk ownership and the existing OpenGL/client compatibility boundary. Missing guarded OPC directories now fail the chunk boundary test instead of silently skipping inspection. |
| Reference/whitespace review | No former production Java package references remain except the intentional legacy alias and its tests. `git diff --check` and separate new-file whitespace checks passed. |

The full-suite skips remain three conditional symbolic-link tests and the separately enabled heavy bunker fixture. No test was removed or disabled for this reorganization. The suite grew by the namespace artifact regression and the alias session regression.

## Commands actually run

The package artifact regression was first observed failing on the old layout:

```powershell
.\gradlew.bat :performanceDiagnosticsTest --tests '*OpcPackageLayoutTest' '-PcodexBuildDir=.codex-build' --no-parallel --no-daemon
```

After moving the files and updating references:

```powershell
.\gradlew.bat :compileJava '-PcodexBuildDir=.codex-build' --no-parallel --no-daemon
.\gradlew.bat :performanceDiagnosticsTest '-PcodexBuildDir=.codex-build' --no-parallel --no-daemon
.\gradlew.bat :test :build :runGameTestServer '-PcodexBuildDir=.codex-build' -PcoordinatorGameTests -PauditGameTestRun=opc-package-20261017 --no-parallel --no-daemon
```

These ran sequentially. The final test/build/GameTest invocation exited 0 with `BUILD SUCCESSFUL in 2m 6s`. No Gradle, NeoForm or Minecraft development processes were run concurrently.

Local evidence remains in ignored output:

- `.codex-build/opc-package-red.log`
- `.codex-build/opc-package-compile.log`
- `.codex-build/opc-package-focused.log`
- `.codex-build/opc-package-acceptance.log`
- `.codex-build/run-audit-opc-package-20261017/logs/latest.log`
- `.codex-build/test-results/performanceDiagnosticsTest/` and `.codex-build/test-results/test/`
- `.codex-build/opc-package-moves.json`: exact source/destination manifest
- `.codex-build/opc-package-before/`: pre-move copies preserving the dirty working-tree content
- `.codex-build/review-opc-package-moves.ps1`: production body comparison

An independent relocation review was attempted but unavailable because the reviewer hit an account usage limit. The main agent completed source/reference review, comparison of all moved production files, artifact inspection and automated validation. No independent-review success is claimed for this follow-up.

## Scope and compatibility

This changes Java package names and source locations. Downstream Java integrations must update imports and recompile; full binary compatibility for moved classes is not claimed. The deprecated old coordinator entry point is stateless and does not preserve old names for its moved argument types. The package guide gives that boundary explicitly.

No TOML key/default, filename, packet/resource ID, command, save schema, feature ownership or lifecycle order changed. The existing Tick Engine compiler exclusion follows its new class name. Shared simulation orchestration moved, while ecology integration/model participants stayed feature-owned. Client GPU/loading classes retain their logical-side separation and specialized lifecycle. The native-save implementation was relocated without changing its body or ownership safeguards.

The native GameTests continue to prove critical/deferred Data Engine admission and native-save readiness/chest persistence/failure retry in an isolated transformed fixture. They do not establish real client rendering, actual-pack compatibility, multiplayer, copied-world acceptance or measured performance gains. Those acceptance boundaries remain as documented in the original [OPC validation](opc-validation.md).
