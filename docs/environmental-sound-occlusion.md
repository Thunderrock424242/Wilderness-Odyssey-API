# Environmental sound occlusion

Wave crashes, coastal wash/retreat, river accents, localized rain (including
rain on water), and distant thunder share a client-side obstruction check.
Their existing water/weather systems still own sound selection, cadence, pitch,
position and base volume. There are no new packets, server simulation changes,
sound-engine mixins, native audio resources or dependencies.

`EnvironmentalSoundOcclusion` applies `SoundOcclusionModel` when those sparse
emitters play. Three CPU rays start at the camera/listener and sample a small
aperture around the sound position. A voxel traversal measures how far each ray
passes through block collision shapes. Open doors and gaps can transmit sound;
thicker barriers reduce it further. Material coefficients distinguish wool,
wood, leaves and other solid shapes. These are approximate tuning values, not
measured acoustic properties.

This is ray-based **volume occlusion**. It does not provide frequency filtering,
reflected echoes, diffraction around corners, GPU ray tracing, or a replacement
for a full acoustic simulation. The multiplier is selected at emission time:
an already-playing sample does not update continuously when a door opens or
the listener moves. Newly emitted sounds use the new geometry/listener position.

## Bounds and lifecycle

- Each ray examines at most the first 64 blocks from the listener and visits at
  most 192 voxels. Distant sources therefore get a local shelter approximation.
- All participating emitters share a limit of 1,024 voxel queries per game tick.
  Tracing runs only when an emitter needs sound, never from a per-frame scan.
- Only loaded client chunks are read, on the client thread. An unavailable
  chunk or exhausted budget ends that ray, retaining attenuation from obstacles
  already found. Unknown space does not invent additional barriers.
- Collision shapes with more than 32 boxes use their bounding box as a
  conservative approximation. Box overlap cannot exceed the ray's voxel length.
- The query budget resets on client-level unload. No world/player reference,
  sound instance, cache, thread pool or scheduled task is retained.
- The model retains a small transmission floor (0.5% before the existing
  distance/category volume), so it does not cancel gameplay or sound events.

Distant thunder uses a nearby directional playback proxy. Its occlusion rays
instead point upward toward the storm bearing, so a proxy inside a large room
cannot bypass the roof. Rain rays target just above the impact surface to avoid
treating the sound's own ground block as an obstruction.

## Configuration and compatibility

In the existing client file
`config/wildernessodysseyapi/wildernessodysseyapi-client.toml`:

```toml
[environment_audio]
enable_occlusion = true
occlusion_strength = 1.0
```

Strength ranges from 0 (original mix) to 1 (full obstruction attenuation).
Disabling the toggle also restores the original mix. Other sound volume controls
continue to apply. No existing world or save data needs migration.

When the `sound_physics_remastered` mod is installed, this helper automatically
returns unity gain so that mod owns acoustic processing. This installation check
also yields when that mod's own effects are disabled; enable its effects or
remove it to use this fallback. With another acoustic-processing mod, disable
`enable_occlusion` to avoid applying two independent attenuation systems.

This initial coverage is limited to the emitters listed above. UI/music/voice,
local cave/glacial ambience, cinematic cues, entity sounds and other mods' sounds
are not routed through this helper. Existing Echo dimension sound suppression
retains its own ownership.

## Validation

Pure JUnit regressions cover open paths, thick walls, roofs, negative coordinates,
axis boundaries, thin door shapes, partial openings, unavailable terrain, invalid
endpoints and bounded distant traces. The existing audio-model tests continue to
cover coastal mixing, river cadence and distant-thunder selection.

```powershell
.\gradlew.bat compileJava -PcodexBuildDir=.codex-build --no-parallel
.\gradlew.bat renderingTest --tests 'com.thunder.wildernessodysseyapi.environment.audio.*' --tests 'com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalBreakAudioModelTest' --tests 'com.thunder.wildernessodysseyapi.watersystem.water.render.RiverSoundscapeModelTest' --tests 'com.thunder.wildernessodysseyapi.weather.client.audio.*' -PcodexBuildDir=.codex-build --no-parallel
```

Client acceptance still requires listening in game:

1. Compare the same coast outdoors, behind a thin barrier, near the bunker
   entrance, and deep inside with the entrance closed. Thick enclosure should
   reduce surf much more than the outdoor case.
2. Open/close a door or make a direct opening toward the source, then wait for
   the next crash. Check slabs/doors/panes as well as full blocks.
3. Repeat near a river and during localized rain and distant thunder, including
   a large interior whose nearest wall is farther than the thunder proxy.
4. Compare with occlusion disabled and at reduced strength. Confirm pitch,
   cadence, category sliders and subtitles continue to behave as before.
5. Change dimension, disconnect/reconnect and reload config; confirm subsequent
   sounds use the new level/settings.
6. Repeat with Sound Physics Remastered. Its acoustic behavior should own the
   result; this helper should not add another attenuation multiplier.

Automated model tests do not prove the in-game mix, every modded block's collision
shape, external-mod compatibility, or measured frame-time/audio performance.
