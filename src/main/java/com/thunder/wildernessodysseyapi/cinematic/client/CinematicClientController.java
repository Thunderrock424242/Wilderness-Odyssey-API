package com.thunder.wildernessodysseyapi.cinematic.client;

import com.thunder.wildernessodysseyapi.cinematic.network.CinematicStagePayload;
import com.thunder.wildernessodysseyapi.cinematic.network.CinematicNarrationPayload;
import com.thunder.wildernessodysseyapi.cinematic.network.EndCinematicPayload;
import com.thunder.wildernessodysseyapi.cinematic.network.StartCinematicPayload;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.UUID;

/**
 * Client presentation state and reversible input ownership for one cinematic.
 *
 * <p>The controller substitutes an inert {@link Input} only while the
 * authoritative stage is locked. It restores the exact previous input object,
 * perspective, and HUD preference from one shared cleanup path.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class CinematicClientController {
    private static final CinematicClientController INSTANCE = new CinematicClientController();
    private static final Input LOCKED_INPUT = new Input();

    private ResourceLocation sequenceId;
    private ResourceLocation stageId;
    private ClientCinematicPresentation presentation;
    private long stageStartGameTime;
    private int stageDurationTicks;
    private boolean controlsLocked;
    private boolean hideHud;
    private BlockPos anchor;
    private float baseYaw;
    private float basePitch;

    private UUID playerId;
    private Input previousInput;
    private CameraType previousCameraType;
    private boolean previousHideGui;
    private boolean clientSettingsCaptured;

    private Component postMessage;
    private int postMessageTicks;
    private int postMessageTotalTicks;
    private Component subtitle;
    private int subtitleTicks;
    private int subtitleTotalTicks;

    private CinematicClientController() {
    }

    public static CinematicClientController get() {
        return INSTANCE;
    }

    /** Accepts a server start snapshot after the network handler reaches the client thread. */
    public static void accept(StartCinematicPayload payload) {
        INSTANCE.start(payload);
    }

    /** Accepts one sparse server-authored stage boundary. */
    public static void accept(CinematicStagePayload payload) {
        INSTANCE.changeStage(payload);
    }

    /** Accepts one validated, authored narration cue for the active presentation. */
    public static void accept(CinematicNarrationPayload payload) {
        INSTANCE.narrate(payload);
    }

    /** Ends presentation and restores all client-owned temporary state. */
    public static void accept(EndCinematicPayload payload) {
        INSTANCE.end(payload);
    }

    public boolean isActive() {
        return sequenceId != null;
    }

    public boolean controlsLocked() {
        return isActive() && controlsLocked;
    }

    public ResourceLocation sequenceId() {
        return sequenceId;
    }

    public ResourceLocation stageId() {
        return stageId;
    }

    public ClientCinematicPresentation presentation() {
        return presentation;
    }

    public float baseYaw() {
        return baseYaw;
    }

    public float basePitch() {
        return basePitch;
    }

    public BlockPos anchor() {
        return anchor;
    }

    /** Returns an optional world-space camera position supplied by the presentation. */
    public java.util.Optional<Vec3> cameraPosition(float partialTick) {
        return presentation == null ? java.util.Optional.empty() : presentation.cameraPosition(this, partialTick);
    }

    public Component subtitle() {
        return subtitle;
    }

    public float subtitleAlpha(float partialTick) {
        if (subtitle == null || subtitleTotalTicks <= 0) {
            return 0.0F;
        }
        float remaining = Math.max(0.0F, subtitleTicks - partialTick);
        float fadeIn = Math.min(1.0F, (subtitleTotalTicks - remaining) / 5.0F);
        float fadeOut = Math.min(1.0F, remaining / 10.0F);
        return Math.min(fadeIn, fadeOut);
    }

    /** Returns smooth stage progress derived from synchronized level time, without tick packets. */
    public float stageProgress(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActive() || minecraft.level == null || stageDurationTicks <= 0) {
            return 0.0F;
        }
        double elapsed = minecraft.level.getGameTime() - stageStartGameTime + partialTick;
        return Mth.clamp((float) (elapsed / stageDurationTicks), 0.0F, 1.0F);
    }

    public Component postMessage() {
        return postMessage;
    }

    public float postMessageAlpha(float partialTick) {
        if (postMessage == null || postMessageTotalTicks <= 0) {
            return 0.0F;
        }
        float remaining = Math.max(0.0F, postMessageTicks - partialTick);
        float fadeIn = Math.min(1.0F, (postMessageTotalTicks - remaining) / 10.0F);
        float fadeOut = Math.min(1.0F, remaining / 20.0F);
        return Math.min(fadeIn, fadeOut);
    }

    private void start(StartCinematicPayload payload) {
        reset(true);
        ClientCinematicPresentation presentation = ClientCinematicPresentationRegistry.get(payload.sequenceId())
                .orElse(null);
        if (presentation == null || !presentation.recognizesStage(payload.stageId())) {
            ModConstants.LOGGER.error("Ignoring unknown client cinematic stage {} for {}",
                    payload.stageId(), payload.sequenceId());
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        sequenceId = payload.sequenceId();
        stageId = payload.stageId();
        this.presentation = presentation;
        stageStartGameTime = payload.stageStartGameTime();
        stageDurationTicks = payload.stageDurationTicks();
        controlsLocked = payload.controlsLocked();
        hideHud = payload.hideHud();
        anchor = payload.anchor();
        baseYaw = payload.baseYaw();
        basePitch = payload.basePitch();

        playerId = player.getUUID();
        previousInput = player.input;
        previousCameraType = minecraft.options.getCameraType();
        previousHideGui = minecraft.options.hideGui;
        clientSettingsCaptured = true;
        enforceClientState(minecraft, player);
        invokePresentation(() -> presentation.onStarted(this), "start");
    }

    private void changeStage(CinematicStagePayload payload) {
        if (!isActive() || !payload.sequenceId().equals(sequenceId)) {
            return;
        }
        if (presentation == null || !presentation.recognizesStage(payload.stageId())) {
            ModConstants.LOGGER.error("Ending cinematic {} after unknown client stage {}",
                    sequenceId, payload.stageId());
            reset(false);
            return;
        }

        stageId = payload.stageId();
        stageStartGameTime = payload.stageStartGameTime();
        stageDurationTicks = payload.stageDurationTicks();
        controlsLocked = payload.controlsLocked();
        hideHud = payload.hideHud();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            enforceClientState(minecraft, minecraft.player);
        }
        invokePresentation(() -> presentation.onStageChanged(this), "stage change");
    }

    private void narrate(CinematicNarrationPayload payload) {
        if (!isActive() || !payload.sequenceId().equals(sequenceId) || presentation == null) {
            return;
        }
        Component text = presentation.narration(payload.cueId()).orElse(null);
        if (text == null) {
            ModConstants.LOGGER.warn("Ignoring unknown narration cue {} for {}", payload.cueId(), sequenceId);
            return;
        }
        subtitle = text;
        subtitleTicks = payload.durationTicks();
        subtitleTotalTicks = payload.durationTicks();
        invokePresentation(() -> presentation.onNarration(this, payload.cueId(), text), "narration");
    }

    private void end(EndCinematicPayload payload) {
        if (!isActive() || !payload.sequenceId().equals(sequenceId)) {
            return;
        }
        Component completionMessage = payload.completedNormally() && presentation != null
                ? presentation.completionMessage()
                : null;
        int completionTicks = presentation == null ? 0 : presentation.completionMessageTicks();
        reset(false);
        if (completionMessage != null && completionTicks > 0) {
            postMessage = completionMessage;
            postMessageTicks = completionTicks;
            postMessageTotalTicks = completionTicks;
        }
    }

    private void enforceClientState(Minecraft minecraft, LocalPlayer player) {
        if (!isActive() || !player.getUUID().equals(playerId)) {
            reset(false);
            return;
        }
        minecraft.options.hideGui = hideHud || previousHideGui;
        if (presentation != null && presentation.forcesFirstPerson(this)
                && minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (controlsLocked) {
            clearInput(LOCKED_INPUT);
            player.input = LOCKED_INPUT;
            player.setSprinting(false);
        } else if (player.input == LOCKED_INPUT && previousInput != null) {
            player.input = previousInput;
        }
    }

    /** Single restoration path used by normal completion and every abnormal client lifecycle exit. */
    private void reset(boolean clearPostMessage) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientCinematicPresentation oldPresentation = presentation;
        if (oldPresentation != null) {
            invokePresentation(() -> oldPresentation.onStopped(this), "cleanup");
        }
        if (clientSettingsCaptured) {
            LocalPlayer player = minecraft.player;
            if (player != null && player.getUUID().equals(playerId)
                    && player.input == LOCKED_INPUT && previousInput != null) {
                player.input = previousInput;
            }
            minecraft.options.hideGui = previousHideGui;
            if (previousCameraType != null) {
                minecraft.options.setCameraType(previousCameraType);
            }
        }

        sequenceId = null;
        stageId = null;
        presentation = null;
        stageStartGameTime = 0L;
        stageDurationTicks = 0;
        controlsLocked = false;
        hideHud = false;
        anchor = null;
        baseYaw = 0.0F;
        basePitch = 0.0F;
        playerId = null;
        previousInput = null;
        previousCameraType = null;
        previousHideGui = false;
        clientSettingsCaptured = false;
        subtitle = null;
        subtitleTicks = 0;
        subtitleTotalTicks = 0;
        clearInput(LOCKED_INPUT);
        if (clearPostMessage) {
            postMessage = null;
            postMessageTicks = 0;
            postMessageTotalTicks = 0;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (INSTANCE.isActive()) {
            if (minecraft.player == null || minecraft.level == null) {
                INSTANCE.reset(false);
            } else {
                INSTANCE.enforceClientState(minecraft, minecraft.player);
            }
        }
        if (INSTANCE.postMessageTicks > 0) {
            INSTANCE.postMessageTicks--;
            if (INSTANCE.postMessageTicks == 0) {
                INSTANCE.postMessage = null;
                INSTANCE.postMessageTotalTicks = 0;
            }
        }
        if (INSTANCE.subtitleTicks > 0) {
            INSTANCE.subtitleTicks--;
            if (INSTANCE.subtitleTicks == 0) {
                INSTANCE.subtitle = null;
                INSTANCE.subtitleTotalTicks = 0;
            }
        }
    }

    /** Clears any movement state another client input listener attempted to add after the inert input tick. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (INSTANCE.controlsLocked()) {
            clearInput(event.getInput());
        }
    }

    /** Prevents attack, use, and pick-block key mappings before vanilla processes them. */
    @SubscribeEvent
    public static void onInteractionInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (INSTANCE.controlsLocked()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Blocks inventory/container screens while retaining chat and the pause menu for safe operator control. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (INSTANCE.controlsLocked() && event.getNewScreen() instanceof AbstractContainerScreen<?>) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.reset(true);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            INSTANCE.reset(true);
        }
    }

    private static void clearInput(Input input) {
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    private void invokePresentation(Runnable callback, String action) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.error("Client cinematic {} failed during {}", sequenceId, action, exception);
        }
    }
}
