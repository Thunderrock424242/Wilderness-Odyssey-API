# Scripted cinematic sequences

Wilderness Odyssey cinematics are split into server authority and client presentation. `CinematicManager` owns one active session per player, advances immutable stage definitions, applies temporary server safety, cues exact world actors, and sends only start, stage-transition, and end payloads. Client presentations interpolate camera and overlay effects locally from synchronized game time.

## Adding a sequence

1. Implement `CinematicSequence` with a stable id and ordered `CinematicStage` list.
2. Register the immutable definition in `CinematicSequences.bootstrap()`.
3. Implement `ClientCinematicPresentation` for camera and overlay behavior, then register it in `ClientCinematicPresentationRegistry`.
4. Use `CinematicActor` when a block entity or other exact world object must translate sequence cues into feature-owned animation states.
5. Emit only registered, authored narration ids through `CinematicSequenceContext.narrateOnce`. The client presentation resolves those ids to translated text; arbitrary server or model-authored speech is not accepted by this channel.
6. Start playback through `CinematicManager.play(player, sequence, options)`. Automatic story playback uses `CinematicPlaybackOptions.automatic`; development replay uses `developerReplay` and does not alter permanent completion.

Stage ids are the network contract between a server definition and its client presentation. Changing those ids requires a network-version change. Durations remain server authoritative, while smooth per-frame values are derived from stage start time and duration without per-tick packets.

## Cryo wake-up lifecycle

The cryo origin is private single-player story content. The spawn handler leaves dedicated-server and LAN-published players on ordinary spawn behavior. `CinematicManager` independently rejects manual or automatic playback outside an unpublished integrated world and stops an active session if the owner publishes the world to LAN. A.E.T.H.E.R chat and voice use the same private-single-player policy.

In a private integrated world, the existing spawn handler starts `wildernessodysseyapi:cryo_wakeup` only after a new player has been successfully assigned to a real cryo tube. The first automatic request records `automatic_started`; normal completion records the sequence id in `completed`. An interrupted started-but-incomplete intro is eligible for replay on the next login. Existing players who already had a cryo assignment before this feature are not enrolled automatically.

The normal timeline lasts 2,310 ticks (115.5 seconds). The first 90.5 seconds remain locked; the final 25 seconds return movement for the briefing. The longer timing preserves the neural caretaker performance instead of accelerating or interrupting it:

| Start | Stage | Presentation and authority |
| ---: | --- | --- |
| 0 | Black screen | Controls locked; tube occupant established |
| 20 | Exterior reveal | Detached camera reveals the local-player proxy floating in the tube |
| 190 | Medical diagnostic | Patient acquisition, contamination, and filtration status |
| 560 | Revival protocol | Rewarming, cryoprotectant washout, and vasopressor response |
| 950 | Cardiac pacing | Caretaker countdown aligns with the electrical pacing jolt |
| 1,130 | Suspension drain | Circulation report, contaminated-fluid drain, and mist burst |
| 1,350 | Black transition | Exterior view cuts to black before first person |
| 1,370 | Eyes reopening | Lower occupant-relative camera, eyelids, strong blur, and consciousness report |
| 1,500 | Mask release | Breathing-mask release cue and mechanical sound |
| 1,630 | Tube opening | Opening machinery cue while blur and mechanical shake ease |
| 1,690 | Balance check | Camera levels and A.E.T.H.E.R calmly tells the player to remain still |
| 1,810 | Recovery walk | Controls return for a twenty-five-second, movement-aware briefing |

Completion shows the temporary objective text, “Find a way out of the facility.” There is currently no separate quest/objective owner in the project, so this message is not persisted as quest state.

The server lock uses zero velocity plus temporary no-gravity. It corrects position only if the player actually drifts from the exact pod anchor; there is no unconditional teleport loop. Attacks and interactions are rejected server-side. The client uses an inert input object, cancels gameplay interaction key mappings and container screens, keeps first-person render semantics, and hides the normal HUD. At tick 1,810 the server moves the real player once to the tube-facing exit, returns ordinary input, applies a short slowness interval plus the existing cryo-shakes recovery effect, and lets the last A.E.T.H.E.R lines respond to distance walked with bounded timeouts.

Every completion, manual cancellation, death, logout, dimension change, LAN publication, invalid state, exception, and server shutdown removes the active session and restores the original no-gravity state. Client completion, logout, and level unload restore the exact previous input object, GUI visibility preference, camera perspective, queued cinematic speech, and temporary subtitles from one cleanup path.

## Cryo camera, occupant, and original model ownership

The original Blockbench-authored `models/block/cryo_tube.json` and its original item model remain the rendering source of truth. The cinematic does not replace that geometry with a simplified GeckoLib model. `CryoTubeBlockEntity` may retain synchronized high-level cue state, but the static tube geometry is unchanged and those cues do not claim model-part animation.

