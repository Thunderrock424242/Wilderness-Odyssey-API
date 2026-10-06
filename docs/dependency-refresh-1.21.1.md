# Minecraft 1.21.1 dependency refresh

Release metadata checked on October 6, 2026. Minecraft remains on 1.21.1 and the
Java toolchain remains on Java 21. Mod updates use published NeoForge releases
for that exact Minecraft version, excluding preview builds.

## Minecraft dependencies

| Dependency | Previous version | Selected version | Publisher release |
| --- | --- | --- | --- |
| NeoForge | 21.1.250 in Gradle / 21.1.248 in properties | 21.1.256 | [Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) |
| Regions Unexplored | 0.6+beta4 | 0.6.2 | [8419667](https://www.curseforge.com/minecraft/mc-mods/regions-unexplored/files/8419667) |
| Lithostitched | 1.6.5 | 1.8.0 | [8944659](https://www.curseforge.com/minecraft/mc-mods/lithostitched/files/8944659) |
| Biolith | 3.0.10 | 3.0.14 | [8435635](https://www.curseforge.com/minecraft/mc-mods/biolith/files/8435635) |
| GeckoLib | 4.8.2 | 4.9.3 | [8893490](https://www.curseforge.com/minecraft/mc-mods/geckolib/files/8893490) |
| Amendments | 2.0.15 | 2.1.10 | [8825641](https://www.curseforge.com/minecraft/mc-mods/amendments/files/8825641) |
| Moonlight Lib | 3.0.18 | 3.7.1 | [9066398](https://www.curseforge.com/minecraft/mc-mods/selene/files/9066398) |
| Supplementaries | 3.6.8 | 3.9.9 | [8852720](https://www.curseforge.com/minecraft/mc-mods/supplementaries/files/8852720) |
| Camera Monitoring | 1.1.0 | 1.3.2 | [9066753](https://www.curseforge.com/minecraft/mc-mods/camera-monitoring-fully-customizable/files/9066753) |
| Sodium, client development runtime | 0.8.12 | 0.8.12 retained; 0.8.13 resolution blocked | [NeoForge 1.21.1 release](https://modrinth.com/mod/sodium/version/uMOpc5uV) |

`neoForge.version` now reads `neo_version` from `gradle.properties`, preventing
the build version and metadata input from diverging. Existing mod dependency
minimum ranges are retained; compiling against a newer release does not by
itself establish that older compatible releases must be rejected.

These dependencies already match their latest published NeoForge 1.21.1 file:
WorldEdit 7.3.8, Tick Tok Lib 1.4.3, Create 6.0.10, Locate Fixer 3.2.0,
Curios 9.5.1, and Spark 1.10.124. Multipart Machines: Cooking remains on 2.1;
its existing beta file is the only published NeoForge 1.21.1 file.

Sodium keeps the existing direct-mod and service-bootstrap development setup.
The [0.8.13 release source](https://github.com/CaffeineMC/sodium/blob/mc1.21.1-0.8.13/neoforge/build.gradle.kts)
declares the same four Forgified Fabric API module versions already used here.
Those versions remain paired with Sodium rather than independently advancing
to releases intended for other Minecraft versions.

The 0.8.13 update was attempted, but Gradle's `writeClientLegacyClasspath`
failed while fetching the `sodium-neoforge-mod` POM from CaffeineMC Maven with
`Connection reset`. Direct publisher metadata requests also failed. The working
0.8.12 pin is retained so the dependency refresh does not require an unreachable
developer artifact. Retry 0.8.13 when the publisher repository is accessible.

Lithostitched 1.8 decodes a registry-backed biome holder set in its region codec.
The region regression now supplies registry-aware JSON operations and verifies
the named Overworld biome tag alongside the established weights. The two
existing region JSON files are compatible and remain unchanged by this refresh.

## Build tooling and Java libraries

| Dependency | Previous version | Selected version | Scope |
| --- | --- | --- | --- |
| ModDevGradle | 2.0.147 | 2.0.148 | Mod development plugin |
| Parchment 1.21.1 | 2024.11.13 | 2024.11.17 | Mapping annotations |
| JUnit BOM | 5.10.2 | 5.14.4 | All three projects, latest stable JUnit 5 line |
| SnakeYAML | 2.2 | 2.7 | Mod packaged/runtime library and Aether gateway |
| OkHttp | 4.12.0 | 5.5.0 | Mod packaged library |
| Okio | 3.9.0 | 3.18.2 | Mod packaged library |
| Resilience4j circuit breaker | 2.2.0 | 2.4.0 | Mod packaged library |
| Zstd JNI | 1.5.7-6 | 1.5.7-21 | Mod packaged compression library |
| Gson | 2.10.1 gateway / 2.11.0 viewer | 2.14.0 | Standalone Aether gateway and structure viewer |

OkHttp and Okio use their explicit `okhttp-jvm` and `okio-jvm` coordinates so
Jar-in-Jar records the Maven group, artifact, and version instead of generated
hash identities for redirected multiplatform artifacts. The mod also bundles
Kotlin stdlib 2.1.21 and Resilience4j core 2.4.0, as required by these libraries'
JVM release metadata. NeoForge supplies SLF4J. A parameterized artifact
regression checks the identities and runtime classes of all seven bundled
libraries; before the fix, four cases failed for the two redirected identities
and the two missing support libraries.

Java library versions were checked against Maven Central publisher metadata.
JUnit stays on the repository's required Jupiter 5 family. Gson changes belong
to the standalone projects; Minecraft's own Gson dependency remains owned by
NeoForge. The mod's gateway test dependency excludes transitive Gson, because
Minecraft strictly pins Gson 2.10.1; the standalone gateway still resolves
2.14.0. Gradle 9.8.0, Foojay Resolver 1.0.0, Nimbus JOSE JWT 10.10, and FlatLaf
3.7.2 already match their latest stable releases and remain unchanged.

## Validation

Production compilation passed for the mod, Aether gateway, and structure
viewer using the checked-in wrapper and isolated output:

```powershell
.\gradlew.bat compileJava '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

Compilation emitted existing removal warnings for `OpenGlGpuTimer` and
`WaveAnimator`, plus ordinary deprecated/unchecked API notes. No production
source changes were needed to compile against the selected releases.

Final validation used one sequential Gradle process:

```powershell
.\gradlew.bat :test :aether-server:test :aether-server:verifyStandalone :tools:structure-viewer:test :build prepareClientRun '-PcodexBuildDir=.codex-build' --no-parallel --continue --console=plain
```

| Check | Result |
| --- | --- |
| Mod unit suite | 1,380 cases: 1,376 passed, 4 skipped, 0 failed |
| Packaged artifact regressions, included above | 13 passed, including 7 bundled-library cases |
| Structure viewer unit suite | 53 passed, 0 failed |
| Aether gateway unit suite | 52 passed, 1 failed |
| Aether standalone dependency/class isolation | Passed |
| Mod packaging tasks | Completed |
| Client launch preparation with Sodium 0.8.12 | Completed; Minecraft was not launched |
| Whitespace check | Passed |

The combined Gradle invocation exited with failure solely because
`AetherServerTest.executableJarStartsAndHandlesHealthGenerationAndOutage`
encounters `java.net.SocketException: Connection reset` at line 298 while
checking that an incomplete HTTP request is closed. A controlled run through
the normal Gradle pipeline with the pre-update Gson 2.10.1, SnakeYAML 2.2, and
JUnit BOM 5.10.2 reproduces the same failure. The gateway's production code and
timeout test are unchanged. This failure reproduces with the original libraries
on the current Windows/JDK setup and is independent of the dependency refresh.

The mod artifact is `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`.
The standalone gateway artifact is
`aether-server/.codex-build/libs/Aether-Gateway.jar`. The existing project
release versions were not changed.

StructureGen flags its optional offline content catalog as stale after the
dependency changes and safely ignores that snapshot. All three existing
blueprints validate and generate successfully. Refresh the catalog through the
existing `runData` workflow before generating structures that require the full
modded block catalog; data generation was not run during this refresh.

These checks do not establish live Minecraft startup, biome distribution,
mod mixin compatibility, rendering, multiplayer, or gameplay behavior. Use a
disposable test world for subsequent client/server acceptance testing.
