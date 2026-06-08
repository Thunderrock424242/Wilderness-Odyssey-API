# Riftfall (NeoForge 1.21.1) — Gameplay + Implementation Plan

## 1) Feature Pillars

- **Vanilla-first compatibility:** Riftfall is an overlay state that can only exist while Minecraft rain/thunder is active.
- **Rare + cinematic:** Most storms stay normal; some escalate into Riftfall with clear warnings.
- **Danger without default griefing:** Exposure, mobs, and corruption are threatening, but player builds are protected by default.
- **Configurable difficulty:** Pack authors can tune rarity, lethality, corruption scope, and meteor damage.
- **Performance-safe:** Small, interval-based sampling around players; no massive scans or per-tick heavy loops.
- **Lore-fit:** Meteor/anomaly origin, Atlas warnings, purple anomaly visuals, Riftborn activity, and Meteor Surge peaks.

---

## 2) Weather Integration Model (Vanilla Base, Riftfall Overlay)

## Canonical rule
Riftfall **never replaces** vanilla weather flags. It augments them.

- Vanilla checks (`isRaining`, `isThundering`) remain true/false exactly as Minecraft sets them.
- Riftfall state is separate mod data: `RiftfallStage`, intensity, timers, and per-player exposure.
- If vanilla rain ends, Riftfall transitions to `ENDING` immediately, then `CLEAR`.
- If weather is force-cleared (`/weather clear`), Riftfall must begin ending.

## Why this preserves mod compatibility
Other mods keep seeing normal rain/thunder and continue functioning (crop logic, mob behavior hooks, visuals, machines) because the base weather system is untouched.

---

## 3) State Machine

```text
CLEAR -> WARNING -> ACTIVE -> (optional) METEOR_SURGE -> ENDING -> CLEAR
```

## Stage behaviors

1. **CLEAR**
   - No Riftfall logic beyond eligibility checks.
   - Vanilla weather untouched.

2. **WARNING**
   - Triggered only during active vanilla rain/thunder.
   - Early telegraphing: sky tint ramp, low rumble, sparse anomaly particles.
   - Atlas warning broadcast can fire once per warning start.

3. **ACTIVE**
   - Core Riftfall gameplay.
   - Player `Rift Exposure` rises only when sky-exposed and unprotected.
   - Low-rate `Chrono Corrosion` attempts on natural, sky-exposed blocks near players.
   - Controlled Riftborn spawns near exposed players.

4. **METEOR_SURGE** (rare sub-phase)
   - Optional peak with higher intensity.
   - Increased meteor events and Riftborn pressure.
   - By default, meteors are mostly VFX + loot + optional mob spawn, not terrain grief.

5. **ENDING**
   - Visual/audio fade-out; exposure gain halted.
   - Spawn/corrosion systems ramp down or stop.
   - Exposure decays faster until clear.

---

## 4) Triggering + Rarity Logic

## Hard gates
- Current level weather must be raining or thundering.
- Global cooldown must be complete.
- Optional biome/region weighting can influence chance (anomaly biomes, impact sites).

## Suggested chance model
Evaluate every `N` seconds while raining:

- Base chance per check: low (e.g., `0.2%`).
- Thunder multiplier: higher likelihood (e.g., `x2.5`).
- Regional anomaly multiplier: modest (e.g., `x1.25`).
- Daily hard cap option (configurable) to avoid streaks.

## Cooldown
After `ENDING -> CLEAR`, start a cooldown timer (e.g., 2–5 in-game days configurable).

This keeps Riftfall rare and prevents every storm from feeling identical.

---

## 5) Rift Exposure System

## Gain conditions (all required)
- Stage is `ACTIVE` or `METEOR_SURGE`.
- Player is in rain-capable dimension (config list/blacklist).
- Player has direct sky access (`canSeeSky` check at position/head).
- Player is not in a protected context.

## Protection contexts
- Under solid roof/indoors.
- In bunker safe zone volume/tag.
- Wearing future protective gear tag/set bonus.
- Near future purifier/filter block aura.

## Exposure levels
Track normalized value (0–100):

- **Low (0–34):** subtle screen edge tint, light audio artifacts.
- **Medium (35–69):** mild slowness/mining fatigue, detection bonus for Riftborn.
- **High (70–100):** stronger debuffs/visual distortion, higher Riftborn aggro weight.

