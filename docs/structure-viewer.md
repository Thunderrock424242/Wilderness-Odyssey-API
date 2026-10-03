# Standalone Structure Viewer

The desktop tool browses Minecraft structure templates by mod JAR and previews NBT and supported JSON without starting Minecraft. It includes local block models/textures, manual quality controls, block inspection, validation, and automatic reload. Structures, mod JARs, and assets are read without modification; mod code never runs.

The desktop interface separates the library, preview, and inspector. The header's **Light / Dark** switch changes and saves the appearance, including the viewport. Open actions and local assets are in the header; Quality and Layer stay above the preview. **View options** contains textures, edges, bounds, wireframe, markers, camera focus, and automatic reload. **Controls** shows the keyboard/mouse reference. The default layout keeps these controls visible at a 980 × 640 window size. Loading screens show progress during scans, structure reads, model/texture preparation, meshing, and the first completed preview; the theme switch remains available during loading.

## Windows download and modpacks

The Windows `.exe` installer includes a Java 21 runtime. Users do not need to install Java separately. Run `Wilderness Structure Viewer-<version>.exe`, choose the installation folder, and launch **Wilderness Structure Viewer** from the shortcut or installation folder.

Choose **Open modpack…** and select the instance folder or its `mods` folder. Common `instance/.minecraft/mods` and `instance/minecraft/mods` layouts are recognized. The library groups templates beneath their source JAR, with namespace/resource names below each group. Search filters by JAR name, namespace, or path. Different JARs containing the same resource ID remain separate entries. Corrupt JARs are reported while other mods continue scanning.

For CurseForge, the picker recognizes `%USERPROFILE%\curseforge\minecraft\Instances` (`C:\Users\<name>\curseforge\minecraft\Instances`). Choose the individual pack folder inside Instances. A previously selected valid pack elsewhere remains the picker location; an existing CurseForge instance opens the picker at its parent Instances folder. Detection checks this known directory and does not scan unrelated launcher profiles.

If the application folder is placed inside a modpack, the launcher looks for a nearby `mods` folder on startup. The installer can be pointed at a folder such as `<modpack>/StructureViewer`. The launcher needs its adjacent `app` and `runtime` folders: copying only the installed launcher `.exe` will not work. Alternatively, leave the installation elsewhere and use **Open modpack…**; the last selected folder is remembered.

The native build also produces a portable application folder under `<build>/tools/structure-viewer/windows/image/Wilderness Structure Viewer`. Copy that entire folder into a modpack to use the same native launcher without installation.

Only concrete template files under `data/<namespace>/structure/` or `structures/` are previewed. A mod may generate structures entirely in Java or assemble many templates through jigsaw/worldgen rules. Those systems require Minecraft; the viewer displays the stored pieces it can find. It never scans saves or generates a world.

The installer does not contain Minecraft assets. Installed local client assets, mod JARs, and **Assets → Add local assets…** supply textures and models. Missing assets remain visible as placeholders with diagnostics. Native preferences live in `%LOCALAPPDATA%/WildernessOdyssey/StructureViewer/settings.properties`.

### Updating an installed copy

Close the viewer and run the newer installer while signed in as the same Windows user. Version **1.0.2** uses the same Windows upgrade identity as **1.0.0** and **1.0.1**, so Windows Installer detects and replaces the older registered installation. The 1.0.2 installer refuses to install over a newer installed version. Preferences are stored outside the application directory and are retained. A portable application folder is not a registered installation; replace that entire folder when updating it.

`verifyWindowsInstaller` reads the generated MSI database without installing it. It checks the product version, stable upgrade code, range that detects/replaces 1.0.0, newer-version detection and blocking condition, and scheduled removal of the older product. This is package metadata validation; installation, upgrades, and removal still need acceptance on a clean Windows machine.

## Launch

From the repository root, with JDK 21:

```powershell
.\gradlew.bat structureViewer
.\gradlew.bat structureViewer '-PstructureViewerFile=src/main/structure_blueprints/test_shelter.json'
```

