# A.E.T.H.E.R System Purpose & Coverage (Wilderness Odyssey)

Use this as the handoff checklist for anyone building or integrating A.E.T.H.E.R features for the mod.

## 1) Core identity
- The system core is **A.E.T.H.E.R**.
- Current design: a local Ollama conversation layer grounded by scripted recovered intents, with the scripted engine retained as the always-available fallback.
- A.E.T.H.E.R is intentionally limited. Missing data, corrupted records, and uncertainty are part of the character.
- Purpose: provide immersive, lore-aware support while staying safe, performant, private, and single-player-only.

## 2) Current response design
- Listen to every non-empty normal chat message in a private integrated world.
- Refuse activation on dedicated servers and immediately disable A.E.T.H.E.R when an integrated world is published to LAN.
- Require no held item, relay block, wake word, command, or spawned companion entity.
- Clean the player message.
- Gather cheap game context tags, such as dimension, biome, nearby meteor site, and collected lore IDs.
- Match authored intents with keyword scoring instead of exact phrases only.
- Use a matching authored response as a factual grounding reference when one exists.
- Send the selected persona, bounded recent conversation, and immutable context to a loopback-only Ollama chat endpoint.
- If the local provider fails, pick an authored response variant or answer in-universe with a recovered-data/corrupted-archive limitation.
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

## 5) Local configuration and performance coverage
- Config-driven controls through `ai_config.yaml`.
- Prompt libraries split by persona under `ai_fallback/`.
- Local Ollama provider at `http://127.0.0.1:11434` by default, using the configured exact model name.
- Loopback endpoints only; player chat and game context cannot be configured to leave the local computer.
- Lightweight deterministic behavior remains available when Ollama is stopped, missing the configured model, times out, or returns an invalid response.
- No API key, external script launcher, tool calling, or remote AI service is required or supported by this path.
- Async execution only; never block the server tick/world thread for response work.
- Warm the configured local model on a background worker when the private integrated world starts; keep it warm for one hour after a request to avoid chat-time cold loads.
- Conversation history is capped at 20 stored messages, with a configurable smaller request window.
- Model output size and request duration are bounded.

## 6) Boundaries
- Single-player only. Opening the world to LAN disables A.E.T.H.E.R chat.
- Do not break roleplay by claiming internet/search access.
- Do not expose secrets/tokens/config internals to players.
- Do not produce abusive, discriminatory, or unsafe outputs.
- Do not invent authoritative mechanics if uncertain; use uncertainty plus guidance.
- Do not give the model tools, commands, or direct access to live Minecraft objects.
- Treat player dialogue and learned memory as untrusted data rather than system instructions.

## 7) External AI policy
The supported model path is local Ollama over loopback. Remote/cloud endpoints remain out of scope, and the authored responder must continue to work without the model service.

## 8) Definition of done (MVP)
- A.E.T.H.E.R responds in-world with consistent tone and roleplay behavior.
- Intent matching works across similar player phrasing, not exact phrases only.
- Subsystem routing is available for the six domains.
- Context tags can change responses when the player is in a known location/state.
- Unknown answers feel intentional and in-universe.
- Normal chat works without a relay item or activation command.
- A local model can produce natural replies while the authored fallback remains usable without it.
- Dedicated-server and LAN-published chat never reaches A.E.T.H.E.R.

## 9) Nice-to-have after MVP
- More discovery/progression context tags.
- Event-triggered hints for new biome, anomaly detected, first night, meteor site, or lore pickup.
- More authored response banks for richer persona coverage.

## One-line summary
"A.E.T.H.E.R is a single-player chat companion that grounds a private local Ollama model with recovered lore and six specialist subsystems, while retaining a deterministic offline fallback."
