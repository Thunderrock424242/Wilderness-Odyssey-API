# Standalone Structure Viewer — Phase 1

The viewer is a read-only Java 21 desktop application under `tools/structure-viewer`. It previews Java-edition structure-template NBT without starting Minecraft, loading a world, resolving a game registry, or changing the source template.

## Launch

From the repository root, with JDK 21 configured:

```powershell
.\gradlew.bat structureViewer
.\gradlew.bat structureViewer '-PstructureViewerFile=src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt'
```

The module also launches directly:

```powershell
.\gradlew.bat :tools:structure-viewer:run
.\gradlew.bat :tools:structure-viewer:run --args='--open "path/to/structure.nbt"'
```

For complete independence from the mod's Gradle configuration, use the same checked-in wrapper with the tool's project directory:

```powershell
.\gradlew.bat -p tools/structure-viewer run
```

All three entry points use the same implementation and read the same repository resource roots. IntelliJ can run `structureViewer` from the **Wilderness Odyssey Tools** Gradle group, or run the module's `run` task. Import the nested project separately if only the standalone tool is needed. No runClient task is involved.

Use `'-PcodexBuildDir=.codex-build'` for isolated validation. Tool output is placed below `<selected-build>/tools/structure-viewer`; it never shares the mod's class or resource output.

## Controls

Click the viewport to give it keyboard focus.

| Control | Action |
|---|---|
| F1 | Orbit camera |
| F2 | Free camera; movement passes through blocks |
| Right mouse drag | Look around / orbit |
| W / S | Forward / backward along the view direction |
| A / D | Strafe left / right |
| Space / Shift | Up / down |
| Ctrl while moving | Precision movement |
| Wheel | Free-camera movement speed; orbit-camera distance |
| F | Recenter and fit the structure |
| Left click | Select a visible block and open its inspector |
| R / Reload | Re-read the selected NBT file |
| Y layer -1 | All layers; nonnegative values show only that Y level |

Air visibility displays **explicitly stored air**, not every absent coordinate in the bounding volume. Bounds are a diagnostic overlay and can show through geometry. Wireframe shows the renderer's triangles. Layer changes preserve the camera; loading/reloading a file recenters it.

## Browser and inspection

The browser discovers both `structure/` and legacy `structures/` directories beneath:

- `src/main/resources`
- `src/generated/resources`
- `<selected-build>/generated/structuregen/resources`
- Existing `build/generated/structuregen/resources` and `.codex-build/generated/structuregen/resources`

Authored and generated copies retain separate browser groups and exact source paths. Rescan updates the file list; Open NBT accepts an external template. The viewer never generates or modifies structure resources, scans world saves, or invokes datagen. Existing StructureGen remains responsible for compiling `src/main/structure_blueprints`.

Structure details include dimensions, stored/non-air block counts, palette size and variants, block-entity records, and entity count. Selection reports the original ID, coordinates, palette/source indices, all state properties, typed block-entity NBT, and extra block-entry fields. The Metadata tab preserves root data including `DataVersion`, StructureGen markers, metadata, block-marker mappings, and content manifests.

## Architecture and production isolation

- `model/StructureData`: common data representation with positions, blocks, palettes, entities, and metadata.
- `model/NbtValue`: typed payload tree; the inspector preserves numeric and array types.
- `io/StructureReader`: adapter boundary for future formats.
- `io/NbtStructureReader` and `NbtInput`: bounded raw/gzip decoding. Blocks are processed incrementally instead of retaining a second giant NBT tree.
- `io/StructureDiscovery`: known project resource roots.
- `render/BlockMesh`: removes enclosed cube faces and applies air/layer filters once per change.
- `render/BlockAppearance`: replaceable Phase 1 color/placeholder policy.
- `render/SoftwareRenderer`: clipped triangles, depth buffer, and matching block-ID buffer. Selection follows visible pixels, including from inside a structure.
- `render/Camera`: camera math independent of Swing.
- `ui/ViewerWindow`: browser, import lifecycle, and details.
- `ui/StructureViewport`: input, frame scheduling, and a dedicated render worker.

