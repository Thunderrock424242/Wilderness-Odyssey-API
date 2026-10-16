# Code scanning filters and review

The existing CodeQL Security Analysis workflow runs CodeQL for Java and GitHub
Actions, plus Qodana JVM Community. Qodana's report is uploaded with the existing
`qodana-jvm` category.

## Minecraft analysis setup

Qodana extends the `qodana.starter` profile and uses Java 21. The
workflow passes a published Qodana Community 2026.2 image pinned by digest through
the supported `args: --image ...` input. Updating the image is a deliberate change
that should include checking the resulting report and its analysis diagnostics.

Before Qodana opens the Gradle project, `qodana.yaml` runs:

```sh
./gradlew :jar :aether-server:jar --no-daemon --no-parallel --max-workers=1 --console=plain
```

This prepares the generated NeoForge/Minecraft libraries, the mod JAR required by
the test classpath, and the standalone Aether gateway JAR. It uses the normal
Gradle pipeline; no generated dependency JARs are patched or reconstructed.
The disposable Qodana container deliberately uses the default build directories
because its Gradle importer references those paths. Local Codex validation must
still use `-PcodexBuildDir=.codex-build` and run only one Gradle process at a time.

The scan currently disables Qodana cache reuse and incremental PR mode so each
upload evaluates the complete project with freshly prepared dependencies. This
costs additional CI work but avoids retaining incomplete imported models during
this accuracy pass. Revisit caching after a fresh report confirms healthy project
resolution; keep full scans for changes to the analysis configuration.

After the SARIF upload, `check-qodana-health.mjs` checks every run's invocation
evidence. It fails the job if Qodana reports unresolved library roots, a suspended
sanity inspection, unsuccessful execution, or no invocation evidence. Findings
are uploaded first and remain available for investigation. This check detects the
specific analysis gaps observed in our reports; passing it is not proof of complete
Minecraft semantic understanding or a replacement for compiling and testing.

`codeql-config.yml` is connected explicitly to the Java CodeQL initialization.
It retains CodeQL's default security queries. The manual build compiles the mod
and Aether gateway; it determines Java analysis scope. `paths`/`paths-ignore`
settings are not used to pretend to exclude Java code compiled by those tasks.
The GitHub Actions CodeQL job remains separate.

## Performance filter

After a scan using the updated workflow uploads its results, use:

```text
is:open branch:in-dev tag:performance
```

[Open performance alerts on GitHub](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/code-scanning?query=is%3Aopen%20branch%3Ain-dev%20tag%3Aperformance)

[Open all code-scanning alerts](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/code-scanning?query=is%3Aopen%20branch%3Ain-dev)

Replace `in-dev` in the query when reviewing a different branch. This uses GitHub's
existing search field; it does not add a custom dropdown to GitHub's interface.
The tagging step also adds the performance-alert link to its Actions job summary.

For a view that omits conventional JVM test directories and GameTest classes from
the list, append these filters:

```text
-path:**/src/test/** -path:**/*GameTest*.java
```

This changes only the displayed list. All code remains scanned. GameTests live
under `src/main/java` in this project, so filtering only `src/test` misses them.

The tagging script reads Qodana's `codeQualityCategory` inspection metadata. Rules
classified as `Performance` receive the lowercase `performance` SARIF rule tag.
For reports without that category, a `Performance` segment in the inspection's
taxonomy path provides the fallback. Warning names and descriptions are not used
to guess categories. Qodana's classification can include threading inspections
such as busy waits, as well as minor suggestions such as unused assignments.

The script writes a separate GitHub upload report. The original Qodana artifact,
all findings, rule IDs, fingerprints, locations, severities, suppression state,
and upload category are preserved. Existing tags are retained, and running the
script again does not duplicate the new tag. Unknown inspection categories remain
in the full report without a performance tag.

This labels inspections already included by the Qodana profile. It does not enable
additional inspections or guarantee that every performance problem is detected.
Existing alerts gain the tag after a new upload; resetting or dismissing alerts
is not required. GitHub may retain an alert's original opened date when a scan
reports the same finding again.

