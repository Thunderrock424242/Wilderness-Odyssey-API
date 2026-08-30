# Configuration layout

Wilderness Odyssey registers exactly three NeoForge configuration files:

| File | NeoForge scope | What belongs there |
| --- | --- | --- |
| `wildernessodysseyapi-common.toml` | `COMMON` | Installation-wide structure, async-threading, and ownership settings |
| `wildernessodysseyapi-client.toml` | `CLIENT` | Local reminders, debug HUD, water rendering, and weather rendering |
| `wildernessodysseyapi-server.toml` | `SERVER` | World/server-authoritative gameplay, simulation, telemetry, and performance settings |

All three are stored under `config/wildernessodysseyapi/`. The separation keeps
NeoForge's side and lifecycle rules intact while categories keep the individual
systems readable.

## Categories

The common file contains `[structures]`, `[asyncThreading]`, and `[ownership]`.

The client file contains `[donations]`, `[debug_hud]`, `[water_rendering]`, and
`[weather_rendering]`. Existing feature sections remain nested below those roots;
for example, localized cloud settings live under
`[weather_rendering.localized_clouds]`.

Water rendering uses automatic hardware quality by default. Under
`[water_rendering]`, `autoDetectWaterQuality = true` selects the effective tier
once per client launch from GPU/VRAM, CPU capacity, physical RAM, Minecraft
heap, and display resolution. Set it to `false` when the explicit
`waterQuality = "LOW"`, `"MEDIUM"`, `"HIGH"`, or `"CINEMATIC"` value should
remain authoritative.

The server file contains the feature roots `[structure_blocks]`, `[performance]`,
`[verificationRelay]`, `[telemetry]`,
`[playerTelemetry]`, `[eventTelemetry]`, `[feedback]`, `[riftfall]`,
`[meteor_event]`, `[temporal_rift]`, `[water_simulation]`, `[weather]`,
`[ecosystem]`, and `[reactiveVegetation]`. Existing feature-specific subsections remain nested under
their owner. For example:

```toml
[performance]
enabled = true

[performance.backgroundEfficiency]
enabled = true

[weather]
enabled = true

[ecosystem]
enabled = true
```

The performance master switch controls only Wilderness-owned performance work.
It never replaces Minecraft's tick loop or assumes ownership of vanilla or
modded chunks, entities, block entities, networking, saving, or world lifecycle.

## Migration from feature files

When a unified destination does not yet exist, startup collects the corresponding
legacy feature files into it. The migration:

- preserves existing values and comments;
- adds only the category nesting required by the new layout;
- creates the destination through a temporary file in the same directory;
- never overwrites an existing unified file; and
- never deletes, renames, or edits a legacy file.

The old files therefore remain as rollback copies, but NeoForge no longer
registers them after consolidation. Once the three new files have been checked,
an administrator may archive the legacy files manually. If safe creation fails,
registration stops with a readable error instead of silently discarding tuning.

The earlier performance migration is still honored: the former background,
tick-engine, and data-engine files are first combined into the legacy performance
layout when needed, then that layout is included under `[performance]` in the new
server file.
