# AI System Purpose & Coverage (Wilderness Odyssey)

Use this as the handoff checklist for anyone building or integrating AI features for the mod.

## 1) Core purpose (what AI is for)
- Provide an **immersive in-world companion** (Atlas) that helps players survive and explore.
- Keep responses **lore-aware** and roleplay-consistent with the post-apocalyptic setting.
- Improve player onboarding and reduce confusion without breaking immersion.

## 2) Player-facing coverage
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
  - Deliver story-consistent flavor responses and archive-style narration.
  - Surface corrupted/archive fragments for atmosphere.
- **Onboarding flow**
  - Guided choices (mission focus, communication style, initial briefings).
  - Clear completion message once setup is done.

## 3) Server/admin coverage
- **Config-driven controls**
  - Enable/disable AI features without code changes.
  - Model/backend endpoint config (local sidecar or hosted service).
- **Reliability behavior**
  - Timeouts, retries, and backoff for AI calls.
  - Graceful “backend unavailable” fallback messaging.
- **Performance safeguards**
  - Async execution only; never block server tick/world thread.
  - Queue limits/rate limiting to avoid lag spikes.
- **Operational visibility**
  - Health/probe commands and logs for debugging AI backend status.

## 4) Content/safety boundaries (what AI should NOT do)
- Do not break roleplay by claiming internet/search access if none exists.
- Do not expose secrets/tokens/config internals to players.
- Do not provide abusive, discriminatory, or unsafe outputs.
- Do not invent authoritative mechanics if uncertain; prefer uncertainty + guidance.
- Do not block or crash gameplay if AI backend fails.

## 5) Suggested API contract for external AI service
- `POST /generate`
  - Input: prompt, player/session context, tone/persona settings, optional world state summary.
  - Output: response text + optional metadata (latency, safety flags).
- `GET /health`
  - Liveness/readiness signal for status commands and startup checks.
- `GET /version`
  - Backend build/model identifier for troubleshooting.

## 6) Definition of done (MVP)
- Atlas responds in-world with consistent tone/persona.
- Onboarding steps complete successfully with selectable options.
- Backend failures return a friendly fallback message in-game.
- AI requests are async and do not degrade TPS under normal load.
- Admin can verify backend status from server commands/logs.

## 7) Nice-to-have after MVP
- Memory summaries per player/session (bounded and privacy-safe).
- Event-triggered hints (new biome, anomaly detected, first night, etc.).
- Optional voice I/O integration where available.
- Tool-calling for safe read-only game context queries.

---

## One-line summary for your dev friend
"Atlas is an immersive expedition assistant for Wilderness Odyssey: it guides survival and mission flow, stays lore-consistent, runs through an external AI backend, and is built to fail gracefully without impacting server performance."
