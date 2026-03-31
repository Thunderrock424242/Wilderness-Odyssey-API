# Volumetric Fluid Mesh Renderer Plan (Water/Lava)

Goal: move from block-placement visuals to smooth, mesh-based fluid surfaces like modern realtime fluid demos.

## Current state

- Server hybrid simulation already tracks per-cell volume and velocity.
- Server now exposes `sampleSurface(...)` data (one top sample per X/Z column) to support a client renderer pipeline.
- `VolumetricSurfaceMesher` now converts those samples into mesh topology stats (quads/triangles/area) and optional triangle lists for renderer upload.
- Added `VolumetricSurfaceSyncPayload` and `VolumetricSurfaceSyncManager` to periodically sync sampled water/lava surfaces to clients.
- Added `VolumetricSurfaceClientCache` to store synchronized surface columns per-dimension for upcoming mesh rendering.
- Added first-pass `VolumetricSurfaceRenderer` client pass that draws translucent triangle meshes from the synced cache (preview quality) with camera-distance culling.
- Added `VolumetricFluidRenderConfig` (client) for renderer toggles, triangle budget, distance culling, stale-age cutoff, wave amplitude, and foam strength tuning.
- Added shader scaffolding files for water/lava (`assets/.../shaders/core/volumetric_surface_{water,lava}.{vsh,fsh,json}`) to transition preview rendering to custom shader-backed materials.
- Added runtime shader hookup classes (`VolumetricSurfaceShaders`, `VolumetricSurfaceRenderTypes`) so the preview renderer can use custom water/lava shader programs with fallback to vanilla position-color shader.
- Added shoreline-aware wave behavior (sand/beach detection), moon-phase wave scaling, and tide offset propagation into sampled surface vertices so beach areas receive stronger wave motion and ocean/river tides move the preview mesh vertically.
- Added `/volfluid tide` debug command output to inspect local water sample volume, shoreline factor, tide offset, moon-phase factor, and reconstructed surface height while tuning.

## Next implementation phases

1. **Networking**
   - Send nearby `SurfaceSample` batches to each tracking player on a fixed cadence (e.g. every 4 ticks).
   - Delta-compress by chunk section + quantized height/volume.

2. **Client cache**
   - Maintain short-lived per-dimension surface cache keyed by column.
   - Expire samples that are stale/unseen.

3. **Mesh generation**
   - Build a regular grid mesh in local chunks from cached samples.
   - Reconstruct smooth normals from height gradients.
   - Add edge skirts at cache boundaries.

4. **Material/shader pass**
   - Water: refraction tint + fresnel + foam mask from velocity/curvature.
   - Lava: emissive + heat distortion + crust mask based on low velocity / cooling rules.

5. **Interaction bridge**
   - Keep physics/hit checks on server cells while rendering is mesh-only on client.
   - Optional: disable world block placement (`visualOnly`) once mesh path is stable.

## Performance guardrails

- Cap samples per player radius (target <= 2k samples frame-visible).
- Rebuild mesh only for dirty regions.
- Quantize heights to 1/32 block to reduce packet size.
- LOD: coarser grid beyond 24-32 blocks from camera.

## Why this matches the target visual

The reference style is a *surface reconstruction* problem (heightfield/mesh + fluid shading), not block placement.
Using server volumetric cells as source data and rendering a shaded client mesh is the practical NeoForge path.
