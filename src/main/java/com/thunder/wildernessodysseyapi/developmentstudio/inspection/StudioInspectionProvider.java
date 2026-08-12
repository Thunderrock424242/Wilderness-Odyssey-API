package com.thunder.wildernessodysseyapi.developmentstudio.inspection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Extensible server-side provider for one kind of real gameplay target.
 *
 * @param <T> target type understood by this provider
 */
public interface StudioInspectionProvider<T> {
    ResourceLocation id();

    Class<T> targetType();

    default boolean supports(T target) {
        return true;
    }

    StudioInspection inspect(ServerPlayer player, T target);

    /** Safely narrows an untyped registry target before invoking the provider. */
    default Optional<StudioInspection> tryInspect(ServerPlayer player, Object target) {
        if (!targetType().isInstance(target)) {
            return Optional.empty();
        }
        T typedTarget = targetType().cast(target);
        return supports(typedTarget) ? Optional.of(inspect(player, typedTarget)) : Optional.empty();
    }
}