IntelliJ exposes `structureViewer` and `structureValidator` under **Wilderness Odyssey Tools**. The module's `run` task is equivalent to the viewer alias. To configure/build only the standalone tool:

```powershell
.\gradlew.bat -p tools/structure-viewer run '-PcodexBuildDir=.codex-build'
.\gradlew.bat -p tools/structure-viewer build '-PcodexBuildDir=.codex-build'
```

The Java-only distribution is `<build>/tools/structure-viewer/distributions/structure-viewer.zip`. Extract it and launch `bin/structure-viewer.bat` with Java 21. Run from the checkout to discover its resources, or use **Open modpack…**, **Open structure…**, and **Add assets…**. Gradle sets the checkout/build paths automatically.

The normal build directory is `build`; `-PcodexBuildDir=.codex-build` selects isolated output. Generated output and viewer preferences must not be committed.

## See individual blocks

**High** is the default. Keep **View options → Textures** and **Block edges** enabled, click a block, then press **G** or choose **Focus selected block**. Use a single **Layer** to expose interior floors; **All** shows the whole structure.

| Quality | Internal resolution limit | Intended use |
| --- | --- | --- |
| Fast | 800 × 600 | Lower pixel cost while navigating |
| Balanced | 1400 × 1000 | Moderate pixel cost |
| High | 2560 × 1440 | Native-resolution detail up to the limit |
| Ultra · 2× | 3840 × 2160 | Up to twice the viewport dimensions, then downsampled |

Limits preserve aspect ratio; actual dimensions appear in the viewport. Quality changes pixel resolution only: every preset uses the same geometry and block identities. Enclosed faces are culled and stored air is hidden unless enabled. Distant blocks can still be smaller than a pixel; use G or fly closer.

Developer launches save quality, edges, textures, automatic reload, added asset sources, and the selected root in `<build>/tools/structure-viewer/settings.properties`. Native launches use the LocalAppData path above. Explicit choices persist across launches; there is no automatic quality override.

## Controls and inspection

| Control | Action |
| --- | --- |
| F1 / F2 | Orbit / free camera |
| Right-drag | Orbit or look |
| WASD | Move in free camera |
| Space / Shift | Move up / down |
| Ctrl | Precision movement |
| Mouse wheel | Orbit zoom or free-camera speed |
| F / G | Fit structure / focus selected block |
| R | Reload |
| Left-click | Inspect the visible block |

The inspector shows original ID, coordinates, supplied properties, palette/source index, typed block-entity NBT, and entry metadata. Rendering defaults do not overwrite imported data.

**View options → Structure bounds**, **Wireframe**, **Stored air**, and **Layer** control inspection. **Entities** and **Block entities** display markers, including jigsaw/structure blocks. **Coordinates** enables marker labels and selected-block coordinates. Markers intentionally show through geometry; entities are markers rather than animated models. The inspector has **Summary**, **Block**, **Issues**, and **Data** tabs; root metadata is in **Data**.

## Sources and supported formats

Discovery indexes top-level JARs in the selected root's `mods` and `run/mods` folders, plus JAR/ZIP data packs in `datapacks`. Developer roots also include `src/main/resources`, `src/generated/resources`, `src/main/structure_blueprints`, and StructureGen resources under that project's build directories. Changing to another modpack excludes the previous project's generated structures and property defaults. Tags/worldgen definitions are excluded. **Rescan** updates the library; **Open structure…** also accepts external files.

NBT supports raw/gzip Minecraft templates, primary/alternate palettes, typed block/entity payloads, and extra metadata. The first palette is rendered; all palettes are retained.

JSON supports concrete Blueprint-v1 data:

```json
{
  "formatVersion": 1,
  "name": "example",
  "size": [1, 1, 1],
  "blocks": [{
    "pos": [0, 0, 0],
    "block": "minecraft:oak_stairs",
    "properties": {"facing": "north", "half": "bottom", "shape": "straight"}
  }],
  "markers": ["example"]
}
```