## Decay
- Faster decay under shelter or safe zones.
- Slow ambient decay when storm ends.
- Optional consumables can reduce exposure later.

---

## 6) Chrono Corrosion (Environment)

## Design constraints
- Not overly destructive.
- Natural blocks first.
- Low chance, interval driven.
- Sky-exposed only by default.
- Player builds protected by default.

## Default target strategy
Only sample a small ring/cluster around each relevant player every few seconds:

1. Pick random candidate positions in radius.
2. Reject if not sky-exposed.
3. Reject if protected claim/build tag and protection enabled.
4. If natural block + roll succeeds, apply transformation table.

## Example transformation tables
- `grass_block -> anomaly_tainted_soil` (rare)
- `dirt -> ash_soil / cracked_dirt`
- `stone -> cracked_stone`
- `leaves -> decayed_leaves` or accelerated decay tick
- `crops -> wither/growth mutation` (only if enabled)

Use datapack-driven tags + codec-based tables so pack authors can expand safely.

---

## 7) Riftborn Spawning

## ACTIVE phase
- Small spawn budget per player per interval.
- Use vanilla-safe spawn checks (light/space/path).
- Respect local mob caps and distance rules.

## METEOR_SURGE phase
- Temporary spawn budget increase.
- Surge-only weighted entries available.

## Anti-overwhelm controls
- Per-player max nearby Riftborn.
- Global concurrent cap.
- Cooldown between spawn bursts.

---

## 8) Meteor Surge Design

## Meteor event pipeline
1. Pick candidate near exposed player at safe radius.
2. Validate against protection zones and build-safety rules.
3. Spawn meteor projectile/event entity.
4. On impact: VFX + sound + optional loot cache + optional Riftborn.
5. Optional crater/block damage only if config enables it.

## Default behavior
- Primarily cinematic and reward-oriented.
- Minimal/no block damage by default.
- Avoid direct impacts inside protected bases.

---

## 9) Atlas Integration

Send stage-based messages with anti-spam cooldown:

- WARNING start: `ATLAS WARNING: Atmospheric anomaly detected.`
- ACTIVE start: `Riftfall formation detected. Chrono Corrosion levels rising.`
- SURGE start: `Meteor activity increasing. Shelter recommended.`
- High exposure: `Exposure levels elevated.`
- ENDING start: `Storm intensity decreasing. Remain cautious.`
- Nearby surge spawns: `Riftborn movement detected nearby.`

Use translatable keys for localization and pack customization.

---

## 10) Config Surface (Server)

## Core toggles
- `enabled`
- `rarity.baseChance`
- `rarity.thunderMultiplier`
- `rarity.anomalyRegionMultiplier`
- `cooldownTicks`

## Stage timing
- `warningMinTicks`, `warningMaxTicks`
- `activeMinTicks`, `activeMaxTicks`
- `surgeChancePerStorm`
- `surgeMinTicks`, `surgeMaxTicks`
- `endingTicks`

## Exposure
- `exposureGainPerSecond`
- `exposureDecayShelterPerSecond`
- `exposureDecayClearPerSecond`
- `debuffThresholds`

## Corrosion safety
- `protectPlayerBuilds=true`
- `allowNaturalBlockCorrosion=true`
- `allowPlacedBlockCorrosion=false`
- `allowCropDamage=false` (or true for harder presets)
- `corrosionChecksPerPlayerInterval`
- `corrosionIntervalTicks`

## Meteor safety
- `allowMeteorBlockDamage=false`
- `allowMobGriefingDuringRiftfall=false`
- `meteorEventsPerMinute`
- `meteorImpactLootChance`

## Spawn balance
- `riftbornSpawnBudgetActive`
- `riftbornSpawnBudgetSurge`
- `maxRiftbornPerPlayer`
- `maxRiftbornGlobal`

---

## 11) Data + API Architecture (NeoForge)

## Server-side systems
- `RiftfallWeatherController` (world singleton via SavedData or level capability)
  - Owns stage machine, timers, cooldowns, rarity checks.
- `RiftfallExposureManager`
  - Tracks per-player exposure values.
- `ChronoCorrosionManager`
  - Interval block sampling/transforms around players.
- `RiftbornSpawnDirector`
  - Budgeted spawn orchestration.
