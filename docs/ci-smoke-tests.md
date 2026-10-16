# Minecraft startup smoke tests

The **Build and Run Client** and **Build and Run Server** GitHub Actions workflows
build the repository, then exercise one Minecraft development launch. They run
on pushes and pull requests and also support **Run workflow** from the Actions
page when GitHub exposes manual dispatch for the selected branch.

The build and launch run sequentially through the checked-in Gradle wrapper,
using JDK 21, `-PcodexBuildDir=.codex-build`, `--no-parallel`, plain console output
and stack traces. The existing `build` task selection is preserved, including
subproject checks. A failed Aether or structure-viewer test is a **build failure**;
Minecraft startup is then reported as **not run**.

## Readiness and shutdown

The client uses Xvfb and software OpenGL on the Ubuntu runner. With
`-PciSmokeTest=true`, the client-only `ClientStartupSmokeProbe` emits a readiness
marker after a title screen (including subclasses) or the first-run alpha notice
has rendered and the loading overlay is gone. The marker identifies the actual
screen class. After a 60-second observation period on a supported startup screen,
the probe asks Minecraft to exit normally. Success requires readiness, the planned
stop marker and exit code zero. Creating a texture atlas alone proves none of
these conditions.

The dedicated server needs no display server. Its normal `Done (...)! For help,
type "help"` message marks world readiness. After 180 seconds, the monitor writes
`stop` to the server console and waits for a clean exit, allowing Minecraft to
save normally. Gradle forwards stdin only for the opt-in CI profile.

CI game data goes into `.codex-build/run-ci-client` or
`.codex-build/run-ci-server`; normal developer `run/` data is not used. The server
profile creates an EULA acceptance file in that disposable CI directory. Run the
CI launcher only in a fresh checkout: it does not erase or reset existing worlds.
The opt-in flag and automatic client exit are disabled for ordinary launches.

Builds have a 25-minute deadline. Client startup has 20 minutes and server startup
has 15 minutes. A planned shutdown has 90 seconds; the client also has a bounded
observation deadline after readiness. Early process exit fails immediately,
including exit code zero before the required milestones. Timeouts and cancellation
terminate the launched Linux process group, with forced cleanup if necessary.

## Reports

Each workflow writes a job summary and a diagnostics artifact, including when
setup or the build fails before Minecraft starts. Reports distinguish **Build**,
**Startup**, **Soak** (the observation period) and **Shutdown**, with the commit,
timings, readiness evidence, exit code, signal, diagnostic excerpts and console
tails. A marker followed by a crash fails the run. Optional-mod warnings are
retained as diagnostics; matching an arbitrary `ERROR` line does not determine
success or failure.

Artifacts are named `client-diagnostics-<run>-<attempt>` or
`server-diagnostics-<run>-<attempt>` and retained for 14 days. They contain:

- `result.json`, `summary.md`, build console output and runtime console output.
- Available Minecraft `logs/`, crash reports and JVM `hs_err_pid` logs.
- Available JUnit XML and HTML reports from the root and subproject builds.
- On a timeout, a process listing and bounded `jcmd Thread.print` attempts for
  Java processes in the launcher's own process group.

The artifact paths explicitly select diagnostics rather than uploading CI worlds
or configuration directories. Checkout failure can prevent repository-owned
report generation; GitHub's setup error remains the evidence in that case.

## Validation

Run the monitor regressions without Minecraft:

```powershell
node --test .github/scripts/ci-smoke.test.mjs
```

The tests use real short-lived child processes to cover readiness, early exit,
crashes, stdin shutdown, timeouts, launcher errors and report classification.
The Linux process-group regression runs on GitHub's Ubuntu runner and is skipped
on Windows. All repository JavaScript checks can be run with:

```powershell
node --test .github/scripts/*.test.mjs
```

Check the Java probe and the Aether executable integration test sequentially:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' '-PciSmokeTest=true' --no-parallel --console=plain
.\gradlew.bat :aether-server:test '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

After publishing the changes, inspect both Actions runs. Verify that a successful
client run records its supported startup screen and a clean exit, and that the
server records `Done`, a console stop and a clean exit. Download each artifact and
check that the summary and logs agree with GitHub's outcome. A local Windows
compile/test pass does not establish Linux Xvfb startup, software graphics,
process-group cleanup or console forwarding in a real Minecraft run.

These smoke tests do not establish client world creation, integrated-server
loading, player login, multiplayer compatibility, graphics quality, real GPU
performance or long-session stability. Use the separate GameTests workflow and
targeted gameplay tests for those behaviors.