Blueprint `blockEntitySnbt` and entity `nbtSnbt` preserve SNBT numeric/array types. JSON also accepts template-style `palette` entries with `Name`/`Properties`, blocks with `pos`/`state`/`nbt`, and entity records. Both readers produce `StructureData`; rendering is format-independent.

Semantic materials and procedural operations must first be resolved through StructureGen. Arbitrary worldgen JSON, `.schem`, and `.litematic` are unsupported. Marker metadata is retained for future integration.

## Assets and model accuracy

The first matching asset wins, in this order:

1. Added sources, newest first.
2. Project `src/main/resources` and `src/generated/resources`.
3. The selected root's `mods`, `run/mods`, and `datapacks` archives, in filename order within each folder.
4. The `structureViewer.minecraftJar` JVM property, when supplied.
5. The selected root's `versions/1.21.1/1.21.1.jar`, local NeoForm Minecraft 1.21.1 client assets, and the standard Windows launcher 1.21.1 JAR.

**Assets → Add local assets** accepts a directory containing `assets/`, resource-pack ZIP, or mod/client JAR. The tool does not download, unpack, or redistribute Minecraft assets. Reload after editing assets. Diagnostics list actual sources; Clear added assets restores automatic sources.

Supported static features include parent models, texture variables, cuboid elements, element rotation/rescale, face UV/rotation, blockstate rotations and UV lock, variants, multipart AND/OR, cutout pixels, and basic translucent blending. Doors and stairs use their state-selected models.

The existing StructureGen `generated/structuregen/catalog/available-content.json` snapshot supplies omitted defaults and property domains when available. Explicit properties always win. Diagnostics identify the snapshot as cached: its environment fingerprint is not revalidated, so it cannot prove current registration. Without defaults, the first compatible variant may be used with a warning.

Missing/unsupported models get checkerboard cubes. Missing textures keep available geometry with checkerboard faces. Malformed optional catalog entries are ignored with warnings. Other blocks continue loading.

