# Paged Wilderness Debug HUD

## Purpose

The paged HUD replaces only the visual text presentation of Minecraft's normal F3 overlay. Minecraft still owns whether debugging is enabled, every vanilla F3 shortcut, debug charts, frame/tick/ping sample loggers, block and fluid ray picks, and the underlying debug values.

The default page is deliberately small and contains only compact World and Target sections. Performance information lives entirely on its dedicated page so General fits at larger GUI scales. Detailed world, rendering, system, network, and target information lives on their dedicated pages, while `VANILLA RAW` preserves the original line collections as an escape hatch.

## Minecraft 1.21.1 interception

NeoForge 21.1.248 patches `net.minecraft.client.gui.components.DebugScreenOverlay.render(GuiGraphics)` to:

1. update its `block` and `liquid` 20-block ray hits;
2. call `collectGameInformationText()` and `collectSystemInformationText()`;
3. post `CustomizeGuiOverlayEvent.DebugText` with the two modifiable line lists;
4. draw those lists;
5. draw the vanilla profiler, FPS/TPS, or network charts when enabled.

`WildernessDebugClientEvents.onDebugText` runs at `EventPriority.LOWEST`. It renders the selected Wilderness page using the lists from step 3 and clears only those two lists after a successful custom render. The rest of `DebugScreenOverlay.render` continues normally, so F3+1/F3+2/F3+3 charts are not cancelled. Running at the lowest priority also lets `VANILLA RAW` retain debug lines contributed by other mods through the same event. Unrelated HUD overlays and GUI rendering are not cancelled.

If the custom renderer throws a runtime exception, the handler does not clear the lists. Vanilla text is therefore the last-resort render fallback for that frame.

## Mixin

`DebugScreenOverlayAccessor` is the only debug-HUD mixin. It exposes the private `DebugScreenOverlay.block` and `DebugScreenOverlay.liquid` fields through two `@Accessor` methods. The target provider reads the ray results vanilla already calculated instead of performing two more ray casts.

There is no render-cancelling or keyboard mixin and no page, formatting, or provider logic in the accessor. The mixin configuration has `defaultRequire: 1`, so a mapping change to either field fails loudly during development.

## Architecture

- `DebugPage` defines a page ID, display name, ordered sections, and an optional availability condition.
- `DebugSection`, `DebugEntry`, and `DebugValue` are the presentation model. `DebugValue` carries semantic normal/good/warning/error/unavailable state without embedding renderer colors in providers.
- `DebugPageRegistry` owns ordered page registration. Its built-ins are General, World, Performance, Rendering, System, Network/Server, Data Engine, Target Details, and Vanilla Raw.
- `ProviderDebugPage` calls only the active page's provider and caches its result for a page-specific interval.
- Provider classes under `debugoverlay/provider` read client-safe Minecraft state. They do not perform disk or network IO.
- `WildernessDebugManager` owns the selected index and active-page scroll offset but never toggles Minecraft's debug state. Changing pages or reopening F3 resets the viewport to the top.
- `WildernessDebugOverlay` draws the responsive panel with `GuiGraphics` and Minecraft's font. It flows visible rows across up to three columns and clamps the requested viewport against the capacity calculated from the current window and GUI scale.
- `DebugViewport` is the tested line-window model used to expose overflow instead of discarding it.
- `DebugPageContributorRegistry` lets optional renderer integrations append sections without hard dependencies. Contributor failures become an unavailable row instead of breaking F3.

The existing Wilderness GPU-profiler, water-render, and localized-weather diagnostic lines moved from the old unbounded vanilla right column to the cached Rendering page. Their information remains available without calculating those subsystem diagnostics on unrelated pages.

Static JVM, OS, CPU, GPU, and OpenGL identity is cached. Slowly changing pages refresh every 250 ms to one second; General, Performance, and Target refresh every 100 ms. The exact vanilla lists are rebuilt by vanilla itself and used directly only while `VANILLA RAW` is active.

## Registering a future page