## Reviewing findings

Review security, correctness, threading, and resource-lifecycle warnings against
the owning code and its callers. Minecraft and mixin lifecycle assumptions can
make generic Java suggestions inappropriate. Confirm false positives before
applying a narrow suppression with a reason.

The resource inspection remains enabled, including checks on factory-returned
resources. Its method exceptions in `qodana.yaml` retain the pinned inspection's
standard entries and add specific accessors for borrowed Minecraft worlds,
shared rendering backends, and the shared Minecraft I/O pool. The paired class
and method lists must stay aligned. They do not exempt all `Level`,
`RenderBackend`, or `ExecutorService` instances: constructors and other factories
still need an owner that closes what they create. In particular, a render state
scope returned by `captureRenderStateScope()` is still owned by its caller.

Project world-context accessors are included only when they retain a reference
to an existing server-owned world. Review ownership again before adding another
method or changing one of those accessors to create a resource.

Source exceptions cover the two-cow fixture's `SlowListContainsAll` assertion and
specific `DataFlowIssue` casts whose types match their declared `@Mixin` targets.
Each is documented at the affected statement. No entire mixin package or
nullability category is excluded. Reevaluate an exception if its fixture grows
or its mixin target changes. Threading, security, and GameTest correctness checks
remain enabled; low-value findings are not evidence that those checks should be
disabled broadly.

Deleting a stale configuration only changes the alert configuration/status.
A history reset instead deletes stored analyses through GitHub's code-scanning
API, newest first, including the final analysis in each set. Even after all
analyses have been removed, a new upload can reconnect a finding to its original
alert number and first-detected date. A reset is not a way to force new dates.

Performance alerts identify candidates for investigation. Check execution
frequency, collection size, client/server side, and whether the code is a GameTest
fixture or a gameplay path. Measure tick, rendering, loading, or world-generation
impact before making invasive optimizations. A successful scan does not establish
a gameplay speedup, and clearing an alert does not establish that its code changed.

## Local validation

Node.js 24 and its standard library are sufficient; no package installation or
Gradle invocation is needed for this report-processing code.

```powershell
node --test .github/scripts/*.test.mjs
node .github/scripts/tag-qodana-performance.mjs original.sarif.json github.sarif.json
node .github/scripts/check-qodana-health.mjs original.sarif.json
```

The input and output must be different files. The workflow runs the focused tests
before producing the GitHub report and then uses the existing SARIF upload step.

To validate artifact preparation on Windows without launching Minecraft:

```powershell
.\gradlew.bat :jar :aether-server:jar '-PcodexBuildDir=.codex-build' --no-parallel --max-workers=1 --console=plain
```

After pushing the configuration, review a fresh full Qodana report. Confirm the
unresolved-library and suspended-sanity diagnostics are gone before judging
changes in alert counts. A compilation or script test alone does not prove that
Qodana's container imported the prepared artifacts correctly.

For resource-profile changes, also check that the borrowed-accessor warnings are
reduced while findings for unlisted resource factories remain visible. Inspect
configuration warnings for unknown option IDs; merely parsing YAML does not
confirm that the linter applied the options.

References:

- [GitHub SARIF rule tags](https://docs.github.com/en/code-security/reference/code-scanning/sarif-files/sarif-support)
- [Qodana report metadata](https://www.jetbrains.com/help/qodana/qodana-inspection-output.html)
- [GitHub alert filtering](https://docs.github.com/en/code-security/how-tos/manage-security-alerts/manage-code-scanning-alerts/assess-alerts)
- [Qodana bootstrap and project configuration](https://www.jetbrains.com/help/qodana/configuration-reference.html)
- [Resource-inspection method exceptions](https://www.jetbrains.com/help/inspectopedia/resource.html)
- [Qodana profile inheritance and options](https://www.jetbrains.com/help/qodana/inspection-profiles.html)
- [CodeQL manual-build analysis scope](https://docs.github.com/en/code-security/reference/code-scanning/troubleshoot-analysis-errors/alerts-in-generated-code)