Reference: [NeoForge 1.21.1 model format](https://docs.neoforged.net/docs/1.21.1/resources/client/models/).

This remains an inspection preview, with these limits:

- Custom Java model loaders, block-entity renderers, and dynamic fluid surfaces require placeholders. Entity behavior never runs.
- Animation displays the first image frame; weighted choices use the first entry. Biome/redstone tints, lighting, and translucent sorting are approximate.
- No Minecraft world lighting, ambient occlusion, connected-texture extensions, neighbor-state updates, or gameplay runs. Blueprints need resolved connection/state properties.
- Static geometry and UV lock cover the implemented subset; complex custom assets still need comparison in Minecraft.
- Multiple palettes are retained, but only the first is shown.

## Reload and validation

Auto reload watches the selected file, or the containing JAR for an archive entry, including replacement during export, with 500 ms debounce. Reloading the same exact entry preserves the camera and layer; switching entries inside one JAR fits the new structure. If an export shrinks, the layer clamps to the new height before meshing. An unreadable intermediate export keeps the previous preview and can recover on the next change. R and Reload work with watching disabled. Asset changes require manual reload.

Diagnostics report invalid IDs/palette references, duplicate coordinates, bounds, malformed payloads, cached/asset-defined state problems, missing assets, and unsupported renderers. Recoverable warnings permit previewing. Unreadable/ambiguous data or resource limits reject the import. Duplicate JSON keys are rejected rather than guessed.

```powershell
.\gradlew.bat structureValidator '-PstructureViewerFile=src/main/structure_blueprints/test_shelter.json' '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat -p tools/structure-viewer validate '-PstructureViewerFile=src/main/structure_blueprints/test_shelter.json'
```

The headless validator shares import/model resolution. Readable files return success with warnings; unreadable files fail. It is not a strict registry acceptance gate. It uses automatic asset sources, not the GUI's saved pack list.

## Architecture and lifecycle

The separate `tools/structure-viewer` application owns its classpath. Gson supplies JSON parsing, with its transitive Error Prone annotation dependency; Java desktop APIs provide the UI/software renderer, FlatLaf 3.7.2 supplies consistent light/dark controls and display scaling, and JUnit is test-only. The root mod does not depend on or package the viewer.

- `model`: shared immutable structure records and typed metadata.
- `io`: bounded readers, discovery, and file watching.
- `assets`: ordered sources, existing catalog adapter, model/texture resolution.
- `render`: cached model faces, camera, perspective-correct textures/picking, quality, markers.
- `ui`: browser, preferences, asynchronous import/render, inspection.
- `validation`: shared diagnostics and headless entry point.

StructureGen's existing reader depends on Minecraft and compiler-oriented validity. The isolated viewer adapter follows its formats without pulling in Minecraft or creating another compiler, mission owner, registry, or world-state system.

Swing/camera are owned by the event thread. One loader with a bounded queue prepares the index/data/meshes, one renderer produces complete frames, and the watcher reports changes. Cancelled requests are removed from the queue; generation checks reject stale results. Closing the window closes the watcher, cancels loading, and stops input/render workers. Archives close after preparation. Decoded models/textures are retained for the current structure, so Y-layer and air changes avoid rereading assets or repeating full validation. A new import/reload replaces this snapshot. Frames are requested when the view changes. The renderer caches model-space normals/lighting and reuses opaque-face projection storage; translucent faces retain independent vertices.

NBT limits: 64 MiB file, 256 MiB decoded data, depth 64, 4 million collection entries, 40 million tags. JSON limits: 64 MiB, depth 64, 8 million values. Archive indexing is capped at 20,000 templates, 200,000 entries per archive, and 4,096 archives per source directory. Assets are capped at 8 MiB each; texture dimensions/pixels, cache (32 million pixels), model parent depth, and faces are bounded. The launch heap is 2 GiB. Dense structures/Ultra can be expensive; reduce quality or use layers. Inspector text is capped at 32 KiB. Markers are capped at 10,000 prepared, 1,000 drawn, and 30 labels per frame.

## Building the Windows installer

### GitHub Actions artifact

The **Structure Viewer Windows** workflow (`.github/workflows/structure-viewer-windows.yml`) builds the Windows x64 installer on pushes to `main` and `in-dev` that change the viewer, wrapper, license, or workflow. Pull requests with those changes also build it. Under **Actions → Structure Viewer Windows → a successful run → Artifacts**, download `wilderness-structure-viewer-windows-x64-1.0.2`. Extract the artifact ZIP to obtain the installer `.exe` and its SHA-256 checksum.

The installer artifact is retained for 30 days; test and launcher diagnostics are retained for 14 days. The run summary links to the installer download. This uploads a build artifact, without creating a GitHub Release or signing the executable.

**Run workflow** allows a manual build with a `major.minor.patch` installer version, defaulting to `1.0.2`. Until this workflow is on the repository's default branch, its automatic push/PR triggers provide builds; GitHub's manual workflow button requires the workflow on the default branch.

CI uses the standalone Gradle module, JDK 21, and the same checksum-verified portable packaging tools as local builds. It runs the viewer unit tests, builds the installer, checks MSI upgrade metadata, and launches the packaged `.exe` to verify its bundled runtime and archive support. It does not download Minecraft assets or run the main mod build. The optional vanilla-asset integration test skips when local assets are absent; desktop smoke checks remain local acceptance tests.

### Local build

Build on Windows with JDK 21 containing `jpackage.exe` and WiX 3's `candle.exe`/`light.exe`. Packaging is opt-in and is separate from the production mod build. Oracle's [JDK 21 jpackage guide](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html) describes the Windows tooling requirement.

`prepare-windows-tools.ps1` downloads pinned portable WiX 3 and an Eclipse Temurin Java 21 JRE from their official GitHub releases, verifies SHA-256 checksums, and extracts them into the ignored build tool directory. It does not install tools or modify system settings. Run it explicitly, then pass the printed paths to Gradle:

```powershell
.\tools\structure-viewer\prepare-windows-tools.ps1
.\gradlew.bat -p tools/structure-viewer verifyWindowsInstaller verifyWindowsLauncher bundledUiSmokeTest '-PcodexBuildDir=.codex-build' '-PviewerPackagingJdk=C:/path/to/jdk-21' '-PviewerRuntimeDir=C:/path/printed/by/helper/jdk-21.0.12.1+1-jre' '-PviewerWixDir=C:/path/printed/by/helper/wix-3.14.1' --no-parallel
```

`structureViewerInstaller` is the root alias for `:tools:structure-viewer:windowsInstaller`. The standalone invocation avoids configuring NeoForge. `viewerPackagingJdk` defaults to Gradle's Java 21 toolchain. `viewerRuntimeDir` is optional: without it, jpackage builds a reduced Java 21 runtime including desktop and ZIP filesystem modules from that JDK. Choose a runtime licensed for redistribution; the supplied helper uses Temurin and the bundle retains runtime legal files. `-PviewerVersion=1.0.2` controls installer versioning; the upgrade identity stays stable.

Outputs beneath the selected build directory:

| Artifact | Path |
| --- | --- |
| Installer | `tools/structure-viewer/windows/installer/Wilderness Structure Viewer-1.0.2.exe` |
| Portable application folder | `tools/structure-viewer/windows/image/Wilderness Structure Viewer/` |
| Native-launcher report | `tools/structure-viewer/windows/launcher-diagnostics.json` |
| Installer-upgrade report | `tools/structure-viewer/windows/installer-upgrade-diagnostics.json` |
| Packaged UI screenshots | `tools/structure-viewer/reports/bundled-ui-smoke/` |

The per-user installer offers a destination chooser, Start menu entry, and shortcut. Its input contains the application, Gson and its annotation dependency, FlatLaf, their licenses, the project license, and bundled runtime. It includes no Minecraft assets, mod JARs, preferences, or test classes. Packages are unsigned until a separately configured release signing step is supplied. Verification runs the native launcher and packaged classes with their bundled runtime; it does not install/uninstall the application on the developer's machine. Installation, upgrades, and removal still need acceptance on a clean Windows machine before a stable release.

Phase 3 remains future work: mission-aware markers, thumbnails, additional formats, and separately scoped editing.

## Verification

Run one Gradle/Minecraft development operation at a time:

```powershell
.\gradlew.bat :tools:structure-viewer:test '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :tools:structure-viewer:uiSmokeTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :tools:structure-viewer:build '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :build '-PcodexBuildDir=.codex-build' --no-parallel
```

Tests cover parsing, typed metadata, source immutability, per-JAR grouping, corrupt-archive recovery, launcher layouts, asset priority, closed-archive model reuse, inheritance/multipart, missing/cyclic/custom models, catalog defaults/recovery, isolation from another project's generated data, partial-block visibility, translucent geometry, edges/picking, camera, preferences, and atomic replacement. Real vanilla integration runs if local 1.21.1 assets exist; otherwise that test is explicitly skipped.

The desktop smoke task opens the actual app, checks selection/freecam/mouse look/orbit, Fast/Ultra resolution, saved quality, G focus, JSON reload/failure recovery, shrinking Y layers, per-JAR tree selection, NBT/JSON archive entries, source immutability, and atomic JAR reload/camera preservation. It uses copied fixtures and isolated preferences, sends input only to its own Swing controls, captures `<build>/tools/structure-viewer/reports/ui-smoke` screenshots, and closes its window. `-PstructureViewerSmokeFile=<path>` selects another template. `bundledUiSmokeTest` repeats those behaviors using packaged application classes and the bundled Java runtime.

`renderBenchmark` measures the existing 2,171,624-block bunker at a fixed 1000 × 700 view, with three warmups and seven measured frames. It prints median frame time, per-frame thread allocations, and a block-ID checksum. Compare runs on the same machine/JVM; it is a local software-renderer measurement, not a general FPS guarantee.

Manual acceptance: inspect stairs/doors and mod blocks, use High/Ultra plus edges, click and press G, fly inside, isolate a Y layer, check markers/metadata, and re-export through normal authoring tools. Minecraft remains the final reference for unsupported/dynamic behavior.
