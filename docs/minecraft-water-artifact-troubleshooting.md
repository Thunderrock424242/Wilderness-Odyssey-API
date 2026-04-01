# Minecraft/NeoForge Water Artifact Troubleshooting

If you see large, translucent "walls" or sheet-like cubes in oceans/lakes (similar to the screenshots), the issue is usually one of these:

## Most likely causes

1. **Broken fluid mesh generation in a mod**
   - A custom renderer or fluid optimization mod can submit incorrect quads for water.
   - Symptoms: geometric sheets visible both above and below water.

2. **Shader/renderer incompatibility**
   - Shader packs or rendering mods can mis-handle fluid faces.
   - Symptoms: artifacts change when enabling/disabling shader pipeline.

3. **Chunk mesh cache desync**
   - The client keeps stale chunk render data after mod reloads/world updates.
   - Symptoms: artifact disappears after relog, F3+A, or changing video settings.

4. **Mixed mod versions (client/server mismatch)**
   - Different versions of rendering/worldgen mods can produce visual corruption.

## Fast isolation workflow (NeoForge dev)

1. Launch with **no shaders** and default video settings.
2. Temporarily disable non-essential rendering/fluid mods.
3. Rebuild and run with:

```bash
./gradlew clean runClient
```

4. In-game, force chunk rebuild (**F3 + A**) and re-enter the world.
5. If still reproducible, test a clean profile with only:
   - NeoForge
   - Your mod
   - Required libraries

## Debugging checklist for mod authors

- Verify fluid mesh builders only emit valid faces per block side.
- Validate fluid height sampling and neighboring block checks.
- Ensure render-layer assignment matches expected translucent layer.
- Confirm no custom mixin cancels/overrides vanilla fluid tesselation unexpectedly.
- Add debug logging around fluid chunk rebuild paths.

## Practical next step in this repository

Use this bug report template when tracking future visual artifacts:

- Game version:
- NeoForge version:
- Modpack/mod list diff from baseline:
- Shader pack + renderer mods:
- Repro seed/coords:
- Whether F3+A/relog changes behavior:
- Screenshot/video:

Capturing these details up-front usually cuts diagnosis time significantly.
