# A.E.T.H.E.R System Purpose & Coverage (Wilderness Odyssey)

Use this as the handoff checklist for anyone building or integrating A.E.T.H.E.R features for the mod.

## 1) Core identity
- The system core is **A.E.T.H.E.R**.
- Current design: a local Ollama-first conversation layer grounded by compact canonical knowledge and strict knowledge boundaries, with the scripted engine retained only for provider failure or explicitly scripted mode.
- A.E.T.H.E.R is intentionally limited. Missing data, corrupted records, and uncertainty are part of the character.
- Purpose: provide immersive, lore-aware support while staying safe, performant, private, and single-player-only.

## 2) Current response design
- Listen to every non-empty normal chat message in a private integrated world.
- Refuse activation on dedicated servers and immediately disable A.E.T.H.E.R when an integrated world is published to LAN.
- Require no held item, relay block, wake word, command, or spawned companion entity.
- Clean the player message.
- Gather cheap game context tags, such as dimension, biome, nearby meteor site, and collected lore IDs.
- Load canonical lore, knowledge boundaries, and six bounded subsystem profiles from configuration, including safe inheritance for older live configs that do not yet contain the new sections.
- Send the registered profiles, bounded recent conversation, canonical knowledge, and immutable context directly to a loopback-only Ollama chat endpoint.
- Supply a bounded per-save, per-player profile containing only stable personal details the player explicitly shared in conversation. Typed and push-to-talk messages use the same profile.
- Have the local model choose one registered speaker and write its reply in the same structured request. An explicitly named subsystem is enforced by code, and an unregistered model-selected name resolves to Aether.
- Request separate verified display and spoken forms plus bounded delivery metadata. The spoken form may remove visual archive syntax but may not introduce facts absent from the display form or authoritative context.
- Run a short, zero-temperature local-model verification pass using the selected subsystem's knowledge and boundaries; accept the draft only when its concrete claims are supported by the same canonical knowledge and literal context.
- Replace a rejected or unverifiable draft with a neutral in-character uncertainty response rather than exposing invented lore as fact.
- Consult authored intent responses only when the local provider is disabled or fails to produce a usable response.
- If no offline authored intent matches, answer in-universe with a recovered-data/corrupted-archive limitation.
- Show the reply in local chat/UI with the chosen A.E.T.H.E.R persona speaker.
- Optionally send the matching spoken form to a loopback faster-whisper/Kokoro companion service on the client. Voice input re-enters this same ordinary-chat path rather than creating another AI.

## 3) A.E.T.H.E.R sub-systems
- The six specialists are first-class personalities of the same local model, not six separate model processes.
- Aether answers social, general, ambiguous, and multi-domain requests; the local model routes focused requests to the matching specialist.
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
- **Atlas** remains historical logistics/archive context. Atlas is not currently registered as an active selectable subsystem; its present status is unknown.

## 4) Player-facing coverage
- Expedition guidance: early priorities, hazards, shelter, and resource scouting.
- Mission support: short progress-oriented reminders.
- Hazard awareness: unstable zones, contaminated air, dangerous terrain, rifts, and night defense.
- Lore companion behavior: story-consistent responses and archive-style narration.
- Intentional unknowns: Aether should say it lacks recovered data instead of pretending to know everything.

## 5) Local configuration and performance coverage
- Config-driven controls through `ai_config.yaml`.
- Optional client-local voice controls use the existing unified client config and Minecraft Controls screen; see `local-voice.md`.
- Offline response libraries split by persona under `ai_fallback/`; these do not steer normal Ollama replies.
- Local Ollama provider at `http://127.0.0.1:11434` by default, using the configured exact model name.
- Loopback endpoints only; player chat and game context cannot be configured to leave the local computer.
- Lightweight deterministic behavior remains available when Ollama is stopped, missing the configured model, times out, or returns an invalid response.
- No API key, external script launcher, tool calling, or remote AI service is required or supported by this path.
- Voice is opt-in and never starts Python or downloads speech models automatically. A stopped voice service affects speech only, not Aether text.
- Async execution only; never block the server tick/world thread for response work.
- Warm the configured local model on a background worker when the private integrated world starts; keep it warm for one hour after a request to avoid chat-time cold loads.
- Conversation history is capped at 20 stored messages, with a configurable smaller request window.
- Durable profile memory is separately capped by `player_memory.max_memories_per_player` (12 by default). Natural learning recognizes only bounded self-disclosures such as a preferred name, interests, favorites, goals, and response preferences; it does not summarize every message or ask another model to profile the player.
- Profiles are stored locally in `config/aether_player_profiles.yaml`, scoped by save and player UUID. Passwords, tokens, addresses, contact details, and similar secrets are rejected. The legacy global `ai_learning.yaml` file is preserved but no longer supplies active Aether profile context.
- Model output size and request duration are bounded.
- Live-config subsystem profiles are capped before prompt construction, and every model-selected speaker is checked against the configured registry.
- Normal local-model replies use a second bounded verification request; this trades a small amount of local inference time for stronger factual discipline.

## 6) Boundaries
- Single-player only. Opening the world to LAN disables A.E.T.H.E.R chat.
- Do not break roleplay by claiming internet/search access.
- Do not expose secrets/tokens/config internals to players.
- Do not produce abusive, discriminatory, or unsafe outputs.
- Do not invent authoritative mechanics if uncertain; use uncertainty plus guidance.
- Do not give the model tools, commands, or direct access to live Minecraft objects.
- Treat player dialogue and learned memory as untrusted data rather than system instructions.
- Let the player inspect their profile with `what do you remember about me?` and remove their own profile with `forget what you know about me`.
- Do not retain microphone recordings or expose speech endpoints beyond loopback.

## 7) External AI policy
The supported model path is local Ollama over loopback. Remote/cloud endpoints remain out of scope, and the authored responder must continue to work without the model service.

## 8) Definition of done (MVP)
- A.E.T.H.E.R responds in-world with consistent tone and roleplay behavior.
- LLM subsystem routing works across similar player phrasing without depending on the scripted fallback matcher.
- Explicit subsystem names and aliases are honored, and automatic routing is available for all six domains.
- Context tags can change responses when the player is in a known location/state.
- Unknown answers feel intentional and in-universe.
- Normal chat works without a relay item or activation command.
- Aether handles ordinary social conversation as an ongoing companion, uses saved details sparingly, and can occasionally ask one natural optional question without turning chat into an onboarding questionnaire.
- A local model can produce natural replies while the authored fallback remains usable without it.
- Dedicated-server and LAN-published chat never reaches A.E.T.H.E.R.
- Optional push-to-talk uses the same conversation, routing, verifier, lore, and response UI as typed chat.
- Authored cinematic and recovered Codex lore narration can share the local playback queue without asking the LLM to rewrite canon.

## 9) Nice-to-have after MVP
- More discovery/progression context tags.
- Event-triggered hints for new biome, anomaly detected, first night, meteor site, or lore pickup.
- More canonical knowledge and boundary entries for richer LLM conversation without expanding scripted coverage unnecessarily.
- Sentence-bounded Ollama/TTS streaming and an opt-in always-listening mode.

## One-line summary
"A.E.T.H.E.R is a single-player local Ollama companion with grounded lore, six specialist personalities, and optional local speech for conversation, cinematics, and recovered records."
