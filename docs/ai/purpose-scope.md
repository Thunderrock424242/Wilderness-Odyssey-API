# A.E.T.H.E.R System Purpose & Coverage (Wilderness Odyssey)

Use this as the handoff checklist for anyone building or integrating A.E.T.H.E.R features for the mod.

## 1) Core identity
- The system core is **A.E.T.H.E.R**.
- Current MVP: scripted recovered-intent responses, not a full LLM chatbot.
- A.E.T.H.E.R is intentionally limited. Missing data, corrupted records, and uncertainty are part of the character.
- Purpose: provide immersive, lore-aware support while staying safe, performant, and server-friendly.

## 2) Current response design
- Clean the player message.
- Gather cheap game context tags, such as dimension, biome, nearby meteor site, and collected lore IDs.
- Match authored intents with keyword scoring instead of exact phrases only.
- Pick an authored response variant.
- If no intent is known, answer in-universe with a recovered-data/corrupted-archive limitation.
- Show the reply in local chat/UI with the chosen A.E.T.H.E.R persona speaker.

## 3) A.E.T.H.E.R sub-systems
- **Aegis** - Health / Protection
  - Player safety guidance, hazard prevention reminders, defensive readiness.
- **Eclipse** - Rift / Anomaly
  - Anomaly/rift risk, unsafe reality behavior, and safe response prompts.
- **Terra** - Terrain / World Restoration / Exploration
  - Terrain intelligence, exploration routing, restoration-oriented world insights.
- **Helios** - Energy / Machines / Atmospheric Stability
  - Machine-power recommendations, atmospheric condition awareness, system stability advice.
- **Enforcer** - Combat / Security
  - Combat readiness guidance, threat prioritization, security posture prompts.
- **Requiem** - Archive / Memory / History
  - Lore memory, historical recall, archive-style narrative continuity.

## 4) Player-facing coverage
- Expedition guidance: early priorities, hazards, shelter, and resource scouting.
- Mission support: short progress-oriented reminders.
- Hazard awareness: unstable zones, contaminated air, dangerous terrain, rifts, and night defense.
- Lore companion behavior: story-consistent responses and archive-style narration.
- Intentional unknowns: Aether should say it lacks recovered data instead of pretending to know everything.

## 5) Server/admin coverage
- Config-driven controls through `ai_config.yaml`.
- Prompt libraries split by persona under `ai_fallback/`.
- Lightweight deterministic behavior that does not require external services.
- Optional future local-model sidecar remains configurable, but is disabled by default.
- Async execution only; never block the server tick/world thread for response work.

## 6) Boundaries
- Do not break roleplay by claiming internet/search access.
- Do not expose secrets/tokens/config internals to players.
- Do not produce abusive, discriminatory, or unsafe outputs.
- Do not invent authoritative mechanics if uncertain; use uncertainty plus guidance.
- Do not make the first version depend on a local or hosted LLM.

## 7) Future external AI service
When funding/runtime support exists, the sidecar path can be re-enabled behind config:
- `POST /generate`
  - Input: prompt, player/session context, requested subsystem, optional world summary.
  - Output: response text and optional metadata.
- `GET /health`
  - Liveness/readiness signal for status commands.
- `GET /version`
  - Backend build/model identifier for troubleshooting.

The scripted intent layer should remain as the safe fallback even after an LLM exists.

## 8) Definition of done (MVP)
- A.E.T.H.E.R responds in-world with consistent tone and roleplay behavior.
- Intent matching works across similar player phrasing, not exact phrases only.
- Subsystem routing is available for the six domains.
- Context tags can change responses when the player is in a known location/state.
- Unknown answers feel intentional and in-universe.
- No LLM backend is required for normal play.

## 9) Nice-to-have after MVP
- More discovery/progression context tags.
- Event-triggered hints for new biome, anomaly detected, first night, meteor site, or lore pickup.
- Optional voice I/O integration where available.
- Optional LLM sidecar for richer responses when funding and moderation support exist.

## One-line summary
"A.E.T.H.E.R is a scripted recovered-intent companion made of six sub-systems that provide survival guidance, anomaly awareness, world intelligence, machine/atmosphere support, combat/security help, and lore memory without requiring a full LLM."
