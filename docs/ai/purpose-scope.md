# A.E.T.H.E.R. purpose and ownership

A.E.T.H.E.R. is Wilderness Odyssey's damaged expedition intelligence: a conversational companion grounded in recovered canon, literal game context, and clearly stated uncertainty.

The logical Minecraft server serves addressed chat in private worlds, LAN games and dedicated servers. It sends structured context to an authenticated standalone Aether gateway. The gateway owns permanent prompts, model settings, specialist selection and factual verification, and connects to privately hosted Ollama. See [backend architecture and migration](aether-backend.md) and [deployment](../../deploy/README.md).

## Personalities

- Aether handles central, social and general conversation.
- Aegis handles health and protection.
- Eclipse handles rifts and anomalies.
- Terra handles exploration, terrain and restoration.
- Helios handles energy, machinery and atmosphere.
- Enforcer handles combat and security.
- Requiem handles archives, history and memory.
- Atlas remains historical logistics/archive context; its current status is unknown.

Named speakers are validated. Missing records remain missing, and location tags do not imply sensor readings, danger, safety or unseen activity. The model never receives tools or direct access to Minecraft objects.

## Game-side responsibilities

AIChatListener owns invocation and captures dimension, biome, surface/interface status, lore collection and recognized meteor/discovery context. Existing shared workers perform network work; results return to the server thread and original player. Gameplay state remains server-authoritative.

MemoryStore keeps bounded transient dialogue isolated by save/player. AIPlayerProfileStore preserves local stable profile notes and explicit remember, recall and forget controls. Profile sharing with the backend defaults off; natural profile learning defaults off for new configurations.

AIFallbackResponder and persona-specific ai_fallback resources provide deterministic recovered-intent replies whenever the gateway or model is unavailable, overloaded, unauthorized, too slow or returns an unusable answer.

The mod never launches, installs, extracts or downloads an Ollama runtime/model. Operators start and stop the independent services.

## Voice

Optional local speech keeps the existing manually started Python service, push-to-talk, voice output, subtitles, cinematic narration and lore-reading queue. Voice remains opt-in and private-world-only. Text AI supports server hosting independently of those voice restrictions. See [voice setup](local-voice.md).

## Validation boundaries

Mocked HTTP and unit tests establish transport, queue, authentication, routing, fallback and privacy behavior. Live model quality, Minecraft client/server behavior, multiple-player tick responsiveness, audio hardware and production HTTPS configuration require their own acceptance checks. Build success alone does not establish those results.