An additive block-entity renderer draws only presentation-time content around the original tube: a softly lit and gently floating copy of the local player, a translucent assisted-breathing mask, bounded life-support routing, suspension volume, deterministic microbubbles, and a stage-colored diagnostic sweep. Minecraft still renders the original baked cryo model normally. The proxy never creates a second entity and never changes server gameplay authority. Strict single-player scope makes the tube anchor sufficient to identify the occupant. The real player remains at the server-owned safe position until control return.

The detached camera position is anchored to the tube and follows its facing. The later first-person position is a separate lower eye anchor inside the chamber, which avoids inheriting the standing player's eye height that previously placed the view near the top of the model. NeoForge exposes camera-angle events but no world-position event in this version, so one narrow client-only `Camera.setup` tail injection applies a position only while an active presentation supplies it.

Blur uses Minecraft's native transient blur pass and respects the screen-effect accessibility scale. Eyelids, contaminated-fluid tint, diagnostic scan, warning wash, condensation, pacing flash, subtitles, FOV, shake, and blur are presentation-only; they do not mutate player rotation or facility blocks. An A.E.T.H.E.R medical panel shows a deliberately fictional but internally consistent recovery trend for estimated core temperature, heart rate, oxygen saturation, cardiac waveform, and current protocol state. These display values are cinematic telemetry, not a general medical simulation.

## A.E.T.H.E.R narration

All medical and recovery lines are authored cue ids. They do not call Ollama and cannot invent a procedure, observation, or capability. The cryo awakening packages fixed offline 24 kHz PCM WAV clips generated by Kokoro-82M with the subdued `af_nicole` voice at 0.95 speed. The writing and punctuation direct a restrained human-caretaker performance: reassurance, understated concern, deliberate pauses, and no radio distortion. This is an original A.E.T.H.E.R direction informed by broad caretaker-fiction qualities, not an imitation of another game's actor. The client preloads the bounded assets during the black-screen lead-in and plays them through the existing A.E.T.H.E.R JavaSound owner, bypassing live synthesis latency and Windows Narrator. Subtitle durations are the measured clip lengths plus a short fade tail, and the stage cue sheet has a regression test that rejects overlaps. Both text and audio start from the same client cue callback and cleanup stops the active clip. Set `aether_voice.cinematicNarration` to false in the client config to silence authored cinematic speech while retaining subtitles. The optional local Kokoro service now also defaults ordinary A.E.T.H.E.R conversation to `af_nicole`; radio processing defaults off.

The checked-in generators are `tools/aether_voice_service/generate_cryo_voice.ps1` and `generate_cryo_voice.py`. They read the authoritative English translation strings, use the separately cached Apache-licensed Kokoro model, regenerate only the cryo voice directory, apply restrained click-free mastering without a synthetic filter, and write a measured manifest. The first deliberate authoring run uses `-AllowModelDownloads`; later runs are cache-only. Regenerating clips after text changes is deliberate source authoring and is never performed at game runtime. The packaged mod requires neither Python nor the model cache.

The machinery, pacing, drain, mask, and opening stages layer sparse vanilla sound events at exact cue ticks: monitor chirps, pump and valve movement, fluid circulation, low cardiac pulses, the pacing discharge, breathing, and the chamber mechanism. There is no continuous per-tick sound loop. Authored voice uses packaged WAVs through the shared bounded player; machinery can still be replaced later with professionally produced Wilderness Odyssey effects without changing sequence timing. Mist uses one bounded vanilla cloud-particle burst during drain.

## Developer commands

Permission level 2 is required:

```text
/wo sequence play cryo_wakeup
/wo sequence play wildernessodysseyapi:cryo_wakeup
/wo sequence stop
```

The play command finds the nearest compatible cryo tube within three horizontal blocks and two vertical blocks. It is rejected on dedicated servers and as soon as an integrated world is published to LAN. Developer replay never changes permanent completion state. The stop command executes the same authoritative cleanup used by abnormal lifecycle exits.

## In-game verification

Use a development client and verify both automatic first login and the developer command. During the first 90.5 seconds, attempt walking, jumping, sprinting, attacking, mining, using the tube, opening inventory, and changing perspective. Confirm none disrupt the sequence; chat and the pause menu intentionally remain available. Confirm the original cryo-tube shape and UV layout are unchanged and the opening exterior view sees the softly lit local-player proxy, breathing mask, suspension volume, bubbles, diagnostic sweep, and life-support routing inside it rather than the tube ceiling; the black cut then enters the lower first-person view. Check that every subtitle begins with its matching subdued caretaker clip, no line overlaps the next, the pacing flash lands on the spoken final count, the contaminated-fluid screen tint clears, blur fades, and controls return for the walking briefing. The restored static model is not expected to animate its door, fluid, or conduits; those cinematic details are temporary renderer layers.

Disconnect, die, change dimension through an external command, use `/wo sequence stop`, and publish to LAN during separate replays. Confirm normal movement, gravity, HUD visibility, perspective, attacks, interaction, blur, and speech cleanup all return. On a dedicated server and LAN-published integrated world, confirm automatic cryo assignment is skipped and the developer play command is rejected.
