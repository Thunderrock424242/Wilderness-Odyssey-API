# Standalone Structure Viewer — Phase 2

The Java 21 desktop tool previews NBT and supported JSON structures without starting Minecraft. Phase 2 adds local block models/textures, state resolution, manual quality controls, debug overlays, validation, and automatic reload. Structures and assets are read without modification.

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

The distributable is `<build>/tools/structure-viewer/distributions/structure-viewer.zip`. Extract it and launch `bin/structure-viewer.bat` with Java 21. Run from the checkout to discover its resources, or use **Open NBT / JSON** and **Add assets**. Gradle sets the checkout/build paths automatically.

The normal build directory is `build`; `-PcodexBuildDir=.codex-build` selects isolated output. Generated output and viewer preferences must not be committed.

## See individual blocks

**High** is the default. Keep **Textures** and **Block edges** enabled, click a block, then press **G** or choose **Focus block**. Use a single **Y** layer to expose interior floors.

| Quality | Internal resolution limit | Intended use |
| --- | --- | --- |
| Fast | 800 × 600 | Lower pixel cost while navigating |
| Balanced | 1400 × 1000 | Moderate pixel cost |
| High | 2560 × 1440 | Native-resolution detail up to the limit |
| Ultra · 2× | 3840 × 2160 | Up to twice the viewport dimensions, then downsampled |

Limits preserve aspect ratio; actual dimensions appear in the viewport. Quality changes pixel resolution only: every preset uses the same geometry and block identities. Enclosed faces are culled and stored air is hidden unless enabled. Distant blocks can still be smaller than a pixel; use G or fly closer.

Quality, edges, textures, automatic reload, and added asset sources are saved in `<build>/tools/structure-viewer/settings.properties`. Explicit choices persist across launches; there is no automatic quality override.

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

**Bounds**, **Wireframe**, **Stored air**, and **Y (-1 = all)** control inspection. **Entities** and **Block entities** display markers, including jigsaw/structure blocks. **Coordinates** enables marker labels and selected-block coordinates. Markers intentionally show through geometry; entities are markers rather than animated models. Root metadata is available in the Metadata tab.

## Sources and supported formats

Discovery includes `src/main/resources` and `src/generated/resources` under `data/<namespace>/structure/` or legacy `structures/`; `src/main/structure_blueprints`; and StructureGen resources beneath the selected build directory, `build`, and `.codex-build`. Tags/worldgen definitions are excluded. Rescan updates the browser; Open also accepts external files.

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
3. Local `run/mods` JARs, in filename order.
4. The `structureViewer.minecraftJar` JVM property, when supplied.
5. Local NeoForm Minecraft 1.21.1 client assets and the standard Windows launcher 1.21.1 JAR.

**Add assets** accepts a directory containing `assets/`, resource-pack ZIP, or mod/client JAR. The tool does not download, unpack, or redistribute Minecraft assets. Reload after editing assets. Diagnostics list actual sources; Clear added assets restores automatic sources.

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

Auto reload watches the selected structure, including replacement during export, with 500 ms debounce. Reloading the same source preserves camera/layer. An unreadable intermediate export keeps the previous preview and can recover on the next change. R and Reload work with watching disabled. Asset changes require manual reload.

Diagnostics report invalid IDs/palette references, duplicate coordinates, bounds, malformed payloads, cached/asset-defined state problems, missing assets, and unsupported renderers. Recoverable warnings permit previewing. Unreadable/ambiguous data or resource limits reject the import. Duplicate JSON keys are rejected rather than guessed.

```powershell
.\gradlew.bat structureValidator '-PstructureViewerFile=src/main/structure_blueprints/test_shelter.json' '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat -p tools/structure-viewer validate '-PstructureViewerFile=src/main/structure_blueprints/test_shelter.json'
```

The headless validator shares import/model resolution. Readable files return success with warnings; unreadable files fail. It is not a strict registry acceptance gate. It uses automatic asset sources, not the GUI's saved pack list.

## Architecture and lifecycle

The separate `tools/structure-viewer` application owns its classpath. Gson is its only runtime library; Java desktop APIs provide the UI/software renderer and JUnit is test-only. The root mod does not depend on or package the viewer.

- `model`: shared immutable structure records and typed metadata.
- `io`: bounded readers, discovery, and file watching.
- `assets`: ordered sources, existing catalog adapter, model/texture resolution.
- `render`: cached model faces, camera, perspective-correct textures/picking, quality, markers.
- `ui`: browser, preferences, asynchronous import/render, inspection.
- `validation`: shared diagnostics and headless entry point.

StructureGen's existing reader depends on Minecraft and compiler-oriented validity. The isolated viewer adapter follows its formats without pulling in Minecraft or creating another compiler, mission owner, registry, or world-state system.

Swing/camera are owned by the event thread. One loader prepares data/meshes, one renderer produces complete frames, and the watcher reports changes. Generation checks reject stale load results. Closing the window closes the watcher, cancels loading, and stops input/render workers. Archives close after preparation. Frames are requested when the view changes.

NBT limits: 64 MiB file, 256 MiB decoded data, depth 64, 4 million collection entries, 40 million tags. JSON limits: 64 MiB, depth 64, 8 million values. Assets are capped at 8 MiB each; texture dimensions/pixels, cache (32 million pixels), model parent depth, and faces are bounded. The launch heap is 2 GiB. Dense structures/Ultra can be expensive; reduce quality or use layers. Inspector text is capped at 32 KiB. Markers are capped at 10,000 prepared, 1,000 drawn, and 30 labels per frame.

Phase 3 remains future work: mission-aware markers, thumbnails, additional formats, and separately scoped editing.

## Verification

Run one Gradle/Minecraft development operation at a time:

```powershell
.\gradlew.bat :tools:structure-viewer:test '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :tools:structure-viewer:uiSmokeTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :tools:structure-viewer:build '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :build '-PcodexBuildDir=.codex-build' --no-parallel
```

Tests cover parsing, typed metadata, source immutability, discovery, asset priority, inheritance/multipart, missing/cyclic/custom models, catalog defaults/recovery, partial-block visibility, edges/picking, camera, preferences, and atomic file replacement. Real vanilla integration runs if local 1.21.1 assets exist; otherwise that test is explicitly skipped.

The desktop smoke task opens the actual app, checks selection/freecam/mouse look/orbit, Fast/Ultra resolution, saved quality, G focus, and JSON reload/camera preservation/failure recovery. It uses copied fixtures and isolated preferences, sends input only to its own Swing controls, captures `<build>/tools/structure-viewer/reports/ui-smoke` screenshots, and closes its window. `-PstructureViewerSmokeFile=<path>` selects another template.

Manual acceptance: inspect stairs/doors and mod blocks, use High/Ultra plus edges, click and press G, fly inside, isolate a Y layer, check markers/metadata, and re-export through normal authoring tools. Minecraft remains the final reference for unsupported/dynamic behavior.