Create a focused `DebugDataProvider`, wrap it in a `DebugPage` (usually a `ProviderDebugPage` subclass), then register it during client setup:

```java
DebugPageRegistry.register(new WaterDebugPage());
```

The manager, renderer, header count, controls, and wrapping behavior update automatically. Use this for future Water, Weather, Worldgen, Environment, AI/Entities, Wilderness Renderer, and Profiler pages.

If an optional mod only needs to add renderer details, register a section callback instead:

```java
DebugPageContributorRegistry.register(RenderingDebugDataProvider.PAGE_ID, context ->
        List.of(DebugSection.builder("IRIS")
                .add("Shader pack", currentPackName)
                .build()));
```

Do not load optional-mod classes until the integration has verified that the mod is present.

## Controls and vanilla shortcuts

The default controls are:

- Left Arrow: previous page
- Right Arrow: next page
- Up Arrow: scroll the current page up one row
- Down Arrow: scroll the current page down one row

All four arrow keys are registered as normal configurable key mappings. A single press changes the page or viewport only while the F3 debug overlay is visible and no menu, chat, inventory, or other screen is open. F3 does not need to be held. Up and Down have no visible effect when all of the active page's lines already fit.

NeoForge's non-cancellable `InputEvent.Key` reports the press after normal keyboard processing. The handler ignores releases, repeats, hidden debug overlays, and open screens, then recognizes only the configured page and scroll controls. The default arrow keys are not Minecraft movement controls. Players who rebind a debug control to a gameplay key may trigger both actions while the debug overlay is visible.

When a page exceeds the calculated multi-column capacity, the center footer displays the first and last visible line plus the total line count. Each Up or Down press shifts that flattened line window by one row. The offset is clamped again every frame so resizing the window, changing GUI scale, or receiving a different number of live diagnostic rows cannot leave the page beyond its valid content.

## Configuration and compatibility fallback

The client config is `config/wildernessodysseyapi/wildernessodysseyapi-client.toml`.
These settings are grouped under `[debug_hud]`:

- `enableCustomDebugHud`: master switch, default `true`;
- `rememberLastDebugPage`: retain the page across F3 toggles in the current client session, default `true`;
- `showPageHints`: show the footer controls, default `true`;
- `debugHudBackground`: draw the translucent panel, default `true`.

Set `enableCustomDebugHud=false` and reload/restart the client to leave the event lists untouched and restore vanilla F3 text exactly. This is the first compatibility check when another mod also changes `DebugScreenOverlay` text.

## Data boundaries and limitations

- The categorized pages use direct Minecraft fields and APIs. They do not parse vanilla's rendered text.
- `VANILLA RAW` intentionally uses the already-generated strings because reproducing those exact collections is its purpose.
- Client light, sky light, block light, client heightmaps, local difficulty, and client chunk state are available directly.
- A distinct server light value is not synchronized to the client. It is labeled unavailable.
- Remote server MSPT and server chunks are not invented or queried. They remain unavailable unless vanilla has supplied a usable client-side sample or a future optional server integration contributes them.
- Integrated-server MSPT uses the public smoothed tick time. Integrated-server chunk objects are not read from the render thread; detailed vanilla server chunk lines remain available on `VANILLA RAW`.
- Long raw output can exceed a tiny window. The renderer retains every line and exposes the overflow through Up/Down scrolling; the footer shows the current visible range whenever scrolling is needed.

## Manual verification

Run the client with JDK 21:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat runClient --no-daemon --console=plain
```

Verify F3 open/close, Left/Right paging without holding F3, Up/Down scrolling on an overflowing page, F3+1/2/3 charts, vanilla F3 letter shortcuts, target changes across blocks/fluids/entities, Overworld/Nether/End, several GUI scales, small window/fullscreen, disconnect/reconnect, and the disabled-config vanilla fallback. Confirm that scrolling stops cleanly at both ends, resets at page changes and F3 reopen, disappears when resized content fits, and does not react after F3 is closed. Multiplayer values should remain safe when the remote server does not have Wilderness Odyssey installed.
