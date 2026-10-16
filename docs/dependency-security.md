# Dependency security alerts

Dependabot notifications focus on the Minecraft mod, the standalone Aether
gateway, and the Aether voice service. Runtime dependencies remain covered,
including transitive libraries supplied by Minecraft/NeoForge.

## Runtime and development scopes

`.github/workflows/dependency-submission.yml` uses the official Gradle
dependency-submission action and its scope-capable 1.5.0 extractor. It reports
the complete graph, but assigns runtime scope only to these configurations in
the root mod project (`:`) and Aether gateway (`:aether-server`):

- `runtimeClasspath`: main code and Minecraft/NeoForge runtime libraries.
- `clientLegacyClasspath` and `serverLegacyClasspath`: ModDev's actual launch
  configurations, including additional runtime dependencies.
- `jarJar`: libraries distributed inside the mod.
- `sodiumService`: the existing Sodium client service dependency.

Other configurations and projects, including JUnit, NeoForm tooling, and the
standalone Structure Viewer, receive development scope. If the same package
version appears in any runtime configuration, runtime wins. For example,
Aether's Gson remains runtime-scoped even though Structure Viewer also uses it.
Different versions of the same library can have different scopes.

The scope guard checks the generated report before submission, including
Minecraft's Netty/Log4j/LZ4, Aether's JWT/HTTP libraries, and client-only Sodium.
An empty or unscoped report fails the workflow instead of replacing coverage.
When intentionally replacing one of those libraries, update the guard along
with the dependency and review the new report.

In **Settings > Advanced Security > Dependabot auto-triage rules**, enable:

- Name: `Dismiss development-only Java dependencies`
- Target: `ecosystem:maven scope:development`
- Action: dismiss indefinitely

Rules run before alert notifications. Development findings remain available
among closed/auto-dismissed alerts. This rule leaves Python service findings,
GitHub Actions findings, unknown scopes, and all runtime findings visible.
Keep Dependabot alerts and security updates enabled. The existing GitHub
low-impact development preset can remain enabled.

GitHub's automatic submission may remain enabled: user-submitted manifests
take precedence over automatic submissions. Check the effective graph after
the first successful run; do not interpret a local report as live activation.

## Why an alert points to settings.gradle

The upstream Gradle extractor assigns the entire multi-project graph to the
root settings file. That link identifies the build, not a dependency
declaration. This is expected upstream behavior, and remains true with scopes.
Do not move dependencies into settings.gradle or patch generated JARs to make
that label appear correct.

For example, alert #1 reported Commons Compress 1.18 through
`net.neoforged:neoform-runtime:2.0.31`, while Minecraft's runtime resolves
Commons Compress 1.26.0. The first is development tooling; the latter remains
runtime-scoped and independently alertable.

Use Gradle's dependency insight to find the owning path and selected version:

```powershell
.\gradlew.bat dependencyInsight --dependency commons-compress --configuration runtimeClasspath '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :aether-server:dependencyInsight --dependency gson --configuration runtimeClasspath '-PcodexBuildDir=.codex-build' --no-parallel
```

Some runtime libraries are pinned by Minecraft/NeoForge. Their alerts are
valid runtime findings, but Dependabot cannot necessarily update them. A
failed updater does not mean the vulnerability is fixed or absent. Review the
advisory's preconditions and use a compatible platform upgrade when needed.

`.github/dependabot.yml` permits security PRs for the explicitly listed mod
and Aether libraries whose versions this repository controls, plus the Python
voice service. The root Gradle entry also covers its Aether subproject.
Routine version PRs are disabled. Keep the owned-library list current when
adding a runtime library. This update policy does not suppress runtime alerts
or guarantee every Gradle/transitive security update can be automated.

## Verification and rollout

```powershell
node --test .github/scripts/check-dependency-scope.test.mjs
node .github/scripts/check-dependency-scope.mjs .codex-build/reports/dependency-graph
```

Publish the workflow to the default branch (`in-dev`) and run **Dependency
Security Scope**. Inspect its retained dependency graph artifact and the
effective dependency graph in GitHub before considering rollout complete.
Keep runtime alerts open even when their automatic update fails.

References:

- [Gradle dependency graph scope configuration](https://github.com/gradle/github-dependency-graph-gradle-plugin#controlling-the-scope-of-dependencies-in-the-dependency-graph)
- [Gradle attribution and security update limitations](https://github.com/gradle/actions/blob/main/docs/dependency-submission-faq.md)
- [GitHub manifest precedence](https://docs.github.com/en/code-security/reference/supply-chain-security/automatic-dependency-submission#manifest-deduplication)
- [GitHub alert triage rules](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/manage-your-dependency-security/auto-triage-dependabot-alerts)
