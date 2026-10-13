# Water sound ownership

Coastal ambience, precipitation, and entity entry use separate sound paths.

## Entity entry

An impact splash requires a dry fall of at least three blocks. Fish are excluded.
The native entity water-entry hook owns emission; the mod gates only its sound
call, before Minecraft resets fall distance. Native water-contact particles,
ripples, swimming, and splash game events retain their existing behavior.

The server water setting `minimumSplashFallDistanceBlocks` defaults to 3 and
allows a higher threshold up to 64 blocks. The synchronized server setting is
also used by the local player's native sound path. The
`wildernessodysseyapi:quiet_water_entry` entity-type tag can extend exclusions
for modded fish; vanilla fish are excluded by their shared fish class too.

The gate reads only entity metadata, tags, and synchronized configuration. It
does not sample water, access chunks, acquire tickets, or create a second sound
emitter. This preserves the generation-thread safety repair.

## Coastal ambience

The four stable coastal events now use original mono surf textures. They no
longer alias Minecraft's generic or high-speed entity splash events. Existing
wave-cycle selection, distance limits, volume controls, and effect budgets
remain in `CoastalBreakEffects`.

## Rain on water

Localized precipitation selects `rain_on_water` when an exposed rain sample
lands on water-tagged fluid. Rain over land retains the native rain ambience.
Water droplets use the weather volume channel and a 16-block sound range;
covered listeners receive a quieter, lower-pitched version. Sound selection
also works with minimal particles and never emits an entity impact splash.

The renderer reuses its existing bounded sampled columns and sound cadence.
It does not scan additional chunks, create rain entities, or play a sound for
every visual droplet. The rain-water event has an English subtitle.

## Assets and validation

`tools/audio/generate_water_ambience.py` authors the assets deterministically
with NumPy and FFmpeg. The sounds are synthesized foley: shaped water noise for
surf and short damped droplet resonances for rain. They contain no recordings
or third-party/Minecraft samples. Asset authoring is offline; game runtime uses
the normal resource-pack sound loader.

The eleven checked-in Ogg files are 48 kHz mono Vorbis. Mono preserves normal
positional attenuation. Rain has three short alternatives, and each coastal
event has two alternatives. The authoring tool fades both ends and limits peak
amplitude before encoding.

The resource regressions require independent playable files. The entry
GameTests exercise native contact for shallow entry, the inclusive three-block
threshold, fish, and a falling item. Repeat these scenarios in a client and
listen near a shoreline and during rain over both water and land. Headless
server tests establish sound-emission decisions, not perceived mix quality.
