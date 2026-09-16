# Configuration layout

Wilderness Odyssey registers exactly three NeoForge configuration files:

| File | NeoForge scope | What belongs there |
| --- | --- | --- |
| `wildernessodysseyapi-common.toml` | `COMMON` | Installation-wide structure, async-threading, ownership and private playtesting settings |
| `wildernessodysseyapi-client.toml` | `CLIENT` | Local reminders, debug HUD, water rendering, and weather rendering |
| `wildernessodysseyapi-server.toml` | `SERVER` | World/server-authoritative gameplay, simulation and performance settings |

All three are stored under `config/wildernessodysseyapi/`. The separation keeps
NeoForge's side and lifecycle rules intact while categories keep the individual
systems readable.

## Categories

The common file contains `[structures]`, `[asyncThreading]`, `[ownership]`,
`[verificationRelay]`, `[telemetry]`, `[playerTelemetry]`, `[eventTelemetry]` and `[feedback]`.
COMMON is local to the installation and never synchronized to joining clients.
Keep webhook credentials here on the server only; do not distribute production common configs.

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
`[riftfall]`,
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

## Private playtesting migration

Before registration, playtesting categories in the installation's former SERVER
file are moved into COMMON. Existing COMMON values win; missing values are
recovered from SERVER. COMMON is saved atomically before removing the old
SERVER categories. An invalid or unwritable file stops registration with a
redacted error. Other categories and legacy feature files are preserved.

NeoForge also permits per-world overrides under world/serverconfig or
saves/<world>/serverconfig. Those overrides are not automatically imported into
the installation-wide common file. Before upgrading a server using such an
override, copy its selected playtesting settings into COMMON and remove the
private categories from the override and any defaultconfigs template. Keep a
private backup. Never distribute webhook credentials in a world or client pack.

See [playtesting.md](playtesting.md) for safe configuration examples, relay
acknowledgements, operator status, retry behavior and multiplayer acceptance tests.