package com.thunder.wildernessodysseyapi.weather.integration.survival;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Guarded server adapter for Thirst Was Taken's player thirst attachment.
 *
 * <p>Only additional outdoor weather exhaustion is written. Normal thirst,
 * drinks, rain collection, synchronization, difficulty rules, and damage remain
 * owned by Thirst Was Taken. Reflection keeps the mod optional and converts an
 * incompatible attachment API into a logged no-op.</p>
 */
public final class ThirstWasTakenWeatherBridge {

    public static final String MOD_ID = "thirst";

    private static final String ATTACHMENT_OWNER =
            "dev.ghen.thirst.foundation.common.capability.ModAttachment";

    private final Supplier<?> thirstAttachmentSupplier;
    private volatile Object thirstAttachmentType;
    private volatile AccessMethods accessMethods;
    private volatile boolean active = true;
    private volatile boolean failureLogged;

    private ThirstWasTakenWeatherBridge(Supplier<?> thirstAttachmentSupplier) {
        this.thirstAttachmentSupplier = thirstAttachmentSupplier;
    }

    /** Resolves Thirst Was Taken's registered attachment without hard-linking it. */
    public static ThirstWasTakenWeatherBridge discover() {
        try {
            Class<?> owner = Class.forName(
                    ATTACHMENT_OWNER,
                    false,
                    ThirstWasTakenWeatherBridge.class.getClassLoader()
            );
            Field field = owner.getField("PLAYER_THIRST");
            Object supplier = field.get(null);
            if (!(supplier instanceof Supplier<?> typedSupplier)) {
                throw new IllegalStateException("PLAYER_THIRST is not a Supplier");
            }
            // Deferred attachment values are not bound during mod construction.
            // Keep the supplier and resolve it on the first server tick, after
            // NeoForge has completed registry events.
            ModConstants.LOGGER.info("Discovered Thirst Was Taken localized-weather exposure integration");
            return new ThirstWasTakenWeatherBridge(typedSupplier);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ModConstants.LOGGER.warn(
                    "Thirst Was Taken is installed but its player thirst attachment could not be resolved; the guarded adapter is disabled",
                    exception
            );
            return new ThirstWasTakenWeatherBridge(null).disabled();
        }
    }

    /** Applies one bounded exposure update to players when its configured cadence is due. */
    public void tick(
            ServerLevel level,
            WeatherQuery weather,
            WeatherConfig.SurvivalIntegrationSettings settings,
            boolean coldSweatIntegrated
    ) {
        if (!active || thirstAttachmentSupplier == null || !settings.thirstWasTakenEnabled()) {
            return;
        }
        if (Math.floorMod(level.getGameTime(), settings.thirstIntervalTicks()) != 0L) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.getAbilities().invulnerable) {
                continue;
            }
            boolean exposed = level.canSeeSky(player.blockPosition().above()) && !player.isInWater();
            WeatherSample sample = weather.sample(level, player.blockPosition());
            double extraExhaustion = WeatherSurvivalExposureModel.thirstExhaustionPerInterval(
                    sample,
                    exposed,
                    coldSweatIntegrated,
                    settings.thirstMaximumExhaustionPerInterval()
            );
            if (extraExhaustion <= 1.0e-6) {
                continue;
            }
            apply(player, (float) extraExhaustion);
        }
    }

    /** Returns whether the optional attachment bridge is still usable. */
    public boolean active() {
        return active;
    }

    private void apply(ServerPlayer player, float extraExhaustion) {
        try {
            Object attachmentType = attachmentType();
            AccessMethods access = accessMethods;
            if (access == null) {
                access = resolveAccess(player, attachmentType);
                accessMethods = access;
            }
            Object thirst = access.getData.invoke(player, attachmentType);
            if (thirst == null || !(boolean) access.getShouldTick.invoke(thirst)) {
                return;
            }
            float current = ((Number) access.getExhaustion.invoke(thirst)).floatValue();
            access.setExhaustion.invoke(thirst, Math.min(40.0F, Math.max(0.0F, current + extraExhaustion)));
            access.updateThirstData.invoke(thirst, player);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            disable(exception);
        }
    }

    private synchronized Object attachmentType() {
        Object resolved = thirstAttachmentType;
        if (resolved == null) {
            resolved = thirstAttachmentSupplier.get();
            if (resolved == null) {
                throw new IllegalStateException("PLAYER_THIRST was not registered");
            }
            thirstAttachmentType = resolved;
            ModConstants.LOGGER.info("Enabled Thirst Was Taken localized-weather exposure integration");
        }
        return resolved;
    }

    private AccessMethods resolveAccess(ServerPlayer player, Object attachmentType)
            throws ReflectiveOperationException {
        Method getData = Arrays.stream(player.getClass().getMethods())
                .filter(method -> method.getName().equals("getData"))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0].isAssignableFrom(attachmentType.getClass()))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("ServerPlayer.getData(AttachmentType)"));
        Object thirst = getData.invoke(player, attachmentType);
        if (thirst == null) {
            throw new IllegalStateException("Thirst attachment returned null");
        }
        Class<?> thirstClass = thirst.getClass();
        return new AccessMethods(
                getData,
                thirstClass.getMethod("getExhaustion"),
                thirstClass.getMethod("setExhaustion", float.class),
                thirstClass.getMethod("getShouldTickThirst"),
                thirstClass.getMethod("updateThirstData", net.minecraft.world.entity.player.Player.class)
        );
    }

    private ThirstWasTakenWeatherBridge disabled() {
        active = false;
        return this;
    }

    private void disable(Throwable throwable) {
        active = false;
        accessMethods = null;
        if (!failureLogged) {
            failureLogged = true;
            ModConstants.LOGGER.warn(
                    "Thirst Was Taken weather integration stopped after an incompatible runtime API response; the guarded adapter is disabled",
                    throwable
            );
        }
    }

    private record AccessMethods(
            Method getData,
            Method getExhaustion,
            Method setExhaustion,
            Method getShouldTick,
            Method updateThirstData
    ) {
    }
}
