# Improving Minecraft World Upgrading (NeoForge)

This document outlines a practical, mod-side world-upgrade system for Minecraft modpacks using NeoForge.

## Problem

Minecraft chunk/world upgrade paths focus on version/schema migration, not semantic mod-content migration. In modpacks, structures, biome logic, features, loot, and block/entity behavior can change between mod versions, but existing chunks generally do not get those content updates automatically.

## Goal

Provide **safe, in-place, versioned migrations** for already-generated worlds/chunks, without moving existing structures and without destroying player modifications.

## Core Design

### 1) Versioned Migration Registry

Create a migration registry with ordered steps:

- `fromVersion -> toVersion`
- chunk-scoped, region-scoped, or global migrations
- idempotent operations (safe to re-run)

Example interface sketch:

```java
public interface WorldMigration {
    int fromVersion();
    int toVersion();
    MigrationScope scope();
    boolean apply(MigrationContext context);
}
```

Store current migration version in `SavedData`.

### 2) Persistent Progress Tracking

Track:

- world migration target version
- per-chunk last-applied migration version
- queue state/checkpoints

Use:

- `SavedData` for world-level state
- attachment/capability/chunk persistent NBT for per-chunk metadata

### 3) Incremental Work Queue

Run upgrades over multiple ticks to avoid lag spikes:

- bounded tasks per tick
- priority queue (loaded chunks first)
- pause/resume support
- server command controls (`start`, `pause`, `status`, `retry-failed`)

### 4) Diff/Patch Strategy (No Structure Movement)

For structures/features in existing chunks:

- anchor migration to original structure origin
- patch changed blocks/entities by rules, not full regen
- skip protected/player-modified blocks
- optional dry-run report of intended changes

### 5) Safety Policies

Define replacement rules:

- replace only known legacy blocks/tags
- preserve inventories/ownership data
- preserve tile entity custom NBT unless explicitly migrated
- avoid replacing air into solid or vice versa unless intended

### 6) Compatibility Layer

Handle missing mods/renamed IDs gracefully:

- old ID -> new ID mapping table
- fallback block/entity if target missing
- log unresolved mappings once per chunk

### 7) Observability

Add structured logs and metrics:

- migrations attempted/succeeded/failed
- average ms per task
- chunks remaining
- last error by migration ID

Expose `/worldupgrade status` for operators.

## Suggested NeoForge Integration Points

- start scheduling on server start after world load
- process queue on server tick
- enqueue chunk tasks when chunks load
- expose admin commands for control and diagnostics

## Operational Workflow for Modpacks

1. Ship new pack with migration version increment.
2. Server boots and detects outdated world/chunks.
3. Operators run dry-run report in staging world.
4. Run migrations in production with bounded TPS impact.
5. Validate completion report and unresolved mappings.

## Failure Handling

- per-task retries with backoff
- quarantine repeatedly failing chunks
- continue processing others
- export failure list for manual repair tools

## Why This Is Better Than Vanilla-Only Upgrade

Vanilla upgrades data format compatibility; this system upgrades **content semantics** for mods:

- retrofits existing chunks
- keeps structures in place
- minimizes griefing of player-built areas
- provides controlled, observable rollout

## Minimal First Milestone

Implement only:

- world version + per-chunk version tracking
- one migration type (legacy block replacement)
- bounded tick queue
- status command

Then expand into structure patching and entity/loot migrations.
