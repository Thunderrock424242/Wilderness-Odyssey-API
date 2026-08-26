# Scripted cinematic sequences

Wilderness Odyssey cinematics are split into server authority and client presentation. `CinematicManager` owns one active session per player, advances immutable stage definitions, applies temporary server safety, cues exact world actors, and sends only start, stage-transition, and end payloads. Client presentations interpolate camera and overlay effects locally from synchronized game time.

## Adding a sequence

1. Implement `CinematicSequence` with a stable id and ordered `CinematicStage` list.
2. Register the immutable definition in `CinematicSequences.bootstrap()`.
3. Implement `ClientCinematicPresentation` for camera and overlay behavior, then register it in `ClientCinematicPresentationRegistry`.
4. Use `CinematicActor` when a block entity or other exact world object must translate sequence cues into feature-owned animation states.
5. Start playback through `CinematicManager.play(player, sequence, options)`. Automatic story playback uses `CinematicPlaybackOptions.automatic`; development replay uses `developerReplay` and does not alter permanent completion.

Stage ids are the network contract between a server definition and its client presentation. Changing those ids requires a network-version change. Durations remain server authoritative, while smooth per-frame values are derived from stage start time and duration without per-tick packets.

## Cryo wake-up lifecycle

The existing cryo spawn handler starts `wildernessodysseyapi:cryo_wakeup` only after a new player has been successfully assigned and positioned in a real cryo tube. The first automatic request records `automatic_started`; normal completion records the sequence id in `completed`. An interrupted started-but-incomplete intro is eligible for replay on the next login. Existing players who already had a cryo assignment before this feature are not enrolled automatically.

The normal timeline lasts 480 ticks (24 seconds). Movement and interactions are locked through tick 459, then returned for the final one-second presentation stage. Completion shows the temporary objective text, “Find a way out of the facility.” There is currently no separate quest/objective owner in the project, so this message is not persisted as quest state.

The server lock uses zero velocity plus temporary no-gravity. It corrects position only if the player actually drifts from the exact pod anchor; there is no unconditional teleport loop. Attacks and interactions are rejected server-side. The client uses an inert input object, cancels gameplay interaction key mappings and container screens, keeps first-person perspective, and hides the normal HUD.

Every completion, manual cancellation, death, logout, dimension change, invalid state, exception, and server shutdown removes the active session and restores the original no-gravity state. Client completion, logout, and level unload restore the exact previous input object, GUI visibility preference, and camera perspective from one cleanup path. Cryo actors are exclusive while active so two players cannot drive the same pod animation concurrently.

## Cryo assets and temporary presentation

`CryoTubeBlockEntity` synchronizes `IDLE`, `WARNING`, `UNLOCK`, `OPENING`, and `OPEN`. The current tube remains a static JSON model; a later Blockbench model, GeckoLib `GeoBlockEntity`, renderer, controller, geometry, texture, and animation JSON can consume `getAnimationState()` without changing cinematic timing or networking.

The machinery, heartbeat, relay, alarm, release, lock, and opening cues currently reuse sparse vanilla sound events. No fake `.ogg` files or nonexistent custom sound registrations are included. Replace those event choices with registered Wilderness Odyssey sound events after real audio assets are added. Mist temporarily uses 18 vanilla cloud particles once at release.

Warning red light and early electrical flicker are currently client overlay presentation plus the pod's synchronized `WARNING` cue. The sequence does not rewrite facility blocks or invent an unrelated door/light controller. A future lab lighting or door owner can consume a cinematic actor cue at the relevant stage.

## Developer commands

Permission level 2 is required:

```text
/wo sequence play cryo_wakeup
/wo sequence play wildernessodysseyapi:cryo_wakeup
/wo sequence stop
```

The play command finds the nearest compatible cryo tube within three horizontal blocks and two vertical blocks. Developer replay never changes permanent completion state. The stop command executes the same authoritative cleanup used by abnormal lifecycle exits.

## In-game verification

Use a development client and verify both automatic first login and the developer command. During the first 23 seconds, attempt walking, jumping, sprinting, attacking, mining, using the tube, opening inventory, and changing perspective. Confirm none disrupt the sequence; chat and the pause menu intentionally remain available. Disconnect, die, change dimension through an external command, and use `/wo sequence stop` in separate replays, then confirm normal movement, gravity, HUD visibility, perspective, attacks, and interaction all return. A two-player check should also confirm independent overlays and rejection when both players attempt to use the same pod.
