package com.thunder.wildernessodysseyapi.temporalrift.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.temporalrift.echo.EchoStabilityPayload;
import com.thunder.wildernessodysseyapi.temporalrift.echo.EchoTimeModel;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only, dimension-bound presentation of the server's stability conclusions. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientEchoState {
    private static EchoStabilityPayload current;
    private static Level owner;
    private static float atmosphere;

    private ClientEchoState() { }

    /** Called on the registrar's game-thread handoff; old-dimension summaries are discarded. */
    public static void accept(Level level, EchoStabilityPayload payload) {
        if (level == null || !level.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)
                || !level.dimension().location().equals(payload.dimension())) return;
        if (owner != level) clear();
        if (current != null && payload.gameTime() < current.gameTime()) return;
        owner = level;
        current = payload;
    }

    /** Smoothed visual amount, neutral until the server supplies a valid summary. */
    public static float atmosphere() {
        return valid() ? atmosphere : 0;
    }

    /** Local Riftfall presentation multiplier follows server gameplay, independent of ambience toggle. */
    public static float riftfallIntensity() {
        return valid() ? current.riftfallIntensity() / 100F : 0;
    }

    /** Changes only the celestial input, leaving client/server game clocks intact. */
    public static long visualDayTime(long dayTime) {
        return valid() ? EchoTimeModel.visualDayTime(dayTime, current.regionId(), current.timeAnomalies(),
                current.intensity() / 100.0) : dayTime;
    }

    /** Smooth transitions over roughly two seconds; the client never derives regional gameplay state. */
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!valid()) { clear(); return; }
        if (Minecraft.getInstance().isPaused()) return;
        float target = current.atmosphere() ? current.intensity() / 100F : 0;
        atmosphere += (target - atmosphere) * 0.08F;
    }

    private static boolean valid() {
        return current != null && owner != null && owner == Minecraft.getInstance().level
                && owner.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY);
    }

    private static void clear() {
        current = null;
        owner = null;
        atmosphere = 0;
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() == owner) clear();
    }
}
