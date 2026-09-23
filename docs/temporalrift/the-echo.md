# The Echo Dimension

The Echo is a same-seed copy of the Overworld using the normal Overworld terrain settings and biome preset, so modded Overworld biomes and compatible structures can appear there too. It should feel recognizable first, then wrong.

Core rules:
- Resource key: `wildernessodysseyapi:the_echo`
- Display name: The Echo
- Terrain: Overworld noise and Overworld biome preset
- Time: fixed light with no normal day-night progression
- Raids and beds: disabled
- Storms: eligible for the Riftfall weather system

Atmosphere:
- Villages can generate, but villagers, zombie villagers, and wandering traders are removed.
- Iron golems are allowed.
- Village doors and other generated doors are forced open when Echo chunks load.
- Some leaf clusters are removed deterministically from loaded Echo chunks, making familiar forests look partially dead.
- Client fog and sky colors are darkened; most sound categories are muted in The Echo, leaving player and whitelisted animal sounds.

Player-built echoes:
- When players place or break solid blocks in the Overworld, a sampled subset is recorded.
- After a short delay, those edits can appear in The Echo as offset ruined or scarred blocks.
- This is intentionally partial and distorted, not a perfect base mirror.