- `MeteorSurgeDirector`
  - Meteor scheduling and safe impact resolution.

## Client-side systems
- `RiftfallClientFX`
  - Sky tint/fog blend, rain shader tint, particles, screen FX.
- `RiftfallAudioController`
  - Rumble/whispers/surge booms with stage intensity.

## Networking
- Minimal S2C packets:
  - stage + intensity + remaining stage time
  - player exposure value and tier
- Avoid per-tick packet spam; send on change or coarse interval.

## Datapack extensibility
- Block tags: natural/corrosible/protected/placed exemptions.
- Loot tables for meteor rewards.
- Spawn tables for Riftborn by biome/time/stage.
- Optional JSON-defined corrosion mappings.

---

## 12) Performance Budget

- No large-radius world scans.
- No per-tick full block iteration.
- Anchor all heavy operations to active players.
- Stagger interval jobs (e.g., modulo world time + player UUID hash).
- Cap spawned entities/events per minute globally.
- Keep visuals client-local; server computes only gameplay-critical state.

Recommended first-pass intervals:
- Exposure checks: every 20 ticks.
- Corrosion sampling: every 80–120 ticks/player.
- Spawn director: every 40 ticks.
- Meteor planner: every 60 ticks in surge.

---

## 13) Compatibility Rules

- Never force-set weather states except optional admin commands.
- Observe existing vanilla weather outcomes and adapt Riftfall stage accordingly.
- Respect gamerules and mob caps.
- Keep block edits conservative and tag-driven.
- Provide config to disable each dangerous subsystem independently.

## Command/sleep interactions
- If players skip night and weather clears, Riftfall moves to `ENDING`.
- If `/weather thunder` is used, Riftfall becomes eligible but still obeys rarity/cooldown unless admin-forced.
- Optional admin command: `/riftfall force <stage>` for testing only.

---

## 14) Balance Presets

## Default (recommended)
- Rare trigger chance, long cooldown.
- Slow exposure gain.
- Light Riftborn pressure.
- Natural-block corrosion only.
- No meteor terrain damage.
- Strong player-build protection.

## Hardcore preset
- Higher trigger chance, shorter cooldown.
- Faster exposure gain and stronger debuffs.
- Higher Riftborn budgets.
- Optional crop and placed-block corrosion.
- Optional meteor crater damage.

---

## 15) Incremental Implementation Roadmap

## Milestone 1 — Backbone
- Add stage machine + cooldown + weather coupling.
- Add config schema + serialization.
- Sync stage/intensity to clients.

## Milestone 2 — Exposure + Warnings
- Implement exposure gain/decay + debuff tiers.
- Implement Atlas stage warnings + anti-spam.
- Add basic sky tint and ambience.

## Milestone 3 — Corrosion (safe defaults)
- Add natural-block sampling + low-rate transforms.
- Add build protection and placed-block toggle.
- Add datapack tags/mappings.

## Milestone 4 — Riftborn Director
- Add ACTIVE and SURGE spawn budgets.
- Add caps/cooldowns and safe spawn checks.

## Milestone 5 — Meteor Surge
- Add rare surge entry + meteor event pipeline.
- Default impact mode: VFX/loot, no block damage.
- Optional crater damage via config.

## Milestone 6 — Polish + Compat QA
- Integrate final audio/visual ramps.
- Test with common weather-dependent mods.
- Profile tick cost in multiplayer scenarios.

---

## 16) QA / Test Matrix

## Functional
- Riftfall never starts in clear weather.
- Thunder has higher trigger rate than rain.
- Cooldown prevents back-to-back storms.
- Exposure only rises under open sky in active stages.
- Exposure decays under shelter and after storm.

## Safety
- Player builds unchanged with default config.
- Meteors do not crater terrain by default.
- Corrosion mostly affects natural blocks.

## Compatibility
- Mods checking vanilla rain/thunder still behave normally.
- Sleep/weather commands transition Riftfall correctly.

## Performance
- Tick-time impact acceptable with 1, 10, and 30 players.
- Entity counts remain within caps during surge.

---

## 17) Explicit Non-Feature

- **No toxic air system is included in Riftfall.**

Riftfall danger comes from exposure, corrosion, Riftborn activity, and meteor surges—not breathable-atmosphere penalties.
