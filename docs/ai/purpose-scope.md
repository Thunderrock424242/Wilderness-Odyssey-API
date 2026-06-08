# A.E.T.H.E.R AI System Purpose & Coverage (Wilderness Odyssey)

Use this as the handoff checklist for anyone building or integrating AI features for the mod.

## 1) Core identity
- The AI system core is **A.E.T.H.E.R**.
- A.E.T.H.E.R is composed of specialized sub-systems that work together as one intelligence layer.
- Purpose: provide immersive, lore-aware support while staying safe, performant, and server-friendly.

## 2) A.E.T.H.E.R sub-systems (required)
- **Aegis** — Health / Protection
  - Player safety guidance, hazard prevention reminders, defensive readiness.
- **Eclipse** — Rift / Anomaly
  - Detect and explain anomaly/rift events, risk levels, and safe response actions.
- **Terra** — Terrain / World Restoration / Exploration
  - Terrain intelligence, exploration routing, restoration-oriented world insights.
- **Helios** — Energy / Machines / Atmospheric Stability
  - Machine-power recommendations, atmospheric condition awareness, system stability advice.
- **Enforcer** — Combat / Security
  - Combat readiness guidance, threat prioritization, security posture prompts.
- **Requiem** — Archive / Memory / History
  - Lore memory, historical recall, archive-style narrative continuity.

## 3) Player-facing coverage
- **Expedition guidance**
  - Early-game priorities (shelter, hazards, resource scouting).
  - Contextual tips for exploration and survival.
- **Mission support**
  - Interpret mission goals and suggest next objectives.
  - Give progress-oriented reminders (short and actionable).
- **Hazard awareness**
  - Warn about environmental risks (storms, dangerous zones, unstable areas).
  - Suggest defensive preparation before players enter risky regions.
- **Lore companion behavior**
  - Deliver story-consistent responses and archive-style narration.
  - Surface corrupted/archive fragments for atmosphere when appropriate.
- **Onboarding flow**
  - Guided choices (mission focus, communication style, initial briefings).
  - Clear completion message once setup is done.

## 4) Server/admin coverage
- **Config-driven controls**
  - Enable/disable AI features without code changes.
  - Backend endpoint config (local sidecar or hosted service).
- **Reliability behavior**
  - Timeouts, retries, and backoff for AI calls.
  - Graceful backend-unavailable fallback messaging, including deterministic prompt-menu personas for lightweight mode.
- **Performance safeguards**
  - Async execution only; never block server tick/world thread.
  - Queue limits/rate limiting to avoid lag spikes.
- **Operational visibility**
  - Health/probe commands and logs for debugging backend status.

## 5) Content/safety boundaries (what A.E.T.H.E.R should NOT do)
- Do not break roleplay by claiming internet/search access if none exists.
- Do not expose secrets/tokens/config internals to players.
- Do not produce abusive, discriminatory, or unsafe outputs.
- Do not invent authoritative mechanics if uncertain; prefer uncertainty + guidance.
- Do not block or crash gameplay if the backend fails.

## 6) Suggested API contract for external AI service
- `POST /generate`
  - Input: prompt, player/session context, requested subsystem (Aegis/Eclipse/Terra/Helios/Enforcer/Requiem), optional world summary.
  - Output: response text + optional metadata (latency, safety flags, subsystem used).
- `GET /health`
  - Liveness/readiness signal for status commands and startup checks.
- `GET /version`
  - Backend build/model identifier for troubleshooting.

## 7) Definition of done (MVP)
- A.E.T.H.E.R responds in-world with consistent tone and roleplay behavior.
- Subsystem routing is available (explicit or automatic) for the six domains.
- Onboarding steps complete successfully with selectable options.
- Backend failures return a friendly fallback message in-game, with optional named persona prompt menus.
- AI requests are async and do not degrade TPS under normal load.
- Admin can verify backend status from commands/logs.

## 8) Nice-to-have after MVP
- Memory summaries per player/session (bounded and privacy-safe).
- Event-triggered hints (new biome, anomaly detected, first night, etc.).
- Optional voice I/O integration where available.
- Safe tool-calling for read-only game context queries.

---

## One-line summary for your dev friend
"A.E.T.H.E.R is the core AI layer made of six sub-systems (Aegis, Eclipse, Terra, Helios, Enforcer, Requiem) that together provide survival guidance, anomaly awareness, world intelligence, machine/atmosphere support, combat/security help, and lore memory—without impacting server performance."
