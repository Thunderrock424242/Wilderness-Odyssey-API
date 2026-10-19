# Where WAGE lives

The Wilderness Odyssey Adaptive GPU Engine and its shared rendering framework live
under `src/main/java/com/thunder/wildernessodysseyapi/wage/`, with the Java root
`com.thunder.wildernessodysseyapi.wage`. Tests mirror that root under
`src/test/java/com/thunder/wildernessodysseyapi/wage/`.

```text
wage/
  EnvironmentState, GPUCapabilities, RenderFrameContext, RenderingQuality
  TemporalFrameData, UpscalingProvider
  backend/                 Backend contracts, selection and lifecycle
    opengl/                Existing deprecated Minecraft 1.21.1 compatibility adapter
  client/                  WildernessRenderingFramework frame/event owner
    diagnostics/           Opt-in WageClientAcceptanceProbe
  compat/                  Cached Iris/Oculus shader ownership checks
  config/                  RendererConfig and immutable settings
  performance/             Adaptive GPU/quality policy, bounded statistics and timers
```

Start with `wage/package-info.java` for ownership and
`wage/client/WildernessRenderingFramework.java` for frame lifecycle. This is a
package reorganization of the existing implementation; it adds no new rendering
manager, executor, event registration or GPU workload.

## Ownership retained

Minecraft and NeoForge retain render-pass order, render targets and submission.
Weather/cloud shaders and renderers remain under `weather`; water rendering,
meshes and scene capture remain under `watersystem`. Rift/anomaly presentation
stays with its feature. Those consumers import WAGE's shared frame/backend
contracts. The Rendering debug-overlay page stays in `debugoverlay`.

OPC retains shared JVM sampling and GPU profiling under `opc`; WAGE continues to
reuse `opc.core.JvmMetrics`. No server scheduling, world generation or simulation
authority moves into WAGE. The general client startup smoke probe stays in
`client.diagnostics`; only the WAGE-specific acceptance probe moves here.

Client event subscribers retain `Dist.CLIENT`, and resource reload registration
still comes from `core.ClientSetup`. Common configuration uses the pure WAGE
policy/config types without initializing the client frame or OpenGL owners.

## Java API migration

| Previous package/type | Canonical package/type |
| --- | --- |
| `com.thunder.wildernessodysseyapi.rendering.*` | `com.thunder.wildernessodysseyapi.wage.*`, preserving subpackages and class names |
| `com.thunder.wildernessodysseyapi.client.diagnostics.WageClientAcceptanceProbe` | `com.thunder.wildernessodysseyapi.wage.client.diagnostics.WageClientAcceptanceProbe` |

Downstream source integrations must update imports and recompile. The package
move changes binary class names; already compiled integrations using the old
names require a rebuild. There are no duplicate legacy framework implementations
or forwarding event subscribers.

Config filenames, TOML keys/defaults (including `renderer.gpuEngine.enabled=false`),
shader/resource IDs, shader content, mixin registrations, save formats and
acceptance system properties remain unchanged. Existing worlds and client configs
need no migration.

## Validation

The existing `renderingTest` and `gpuRenderingTest` task names remain available;
their selectors follow the new package. `renderingTest` also runs
`architecture.WagePackageLayoutTest`, using the exact JAR built by that invocation
to require the WAGE implementations and reject leftover `rendering` classes or the
old acceptance probe. Ordinary `test` includes that guard as well.

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat renderingTest -PcodexBuildDir=.codex-build --no-parallel
```

For explicit GPU acceptance, the existing `gpuRenderingTest` task and
`runClient -PwageAcceptanceTest` profile retain their behavior and opt-in
requirements. See [WAGE validation](wage-validation.md) for their setup and prior
evidence. Package compilation/tests do not establish fresh in-game, driver,
shader-pack or measured-performance acceptance.

### Executed package checks, 2026-10-19

- Before relocation, `compileJava` succeeded and the new package-layout test
  failed as expected on missing `wage/EnvironmentState.class`, proving that the
  guard rejects the old shipped layout.
- After relocation,
  `.\gradlew.bat compileJava renderingTest -PcodexBuildDir=.codex-build --no-parallel --console=plain`
  completed successfully: 329 cases, zero failures/errors, one opt-in GPU skip.
- `.\gradlew.bat :test -PcodexBuildDir=.codex-build --no-parallel --console=plain`
  completed successfully: 1,674 cases, zero failures/errors, five skips. Both
  suites passed the exact-JAR package guard.
- All 36 relocated Java files (21 production, 15 tests) and 27 affected Java
  consumers matched their previous contents after namespace/path substitutions.
  The root package Javadoc and JAR-layout regression are new files.
- `git diff --check` passed. No tracked resources or generated output changed.
  Minecraft and explicit GPU acceptance were not run for this package-only change.
