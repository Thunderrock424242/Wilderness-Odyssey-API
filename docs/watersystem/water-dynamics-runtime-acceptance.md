# Waterfall and coastal-storm runtime acceptance

This record supplements the [implementation and automated validation report](waterfalls-and-storms.md).
Tests ran on 2026-11-02, with uncommitted changes on `in-dev` above `7b800ed6`.
It records observed clients separately from numerical, GPU and loaded-world
fixtures. No result here certifies the entire advanced-water roadmap.

## Environment and isolation

- Minecraft 1.21.1, NeoForge 21.1.262; Corretto 21.0.13 for the live clients.
- Normal JIT, 1 GiB initial / 6 GiB maximum heap; the repository's existing two
  scoped launch compiler exclusions remained in place.
- Intel Core i9-14900HX; NVIDIA RTX 4090 Laptop GPU, OpenGL driver 621.01.
- 2560 x 1494 framebuffer after maximizing; render distance 8, simulation
  distance 6, VSync and the existing 60 FPS cap retained. Water tier was Auto/High.
- Three disposable profiles under `.codex-build/run-water-dynamics-*-20261102`,
  each copied from a stopped acceptance world. Normal developer game files and
  the source world were not edited. All development processes ran serially.
- Ocean observation at `(748, 70, -984)` in the overworld, in spectator mode.
  Camera orientation and world time differ across runs; these are not matched
  benchmark frames. The scene does not contain an accepted waterfall fixture.

The build was already successful before these clients. No production source
change was needed to activate the optional renderer. The ignored init script
uses ordinary Gradle dependency resolution, a `Copy` task into the disposable
profile's `mods` directory and the supported `runClient` task. It does not edit
dependency JARs or replace the NeoForge launcher.

## Installed-mod observations

| Profile | Actual result |
| --- | --- |
| Native renderer | World loaded; water and foam visible; F3+T reload returned the scene; normal save, shutdown and Gradle exit 0 |
| Sodium 0.8.12 | Actual Sodium renderer confirmed in F3; water and foam visible; F3+T reload returned the scene; normal save, shutdown and Gradle exit 0 |
| Iris 1.8.14 beta 1 / Distant Horizons 2.4.3-b | World loaded with shaders off; Complementary Unbound r5.6.1 then activated and rendered the water scene behind the options/menu screens. User took over remaining testing before F3, reload or travel acceptance |

The native run completed in 6m32s. Its diagnostic spot readings were 57-59 FPS
and 5.8-11.8 ms integrated-server ticks. The active Sodium run showed 58-59 FPS
and 3.6-5.6 ms ticks during the captured observations. It completed in 4m25s;
all dimensions saved at 22:03:23 and Minecraft stopped at 22:04:06.
The capped, short samples
do not establish an FPS improvement, a percentile distribution, a memory trend
or a representative whole-server budget.

Native local evidence:

- `.codex-build/water-dynamics-native-client.log`
- `.codex-build/run-water-dynamics-native-20261102/screenshots/2026-11-02_21.35.25.png`

Active Sodium local evidence:

- `.codex-build/water-dynamics-sodium-mods-client.log`
- `.codex-build/run-water-dynamics-sodium-20261102/screenshots/2026-11-02_22.02.18.png`
- F3 explicitly reported `Sodium Renderer (0.8.12+mc1.21.1)` and
  `[Fabric] Active renderer: SodiumRenderer`. Sodium bootstrap, common rendering
  mixins and Wilderness's chunk-output handoff also appear in the log.
- Resource reload began at 22:02:37; the water scene returned. Unrelated optional
  mods still log missing subtitle translations and optional resource warnings.

Versions were selected from the official release pages:
[Sodium 0.8.12](https://modrinth.com/mod/sodium/version/S3DUMfBo),
[Iris 1.8.14 beta 1](https://modrinth.com/mod/iris/version/1.8.14-beta.1%2B1.21.1-neoforge),
[Distant Horizons 2.4.3-b](https://modrinth.com/mod/distanthorizons/version/2.4.3-b-1.21.1),
and [Complementary Unbound r5.6.1](https://modrinth.com/shader/complementary-unbound/version/LXrX6oqm).
Only disposable profiles receive these additional dependencies.

Iris/Distant Horizons local evidence is
`.codex-build/water-dynamics-iris-dh-client.log`. Iris logged
`Using shaderpack: ComplementaryUnbound_r5.6.1.zip` and created the overworld
pipeline at 22:09:33; the shader-lit water scene was visibly present afterward.
This establishes startup and shader activation only. No Iris frame/tick figures,
wet resource-reload result, far-LOD water seam result or waterfall result was
captured. The user requested to perform the remaining testing themselves; the
client was saved and closed for handoff. Distant Horizons closed its world/level
workers normally. Shutdown exit status is recorded below after process completion.

The selected shader archive SHA-256 is
`1C25B2B917FF6D0A511CF2EDD8992E0BC66C950C401020F1B0362646BB3E2952`.
The successful packaged mod JAR, `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`,
retained SHA-256
`0B1372A480B821BBB4BD9E89746DDD08F8387FE6211E3905F2632F56D266E103`.
Live runs used the development source through the normal Gradle launcher.

## Development-launch issues distinguished from acceptance

Initial attempts exposed three different setup problems:

1. The upstream Sodium Maven service-artifact download ended with a connection
   reset before runtime. No network/security settings changed.
2. The cached split development mod activated mixins but lacked
   `Workarounds$Reference`, producing a `ClassNotFoundException` before the
   world opened. This was a dependency-layout failure; no water-source failure
   was established.
3. Putting the complete published wrapper JAR on the development classpath
   listed Sodium as a mod while leaving the renderer vanilla. Those healthy
   launches are **not** Sodium compatibility passes. Loading the complete
   published artifact through the normal `mods` directory activated its
   bootstrap and actual renderer, as verified above. A staging-task registration
   error in the disposable init script was corrected before that successful run.

The complete release replaced the split test dependency only in the ignored
acceptance profile. No generated JAR was patched or repackaged, and no global
Gradle cache, driver, Java installation, ACL or security setting was changed.

## Coverage still requiring acceptance

At the user's request, remaining hands-on acceptance is handed over to the user.
No further test clients or validation commands are planned in this pass.

The automated results are in the linked implementation report: 33 required
water-safety GameTests, the loaded coastal funding/recession fixture, full unit
suite/build, production water GPU checks and bounded arithmetic/query timings.
Those validate controlled behavior and conservation, including actual block
operations and regional serialization. They do not replace:

- A generated waterfall visit, moving player/boat interaction, lip blockage and
  reopening, sheet/mist inspection across all four tiers, and wet resource reload.
- A naturally formed, sustained storm with coastal rise, retreat and terrain
  travel on a representative save. Coastal surge remains default-disabled.
- Two-client synchronization, a broader terrain/graphics-mod matrix and optional
  renderer behavior across representative shorelines and waterfalls.
- A 30-minute gameplay FPS/MSPT/GPU/memory soak with uncapped comparative runs.

The earlier native JVM/GLFW failures are not declared generally fixed by the
healthy short clients. No simultaneous multiplayer processes were launched in
this pass. No commit or push was made.