The existing `structuregen.model.StructureModel` establishes the project's palette and metadata conventions, which this adapter preserves. Its current `MinecraftStructureNbtReader` depends on Minecraft NBT/resource classes and enforces compiler-oriented validity. Reusing that runtime would pull Minecraft into this tool. Phase 1 therefore uses a small, tolerant binary adapter without extracting or changing the production StructureGen subsystem. A later shared format library can consolidate these boundaries if needed.

The tool has **no production runtime dependencies** beyond Java 21's desktop APIs. JUnit is test-only. Neither the root mod dependencies nor `sourceSets.main` include this project. It is not added to NeoForge registration, networking, jar-in-jar packaging, or gameplay startup.

The UI thread owns Swing and the camera. One loading worker prepares structures/meshes, with cancellation and request generations preventing stale loads from replacing newer selections. A separate single rendering worker publishes complete frames. Closing the window cancels loading, stops the input timer, and shuts down both workers.

## Limits and accuracy

Phase 1 is an **approximate cube preview**, not an exact Minecraft renderer.

- Every block uses a shaded cube. Doors, stairs, fences, panes, fluids, block-entity models, textures, transparency, animation, lighting, and entities do not yet render as in Minecraft.
- Non-Minecraft IDs and broken palette references use magenta placeholders. Original IDs and state properties are retained.
- Minecraft-looking IDs are not checked against a live registry; the diagnostics explicitly distinguish import checks from missing-block/state validation.
- Multiple palettes are preserved; the first is rendered.
- Invalid palette references, out-of-bounds blocks, malformed block-entity payloads, and duplicate root metadata are reported where recovery is possible. Duplicate visible coordinates show the last record and are counted.
- Unreadable binary data, invalid dimensions, excessive collections, or ambiguous duplicate compound keys stop that import; the previous preview remains visible.
- Limits: 64 MiB file, 256 MiB decoded bytes, 4,000,000 entries per collection, 40,000,000 decoded tags, and nesting depth 64. The launch heap ceiling is 2 GiB.
- NBT text display is capped at 32 KiB, and import diagnostics at 200 messages plus a truncation notice. Original imported metadata remains retained.
- Software rendering is capped at 1100 × 800 internal pixels, skips enclosed/back-facing/offscreen faces, and only requests new frames when the view changes. Large exposed or air-filled structures can still be expensive; use a Y layer to inspect them.

## Validation

Use the normal Gradle pipeline, with only one Codex-controlled Gradle/Minecraft process active at a time:

```powershell
.\gradlew.bat -p tools/structure-viewer test '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat -p tools/structure-viewer uiSmokeTest '-PcodexBuildDir=.codex-build' --no-parallel
.\gradlew.bat :build '-PcodexBuildDir=.codex-build' --no-parallel
```

The unit suite covers raw/gzip import, typed payloads, unknown mod IDs, alternate palettes, malformed data, discovery, existing bunker immutability, occlusion/picking, near-plane clipping, and camera motion.

The GUI smoke task requires a desktop. It runs the real application entry point, opens the existing generated test shelter (or the authored bunker if unavailable), dispatches input only to its own Swing controls, checks inspection/freecam/mouse-look/orbit behavior, captures screenshots beneath `<build>/tools/structure-viewer/reports/ui-smoke`, and closes its own window. It does not send global input to other applications. Use `-PstructureViewerSmokeFile=<path>` to select a specific existing template.

Manual acceptance: launch the viewer; select bunker or a generated structure; press F2 and right-drag to look; fly through a doorway with WASD/Space/Shift; change speed and try Ctrl; select a block and inspect its ID/state/NBT; switch to orbit; isolate a layer; replace/re-export the template through the existing authoring workflow and press Reload.

## Later phases

Phase 2 adds asset-backed blockstate/model/texture resolution, better shape fidelity, a defined JSON structure adapter, deeper registry/model validation, additional overlays, and file watching. It should reuse existing Blueprint-v1 semantics rather than treating arbitrary worldgen JSON as a structure.

Phase 3 adds marker visualization and mission integration through existing Wilderness Odyssey ownership, thumbnails, other import formats, and separately designed editing workflows. Phase 1 preserves the data needed for these additions but does not create new mission state or world-editing paths.

