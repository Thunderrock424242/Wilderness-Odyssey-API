# Configuration layout

Wilderness Odyssey is moving from many feature files to a smaller, scope-aware
configuration suite. “Common” in this project means a shared user-facing layout;
it does not erase NeoForge's distinct `CLIENT`, `COMMON`, and `SERVER` ownership
types.

Those types must remain separate:

- Client settings belong to the local player and must never load on a dedicated server.
- Common settings are installation-wide inputs loaded on both physical sides.
- Server settings are world/server authoritative and may be supplied per world.

Combining all three into one `ModConfigSpec` would give settings the wrong
lifecycle and synchronization behavior. Consolidation therefore proceeds as a
small suite with nested feature sections and explicit, non-destructive migrations.

## Implemented first group

`wildernessodysseyapi-performance-server.toml` contains:

```toml
[performance]
enabled = true

[performance.backgroundEfficiency]
enabled = true

[performance.tickEngine]
enabled = true

[performance.dataEngine]
enabled = true
```

The master switch controls only Wilderness-owned performance work. It never
replaces Minecraft's tick loop or assumes ownership of vanilla/modded chunks,
entities, block entities, networking, saving, or world lifecycle.

If the unified file is absent, startup can migrate values from these exact files:

- `wildernessodysseyapi-background-efficiency-server.toml`
- `wildernessodysseyapi-tick-engine-server.toml`
- `wildernessodysseyapi-data-engine-server.toml`

Migration writes a temporary file in the same config directory and moves it into
place without overwrite. It never deletes, renames, or edits a legacy file. If
safe creation fails, registration stops with a readable error instead of silently
discarding administrator tuning.

Other feature files remain authoritative until a later scope-compatible group has
its own migration and reload coverage. They should not be manually merged into
the performance file.
